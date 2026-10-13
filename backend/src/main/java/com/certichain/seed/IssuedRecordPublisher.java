package com.certichain.seed;

import com.certichain.blockchain.ChainService;
import com.certichain.certificate.CertificateEntity;
import com.certichain.certificate.CertificateRepository;
import com.certichain.crypto.CanonicalHasher;
import com.certichain.document.CertificatePdf;
import com.certichain.document.CertificateStorage;
import com.certichain.institution.InstitutionRepository;
import com.certichain.student.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class IssuedRecordPublisher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IssuedRecordPublisher.class);

    private final CertificateRepository certificates;
    private final StudentRepository students;
    private final CertificateStorage storage;
    private final ChainService chain;
    private final InstitutionRepository institutions;

    public IssuedRecordPublisher(
            CertificateRepository certificates,
            StudentRepository students,
            CertificateStorage storage,
            ChainService chain,
            InstitutionRepository institutions) {
        this.certificates = certificates;
        this.students = students;
        this.storage = storage;
        this.chain = chain;
        this.institutions = institutions;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (CertificateEntity certificate : certificates.findAll()) {
            if ("DRAFT".equals(certificate.getStatus()) || certificate.getDocumentHash() == null || certificate.getDocumentHash().isBlank()) {
                continue;
            }
            var student = students.findById(certificate.getStudentId()).orElse(null);
            if (student == null) {
                continue;
            }
            boolean changed = false;
            if (certificate.getFileKey() == null || certificate.getFileKey().isBlank()) {
                String institution = student.getInstitutionId() == null
                        ? CanonicalHasher.INSTITUTION
                        : institutions.findById(student.getInstitutionId())
                                .map(item -> item.getName())
                                .orElse(CanonicalHasher.INSTITUTION);
                var record = student.getInstitutionId() == null ? null : institutions.findById(student.getInstitutionId()).orElse(null);
                changed = storage.store(certificate, CertificatePdf.render(
                        certificate,
                        student,
                        institution,
                        record == null ? "Registrar" : record.getSignatoryName(),
                        record == null ? "Registrar" : record.getSignatoryTitle()));
            }
            changed = chain.sync(certificate) || changed;
            if (changed) {
                certificates.save(certificate);
                log.info("Published {} on {}", certificate.getId(), certificate.getChainNetwork());
            }
        }
    }
}
