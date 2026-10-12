package com.certichain.api;

import com.certichain.blockchain.ChainService;
import com.certichain.certificate.CertificateEntity;
import com.certichain.certificate.CertificateRepository;
import com.certichain.crypto.CanonicalHasher;
import com.certichain.crypto.CredentialSigner;
import com.certichain.issuer.AnomalyService;
import com.certichain.issuer.CredentialAmendment;
import com.certichain.issuer.CredentialAmendmentRepository;
import com.certichain.document.CertificatePdf;
import com.certichain.document.CertificateStorage;
import com.certichain.document.DocumentScanService;
import com.certichain.institution.InstitutionEntity;
import com.certichain.institution.InstitutionRepository;
import com.certichain.security.CurrentUser;
import com.certichain.student.StudentEntity;
import com.certichain.student.StudentRepository;
import com.certichain.user.UserAccount;
import com.certichain.user.UserAccountRepository;
import com.certichain.wallet.ActivityRecorder;
import com.certichain.wallet.VerifierCheck;
import com.certichain.wallet.VerifierCheckRepository;
import com.certichain.verification.VerificationLogEntity;
import com.certichain.verification.VerificationLogRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RecordsController {

    private final StudentRepository students;
    private final CertificateRepository certificates;
    private final VerificationLogRepository logs;
    private final ChainService chain;
    private final CertificateStorage storage;
    private final DocumentScanService scanner;
    private final CurrentUser current;
    private final InstitutionRepository institutions;
    private final ActivityRecorder activity;
    private final UserAccountRepository users;
    private final VerifierCheckRepository checks;
    private final CredentialSigner signer;
    private final CredentialAmendmentRepository amendments;
    private final AnomalyService anomalies;

    public RecordsController(
            StudentRepository students,
            CertificateRepository certificates,
            VerificationLogRepository logs,
            ChainService chain,
            CertificateStorage storage,
            DocumentScanService scanner,
            CurrentUser current,
            InstitutionRepository institutions,
            ActivityRecorder activity,
            UserAccountRepository users,
            VerifierCheckRepository checks,
            CredentialSigner signer,
            CredentialAmendmentRepository amendments,
            AnomalyService anomalies) {
        this.students = students;
        this.certificates = certificates;
        this.logs = logs;
        this.chain = chain;
        this.storage = storage;
        this.scanner = scanner;
        this.current = current;
        this.institutions = institutions;
        this.activity = activity;
        this.users = users;
        this.checks = checks;
        this.signer = signer;
        this.amendments = amendments;
        this.anomalies = anomalies;
    }

    @GetMapping("/api/students")
    public List<StudentResponse> listStudents() {
        return students.findByInstitutionIdOrderByNameAsc(current.institution().getId()).stream()
                .map(StudentResponse::from)
                .toList();
    }

    @PostMapping("/api/students")
    public StudentResponse createStudent(@Valid @RequestBody StudentRequest request) {
        InstitutionEntity institution = current.institution();
        StudentEntity student = new StudentEntity();
        student.setId("stu-" + UUID.randomUUID().toString().substring(0, 8));
        student.setInstitutionId(institution.getId());
        applyStudent(student, request);
        return StudentResponse.from(students.save(student));
    }

    @PutMapping("/api/students/{id}")
    public StudentResponse updateStudent(@PathVariable String id, @Valid @RequestBody StudentRequest request) {
        StudentEntity student = ownStudent(id);
        String previousName = student.getName();
        applyStudent(student, request);
        students.save(student);
        if (!previousName.equals(student.getName())) {
            for (CertificateEntity certificate : certificates.findByStudentId(student.getId())) {
                republish(certificate, student);
            }
        }
        return StudentResponse.from(student);
    }

    @DeleteMapping("/api/students/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStudent(@PathVariable String id) {
        StudentEntity student = ownStudent(id);
        if (certificates.existsByStudentId(student.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Delete is blocked while this student still has certificates.");
        }
        students.delete(student);
    }

    @GetMapping("/api/certificates")
    public List<CertificateResponse> listCertificates() {
        return ownCertificates().stream().map(CertificateResponse::from).toList();
    }

    @PostMapping("/api/certificates")
    public CertificateResponse saveCertificate(@Valid @RequestBody CertificateRequest request) {
        StudentEntity student = ownStudent(request.studentId());
        String id = request.id() == null || request.id().isBlank()
                ? nextCertificateId()
                : request.id();
        CertificateEntity certificate = certificates.findById(id).orElseGet(CertificateEntity::new);
        if (certificate.getId() != null) {
            ownStudent(certificate.getStudentId());
            if ("REVOKED".equals(certificate.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A revoked certificate cannot be changed.");
            }
            if ("ISSUED".equals(certificate.getStatus()) && !"ISSUED".equals(request.status())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "An issued certificate stays issued. Revoke it instead of turning it back into a draft.");
            }
        }
        certificate.setId(id);
        certificate.setStudentId(student.getId());
        certificate.setCertificateType(request.certificateType().trim());
        certificate.setDegree(request.degree().trim());
        certificate.setDepartment(request.department().trim());
        certificate.setIssueDate(LocalDate.parse(request.issueDate()));
        certificate.setGrade(request.grade().trim());
        certificate.setStatus(request.status());
        certificate.setRevokedReason(certificate.getRevokedReason() == null ? "" : certificate.getRevokedReason());
        if ("ISSUED".equals(request.status())) {
            String previousHash = certificate.getDocumentHash() == null ? "" : certificate.getDocumentHash();
            String nextHash = fingerprint(certificate, student);
            if (!nextHash.equals(certificate.getDocumentHash())) {
                certificate.setDocumentHash(nextHash);
                certificate.setFileHash("");
                certificate.setFileKey("");
                certificate.setChainStatus(ChainService.NOT_ANCHORED);
                certificate.setChainTxHash("");
                certificate.setChainNetwork("");
                if (!previousHash.isBlank()) {
                    rememberAmendment(certificate, previousHash, "Credential fields were corrected.");
                }
            }
            sign(certificate, student);
            storage.store(certificate, CertificatePdf.render(certificate, student, institutionName(student)));
            chain.sync(certificate);
        } else {
            certificate.setDocumentHash("");
            certificate.setFileHash("");
            certificate.setFileKey("");
            certificate.setChainStatus(ChainService.NOT_ANCHORED);
            certificate.setChainTxHash("");
            certificate.setChainNetwork("");
        }
        certificates.save(certificate);
        if ("ISSUED".equals(certificate.getStatus())) {
            tellStudent(student, "CREDENTIAL_ISSUED", "Credential issued", certificate.getDegree() + " is in your wallet.", "/wallet");
        }
        return CertificateResponse.from(certificate);
    }

    @PostMapping("/api/certificates/{id}/revoke")
    public CertificateResponse revoke(@PathVariable String id, @Valid @RequestBody RevokeRequest request) {
        CertificateEntity certificate = ownCertificate(id);
        if (!"ISSUED".equals(certificate.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only an issued certificate can be revoked.");
        }
        certificate.setStatus("REVOKED");
        certificate.setRevokedReason(request.reason().trim());
        chain.sync(certificate);
        certificates.save(certificate);
        StudentEntity student = students.findById(certificate.getStudentId()).orElse(null);
        if (student != null) {
            tellStudent(student, "CREDENTIAL_REVOKED", "Credential revoked", certificate.getId() + " was revoked.", "/wallet");
        }
        return CertificateResponse.from(certificate);
    }

    @GetMapping("/api/verification-logs")
    public List<LogResponse> listLogs() {
        List<String> ids = ownCertificates().stream().map(CertificateEntity::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return logs.findByCertificateIdInOrderByVerifiedAtDesc(ids).stream().map(LogResponse::from).toList();
    }

    @GetMapping("/api/public/verify/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable String id) {
        CertificateEntity certificate = certificates.findById(id)
                .filter(item -> !"DRAFT".equals(item.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No issued certificate uses this ID."));
        StudentEntity student = students.findById(certificate.getStudentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record is missing."));
        byte[] pdf = storage.loadOrCreate(certificate, student, institutionName(student));
        certificates.save(certificate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + certificate.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/api/public/verify/{id}")
    public PublicVerification publicVerify(@PathVariable String id) {
        PublicVerification view = readPublic(id);
        writeLog(id, view.status(), "QR_LOOKUP");
        return view;
    }

    @PostMapping("/api/public/verify/{id}/check-file")
    public FileCheckResponse checkFile(@PathVariable String id, @RequestParam("file") MultipartFile file) throws Exception {
        PublicVerification view = readPublic(id);
        if ("NOT_FOUND".equals(view.status())) {
            writeLog(id, "NOT_FOUND", "DOCUMENT_CHECK");
            return new FileCheckResponse("NOT_FOUND", "", view.documentHash());
        }
        CertificateEntity certificate = certificates.findById(id).orElseThrow();
        String fileHash = CanonicalHasher.sha256(file.getBytes());
        boolean matches = fileHash.equals(certificate.getDocumentHash())
                || (!certificate.getFileHash().isBlank() && fileHash.equals(certificate.getFileHash()));
        String result = !matches ? "HASH_MISMATCH" : "REVOKED".equals(certificate.getStatus()) ? "REVOKED" : "VERIFIED";
        if ("HASH_MISMATCH".equals(result)) {
            StudentEntity student = students.findById(certificate.getStudentId()).orElse(null);
            if (student != null) {
                anomalies.record(student.getInstitutionId(), "HASH_MISMATCH", "WARNING", certificate.getId(), "A compared file did not match the registered fingerprint.");
            }
        }
        writeLog(id, result, "DOCUMENT_CHECK");
        return new FileCheckResponse(result, fileHash, view.documentHash());
    }

    @PostMapping("/api/public/verify/{id}/scan")
    public ScanResponse scan(@PathVariable String id, @RequestParam("file") MultipartFile file) throws Exception {
        CertificateEntity certificate = certificates.findById(id).orElse(null);
        if (certificate == null || "DRAFT".equals(certificate.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No issued certificate uses this ID.");
        }
        StudentEntity student = students.findById(certificate.getStudentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record is missing."));
        DocumentScanService.ScanOutcome outcome = scanner.scan(certificate, student, file.getBytes(), institutionName(student));
        boolean allMatch = outcome.fields().stream().allMatch(DocumentScanService.FieldComparison::match);
        String result = allMatch && "REVOKED".equals(certificate.getStatus()) ? "REVOKED" : allMatch ? "VERIFIED" : "HASH_MISMATCH";
        writeLog(id, result, "DOCUMENT_CHECK");
        return new ScanResponse(result, outcome.note(), outcome.fields());
    }

    @PostMapping("/api/verifier/checks/{id}")
    public PublicVerification recruiterCheck(@PathVariable String id) {
        UserAccount user = current.requireRole("RECRUITER");
        PublicVerification view = readPublic(id);
        writeLog(id, view.status(), "RECRUITER_CHECK");
        VerifierCheck check = new VerifierCheck();
        check.setId(UUID.randomUUID());
        check.setUserId(user.getId());
        check.setCertificateId(id);
        check.setResult(view.status());
        check.setCheckedAt(Instant.now());
        checks.save(check);
        return view;
    }

    @GetMapping("/api/verifier/checks")
    public List<VerifierCheck> recentChecks() {
        return checks.findTop20ByUserIdOrderByCheckedAtDesc(current.requireRole("RECRUITER").getId());
    }

    private void tellStudent(StudentEntity student, String type, String title, String message, String href) {
        if (student.getEmail() != null && !student.getEmail().isBlank()) {
            users.findByEmailIgnoreCase(student.getEmail())
                    .ifPresent(user -> activity.notify(user.getId(), type, title, message, href));
        }
        activity.audit(current.require().getEmail(), type, "CREDENTIAL", student.getId(), title);
    }

    private static String maskName(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].charAt(0) + ".";
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private PublicVerification readPublic(String id) {
        CertificateEntity certificate = certificates.findById(id).orElse(null);
        if (certificate == null || "DRAFT".equals(certificate.getStatus())) {
            return PublicVerification.missing(id);
        }
        StudentEntity student = students.findById(certificate.getStudentId()).orElse(null);
        if (student == null) {
            return PublicVerification.missing(id);
        }
        String status = "REVOKED".equals(certificate.getStatus()) ? "REVOKED" : "VERIFIED";
        return new PublicVerification(
                certificate.getId(),
                status,
                maskName(student.getName()),
                student.getStudentNumber(),
                certificate.getDegree(),
                certificate.getDepartment(),
                certificate.getIssueDate().toString(),
                certificate.getGrade(),
                institutionName(student),
                certificate.getDocumentHash(),
                certificate.getRevokedReason(),
                certificate.getChainStatus(),
                certificate.getChainTxHash(),
                certificate.getChainNetwork());
    }

    private void writeLog(String certificateId, String result, String type) {
        VerificationLogEntity log = new VerificationLogEntity();
        log.setId(UUID.randomUUID());
        log.setCertificateId(certificateId);
        log.setResult(result);
        log.setVerificationType(type);
        log.setVerifiedAt(Instant.now());
        logs.save(log);
    }

    private void applyStudent(StudentEntity student, StudentRequest request) {
        if (request.graduationYear() < 2000 || request.graduationYear() > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a graduation year between 2000 and 2100.");
        }
        String number = request.studentNumber().trim();
        students.findByInstitutionIdAndStudentNumber(student.getInstitutionId(), number)
                .filter(existing -> !existing.getId().equals(student.getId()))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "That student number is already used.");
                });
        student.setName(request.name().trim());
        student.setStudentNumber(number);
        student.setDepartment(request.department().trim());
        student.setCourse(request.course().trim());
        student.setGraduationYear(request.graduationYear());
    }

    private StudentEntity ownStudent(String id) {
        StudentEntity student = students.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student was not found."));
        if (!current.institution().getId().equals(student.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student was not found.");
        }
        return student;
    }

    private CertificateEntity ownCertificate(String id) {
        CertificateEntity certificate = certificates.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificate was not found."));
        ownStudent(certificate.getStudentId());
        return certificate;
    }

    private List<CertificateEntity> ownCertificates() {
        List<String> studentIds = students.findByInstitutionIdOrderByNameAsc(current.institution().getId()).stream()
                .map(StudentEntity::getId)
                .toList();
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return certificates.findByStudentIdInOrderByIssueDateDesc(studentIds);
    }

    private void republish(CertificateEntity certificate, StudentEntity student) {
        if ("DRAFT".equals(certificate.getStatus())) {
            return;
        }
        String previousHash = certificate.getDocumentHash() == null ? "" : certificate.getDocumentHash();
        String nextHash = fingerprint(certificate, student);
        if (!nextHash.equals(certificate.getDocumentHash())) {
            certificate.setDocumentHash(nextHash);
            certificate.setFileHash("");
            certificate.setFileKey("");
            certificate.setChainStatus(ChainService.NOT_ANCHORED);
            certificate.setChainTxHash("");
            certificate.setChainNetwork("");
            if (!previousHash.isBlank()) {
                rememberAmendment(certificate, previousHash, "The student name on the credential was corrected.");
            }
        }
        sign(certificate, student);
        storage.store(certificate, CertificatePdf.render(certificate, student, institutionName(student)));
        chain.sync(certificate);
        certificates.save(certificate);
    }

    private void sign(CertificateEntity certificate, StudentEntity student) {
        String canonical = CanonicalHasher.canonical(
                certificate.getId(),
                student.getName(),
                certificate.getDegree(),
                certificate.getIssueDate().toString(),
                certificate.getGrade(),
                institutionName(student));
        signer.apply(certificate, CredentialSigner.payload(canonical, certificate.getDocumentHash()));
    }

    private void rememberAmendment(CertificateEntity certificate, String previousHash, String reason) {
        CredentialAmendment amendment = new CredentialAmendment();
        amendment.setId(UUID.randomUUID());
        amendment.setCertificateId(certificate.getId());
        amendment.setActorEmail(current.require().getEmail());
        amendment.setReason(reason);
        amendment.setPreviousHash(previousHash);
        amendment.setCreatedAt(Instant.now());
        amendments.save(amendment);
    }

    private String fingerprint(CertificateEntity certificate, StudentEntity student) {
        return CanonicalHasher.sha256(CanonicalHasher.canonical(
                certificate.getId(),
                student.getName(),
                certificate.getDegree(),
                certificate.getIssueDate().toString(),
                certificate.getGrade(),
                institutionName(student)));
    }

    private String institutionName(StudentEntity student) {
        if (student.getInstitutionId() == null) {
            return CanonicalHasher.INSTITUTION;
        }
        return institutions.findById(student.getInstitutionId())
                .map(InstitutionEntity::getName)
                .orElse(CanonicalHasher.INSTITUTION);
    }

    private String nextCertificateId() {
        String prefix = "CERT-" + LocalDate.now().getYear() + "-";
        long sequence = certificates.countByIdStartingWith(prefix) + 1;
        String id;
        do {
            id = prefix + String.format("%06d", sequence);
            sequence++;
        } while (certificates.existsById(id));
        return id;
    }

    public record StudentRequest(
            @NotBlank String name,
            @NotBlank String studentNumber,
            @NotBlank String department,
            @NotBlank String course,
            int graduationYear) {
    }

    public record StudentResponse(String id, String name, String studentNumber, String department, String course, int graduationYear) {
        static StudentResponse from(StudentEntity student) {
            return new StudentResponse(
                    student.getId(),
                    student.getName(),
                    student.getStudentNumber(),
                    student.getDepartment(),
                    student.getCourse(),
                    student.getGraduationYear());
        }
    }

    public record CertificateRequest(
            String id,
            @NotBlank String studentId,
            @NotBlank String certificateType,
            @NotBlank String degree,
            @NotBlank String department,
            @NotBlank String issueDate,
            @NotBlank String grade,
            @NotBlank String status) {
    }

    public record CertificateResponse(
            String id,
            String studentId,
            String certificateType,
            String degree,
            String department,
            String issueDate,
            String grade,
            String status,
            String documentHash,
            String revokedReason,
            String chainStatus,
            String chainTxHash,
            String chainNetwork) {
        static CertificateResponse from(CertificateEntity certificate) {
            return new CertificateResponse(
                    certificate.getId(),
                    certificate.getStudentId(),
                    certificate.getCertificateType(),
                    certificate.getDegree(),
                    certificate.getDepartment(),
                    certificate.getIssueDate().toString(),
                    certificate.getGrade(),
                    certificate.getStatus(),
                    certificate.getDocumentHash(),
                    certificate.getRevokedReason(),
                    certificate.getChainStatus(),
                    certificate.getChainTxHash(),
                    certificate.getChainNetwork());
        }
    }

    public record RevokeRequest(@NotBlank String reason) {
    }

    public record LogResponse(String id, String certificateId, String result, String verificationType, Instant verifiedAt) {
        static LogResponse from(VerificationLogEntity log) {
            return new LogResponse(log.getId().toString(), log.getCertificateId(), log.getResult(), log.getVerificationType(), log.getVerifiedAt());
        }
    }

    public record PublicVerification(
            String certificateId,
            String status,
            String studentName,
            String studentNumber,
            String degree,
            String department,
            String issueDate,
            String grade,
            String institution,
            String documentHash,
            String revokedReason,
            String chainStatus,
            String chainTxHash,
            String chainNetwork) {
        static PublicVerification missing(String id) {
            return new PublicVerification(id, "NOT_FOUND", "", "", "", "", "", "", CanonicalHasher.INSTITUTION, "", "", "", "", "");
        }
    }

    public record FileCheckResponse(String result, String fileHash, String documentHash) {
    }

    public record ScanResponse(String result, String note, List<DocumentScanService.FieldComparison> fields) {
    }
}
