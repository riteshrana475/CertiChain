package com.certichain.issuer;

import com.certichain.blockchain.ChainService;
import com.certichain.certificate.CertificateEntity;
import com.certichain.certificate.CertificateRepository;
import com.certichain.crypto.CanonicalHasher;
import com.certichain.crypto.CredentialSigner;
import com.certichain.document.CertificatePdf;
import com.certichain.document.CertificateStorage;
import com.certichain.institution.InstitutionEntity;
import com.certichain.security.CurrentUser;
import com.certichain.student.StudentEntity;
import com.certichain.student.StudentRepository;
import com.certichain.user.UserAccount;
import com.certichain.wallet.ActivityRecorder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BulkIssuanceService {

    private final CurrentUser current;
    private final BulkJobRepository jobs;
    private final BulkRowRepository rows;
    private final StudentRepository students;
    private final CertificateRepository certificates;
    private final CertificateStorage storage;
    private final ChainService chain;
    private final CredentialSigner signer;
    private final ActivityRecorder activity;
    private final AnomalyService anomalies;

    public BulkIssuanceService(
            CurrentUser current,
            BulkJobRepository jobs,
            BulkRowRepository rows,
            StudentRepository students,
            CertificateRepository certificates,
            CertificateStorage storage,
            ChainService chain,
            CredentialSigner signer,
            ActivityRecorder activity,
            AnomalyService anomalies) {
        this.current = current;
        this.jobs = jobs;
        this.rows = rows;
        this.students = students;
        this.certificates = certificates;
        this.storage = storage;
        this.chain = chain;
        this.signer = signer;
        this.activity = activity;
        this.anomalies = anomalies;
    }

    public String template() {
        return "studentNumber,email,name,degree,certificateType,issueDate,grade,certificateId\n";
    }

    public JobView preview(String csv) {
        InstitutionEntity institution = current.institution();
        UserAccount actor = current.require();
        BulkJob job = new BulkJob();
        job.setId(UUID.randomUUID());
        job.setInstitutionId(institution.getId());
        job.setActorEmail(actor.getEmail());
        job.setStatus("PREVIEW");
        job.setCreatedAt(Instant.now());
        jobs.save(job);
        Set<String> seen = new HashSet<>();
        for (BulkCsv.ParsedRow parsed : BulkCsv.parse(csv)) {
            rows.save(validate(job, institution, parsed, seen));
        }
        return view(job);
    }

    public JobView confirm(UUID id) {
        InstitutionEntity institution = current.institution();
        BulkJob job = own(id, institution);
        if ("COMPLETED".equals(job.getStatus())) {
            return view(job);
        }
        if (!"PREVIEW".equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This import is already being processed.");
        }
        job.setStatus("RUNNING");
        jobs.save(job);
        for (BulkRow row : rows.findByJobIdOrderByRowNumberAsc(job.getId())) {
            if (!"VALID".equals(row.getStatus())) {
                continue;
            }
            try {
                issue(institution, row);
                row.setStatus("ISSUED");
                row.setDetail("Issued");
            } catch (RuntimeException exception) {
                row.setStatus("FAILED");
                row.setDetail(exception.getMessage() == null ? "This row could not be issued." : exception.getMessage());
            }
            rows.save(row);
        }
        job.setStatus("COMPLETED");
        jobs.save(job);
        activity.notify(current.require().getId(), "BULK_ISSUANCE", "Bulk issuance finished", "Open the import to see which rows were issued.", "/issuer/bulk");
        return view(job);
    }

    public JobView get(UUID id) {
        return view(own(id, current.institution()));
    }

    public String report(UUID id) {
        JobView job = get(id);
        StringBuilder csv = new StringBuilder("row,studentNumber,certificateId,status,detail\n");
        for (RowView row : job.rows()) {
            csv.append(row.rowNumber()).append(',')
                    .append(BulkCsv.excelSafe(row.studentNumber())).append(',')
                    .append(BulkCsv.excelSafe(row.certificateId())).append(',')
                    .append(row.status()).append(',')
                    .append(BulkCsv.excelSafe(row.detail())).append('\n');
        }
        return csv.toString();
    }

    private BulkRow validate(BulkJob job, InstitutionEntity institution, BulkCsv.ParsedRow parsed, Set<String> seen) {
        BulkRow row = new BulkRow();
        row.setId(UUID.randomUUID());
        row.setJobId(job.getId());
        row.setRowNumber(parsed.rowNumber());
        row.setStudentNumber(parsed.studentNumber());
        row.setCertificateId(parsed.certificateId());
        String problem = problem(institution, parsed, seen);
        if (problem == null) {
            row.setStatus("VALID");
            row.setDetail("Ready to issue");
        } else if (problem.startsWith("Duplicate")) {
            row.setStatus("DUPLICATE");
            row.setDetail(problem);
            anomalies.record(institution.getId(), "DUPLICATE_CREDENTIAL", "INFORMATIONAL", parsed.certificateId(), problem);
        } else {
            row.setStatus("ERROR");
            row.setDetail(problem);
        }
        row.setPayload(String.join("\u001f", parsed.degree(), parsed.certificateType(), parsed.issueDate(), parsed.grade(), parsed.certificateId()));
        return row;
    }

    private String problem(InstitutionEntity institution, BulkCsv.ParsedRow parsed, Set<String> seen) {
        if (parsed.studentNumber().isBlank() || parsed.degree().isBlank() || parsed.certificateType().isBlank() || parsed.issueDate().isBlank() || parsed.grade().isBlank()) {
            return "Student number, degree, credential type, issue date, and grade are required.";
        }
        StudentEntity student = students.findByInstitutionIdAndStudentNumber(institution.getId(), parsed.studentNumber()).orElse(null);
        if (student == null) {
            return "That student is not in this institution.";
        }
        if (!parsed.email().isBlank() && student.getEmail() != null && !student.getEmail().isBlank() && !student.getEmail().equalsIgnoreCase(parsed.email())) {
            return "The email does not match the student record.";
        }
        try {
            LocalDate.parse(parsed.issueDate());
        } catch (DateTimeParseException exception) {
            return "Use an issue date as YYYY-MM-DD.";
        }
        if (!parsed.certificateId().isBlank()) {
            if (!seen.add(parsed.certificateId()) || certificates.existsById(parsed.certificateId())) {
                return "Duplicate credential ID " + parsed.certificateId() + ".";
            }
        }
        return null;
    }

    private void issue(InstitutionEntity institution, BulkRow row) {
        String[] payload = row.getPayload().split("\u001f", -1);
        StudentEntity student = students.findByInstitutionIdAndStudentNumber(institution.getId(), row.getStudentNumber())
                .orElseThrow(() -> new IllegalStateException("That student is not in this institution."));
        String id = payload.length > 4 && !payload[4].isBlank() ? payload[4] : nextId();
        if (certificates.existsById(id)) {
            throw new IllegalStateException("Duplicate credential ID " + id + ".");
        }
        CertificateEntity certificate = new CertificateEntity();
        certificate.setId(id);
        certificate.setStudentId(student.getId());
        certificate.setCertificateType(payload[1]);
        certificate.setDegree(payload[0]);
        certificate.setDepartment(student.getDepartment());
        certificate.setIssueDate(LocalDate.parse(payload[2]));
        certificate.setGrade(payload[3]);
        certificate.setStatus("ISSUED");
        certificate.setRevokedReason("");
        certificate.setChainStatus(ChainService.NOT_ANCHORED);
        certificate.setChainTxHash("");
        certificate.setChainNetwork("");
        certificate.setFileHash("");
        certificate.setFileKey("");
        String canonical = CanonicalHasher.canonical(id, student.getName(), certificate.getDegree(), certificate.getIssueDate().toString(), certificate.getGrade(), institution.getName());
        certificate.setDocumentHash(CanonicalHasher.sha256(canonical));
        signer.apply(certificate, CredentialSigner.payload(canonical, certificate.getDocumentHash()));
        storage.store(certificate, CertificatePdf.render(certificate, student, institution.getName()));
        chain.sync(certificate);
        certificates.save(certificate);
        row.setCertificateId(id);
        if (student.getEmail() != null && !student.getEmail().isBlank()) {
            activity.audit(current.require().getEmail(), "CREDENTIAL_ISSUED", "CREDENTIAL", id, "Bulk issuance");
        }
    }

    private String nextId() {
        String prefix = "CERT-" + LocalDate.now().getYear() + "-";
        long sequence = certificates.countByIdStartingWith(prefix) + 1;
        String id;
        do {
            id = prefix + String.format("%06d", sequence);
            sequence++;
        } while (certificates.existsById(id));
        return id;
    }

    private BulkJob own(UUID id, InstitutionEntity institution) {
        BulkJob job = jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import was not found."));
        if (!job.getInstitutionId().equals(institution.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import was not found.");
        }
        return job;
    }

    private JobView view(BulkJob job) {
        List<RowView> items = new ArrayList<>();
        int valid = 0;
        int issued = 0;
        int failed = 0;
        int skipped = 0;
        for (BulkRow row : rows.findByJobIdOrderByRowNumberAsc(job.getId())) {
            items.add(new RowView(row.getRowNumber(), row.getStudentNumber(), row.getCertificateId(), row.getStatus(), row.getDetail()));
            switch (row.getStatus()) {
                case "VALID" -> valid++;
                case "ISSUED" -> issued++;
                case "FAILED", "ERROR" -> failed++;
                case "DUPLICATE" -> skipped++;
                default -> {
                }
            }
        }
        return new JobView(job.getId().toString(), job.getStatus(), items.size(), valid, issued, failed, skipped, items);
    }

    public record JobView(String id, String status, int total, int valid, int issued, int failed, int skipped, List<RowView> rows) {
    }

    public record RowView(int rowNumber, String studentNumber, String certificateId, String status, String detail) {
    }
}
