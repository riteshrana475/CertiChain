package com.certichain.student;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<StudentEntity, String> {

    List<StudentEntity> findByInstitutionIdOrderByNameAsc(UUID institutionId);

    Optional<StudentEntity> findByInstitutionIdAndStudentNumber(UUID institutionId, String studentNumber);

    Optional<StudentEntity> findByEmailIgnoreCase(String email);

    boolean existsByInstitutionIdAndStudentNumber(UUID institutionId, String studentNumber);
}
