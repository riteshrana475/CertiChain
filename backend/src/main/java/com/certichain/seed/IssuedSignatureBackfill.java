package com.certichain.seed;

import com.certichain.certificate.CertificateEntity;
import com.certichain.certificate.CertificateRepository;
import com.certichain.crypto.CanonicalHasher;
import com.certichain.crypto.CredentialSigner;
import com.certichain.institution.InstitutionRepository;
import com.certichain.student.StudentEntity;
import com.certichain.student.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class IssuedSignatureBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IssuedSignatureBackfill.class);

    private final CertificateRepository certificates;
    private final StudentRepository students;
    private final InstitutionRepository institutions;
    private final CredentialSigner signer;

    public IssuedSignatureBackfill(
            CertificateRepository certificates,
            StudentRepository students,
            InstitutionRepository institutions,
            CredentialSigner signer) {
        this.certificates = certificates;
        this.students = students;
        this.institutions = institutions;
        this.signer = signer;
    }

    @Override
    public void run(ApplicationArguments args) {
        int signed = 0;
        for (CertificateEntity certificate : certificates.findAll()) {
            if ("DRAFT".equals(certificate.getStatus()) || certificate.getDocumentHash() == null || certificate.getDocumentHash().isBlank()) {
                continue;
            }
            if (certificate.getSignature() != null && !certificate.getSignature().isBlank()) {
                continue;
            }
            StudentEntity student = students.findById(certificate.getStudentId()).orElse(null);
            if (student == null || certificate.getIssueDate() == null) {
                continue;
            }
            String institution = student.getInstitutionId() == null
                    ? CanonicalHasher.INSTITUTION
                    : institutions.findById(student.getInstitutionId()).map(item -> item.getName()).orElse(CanonicalHasher.INSTITUTION);
            String canonical = CanonicalHasher.canonical(
                    certificate.getId(),
                    student.getName(),
                    certificate.getDegree(),
                    certificate.getIssueDate().toString(),
                    certificate.getGrade(),
                    institution);
            signer.apply(certificate, CredentialSigner.payload(canonical, certificate.getDocumentHash()));
            if (certificate.getSignature() != null && !certificate.getSignature().isBlank()) {
                certificates.save(certificate);
                signed++;
            }
        }
        if (signed > 0) {
            log.info("Signed {} existing credential records with the local issuer key.", signed);
        }
    }
}
