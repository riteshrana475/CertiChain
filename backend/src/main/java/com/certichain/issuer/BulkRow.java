package com.certichain.issuer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "bulk_rows")
public class BulkRow {
    @Id
    private UUID id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "row_number", nullable = false)
    private int rowNumber;
    @Column(name = "student_number", nullable = false)
    private String studentNumber = "";
    @Column(name = "certificate_id", nullable = false)
    private String certificateId = "";
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private String detail = "";
    @Column(nullable = false)
    private String payload = "";

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public String getCertificateId() { return certificateId; }
    public void setCertificateId(String certificateId) { this.certificateId = certificateId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
