package com.certichain.certificate;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<CertificateEntity, String> {

    List<CertificateEntity> findAllByOrderByIssueDateDesc();

    List<CertificateEntity> findByStudentIdInOrderByIssueDateDesc(Collection<String> studentIds);

    List<CertificateEntity> findByStudentId(String studentId);

    boolean existsByStudentId(String studentId);

    long countByIdStartingWith(String prefix);
}
