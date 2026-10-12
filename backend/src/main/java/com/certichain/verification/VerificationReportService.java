package com.certichain.verification;

import com.certichain.certificate.CertificateEntity;
import com.certichain.certificate.CertificateRepository;
import com.certichain.crypto.CanonicalHasher;
import com.certichain.crypto.CredentialSigner;
import com.certichain.institution.InstitutionRepository;
import com.certichain.student.StudentEntity;
import com.certichain.student.StudentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VerificationReportService {

    private final CertificateRepository certificates;
    private final StudentRepository students;
    private final InstitutionRepository institutions;
    private final CredentialSigner signer;

    public VerificationReportService(
            CertificateRepository certificates,
            StudentRepository students,
            InstitutionRepository institutions,
            CredentialSigner signer) {
        this.certificates = certificates;
        this.students = students;
        this.institutions = institutions;
        this.signer = signer;
    }

    public ReportView report(String id) {
        CertificateEntity certificate = certificates.findById(id).orElse(null);
        if (certificate == null || "DRAFT".equals(certificate.getStatus())) {
            VerificationPolicy.Report report = VerificationPolicy.assess(new VerificationPolicy.Facts(
                    false, false, false, false, "NOT_APPLICABLE", "NOT_COMPARED", "NOT_ANCHORED"));
            return new ReportView(id, report.overall(), Instant.now().toString(), report.checks());
        }
        StudentEntity student = students.findById(certificate.getStudentId()).orElse(null);
        if (student == null) {
            VerificationPolicy.Report report = VerificationPolicy.assess(new VerificationPolicy.Facts(
                    false, false, false, false, "NOT_APPLICABLE", "NOT_COMPARED", "NOT_ANCHORED"));
            return new ReportView(id, report.overall(), Instant.now().toString(), report.checks());
        }
        String institution = student.getInstitutionId() == null
                ? CanonicalHasher.INSTITUTION
                : institutions.findById(student.getInstitutionId()).map(item -> item.getName()).orElse(CanonicalHasher.INSTITUTION);
        String canonical = CanonicalHasher.canonical(
                certificate.getId(), student.getName(), certificate.getDegree(), certificate.getIssueDate().toString(), certificate.getGrade(), institution);
        String signature = signer.check(
                CredentialSigner.payload(canonical, certificate.getDocumentHash()),
                certificate.getSignature(),
                certificate.getSignerPublicKey());
        boolean hasExpiry = certificate.getExpiryDate() != null;
        boolean expired = hasExpiry && certificate.getExpiryDate().isBefore(LocalDate.now());
        String anchor = "ANCHORED".equals(certificate.getChainStatus()) ? "ANCHORED" : "NOT_ANCHORED";
        VerificationPolicy.Report report = VerificationPolicy.assess(new VerificationPolicy.Facts(
                true, "REVOKED".equals(certificate.getStatus()), hasExpiry, expired, signature, "NOT_COMPARED", anchor));
        return new ReportView(id, report.overall(), Instant.now().toString(), report.checks());
    }

    public record ReportView(String certificateId, String overall, String checkedAt, List<VerificationPolicy.Check> checks) {
    }
}
