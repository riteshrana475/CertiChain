package com.certichain.issuer;

import com.certichain.blockchain.ChainService;
import com.certichain.certificate.CertificateEntity;
import com.certichain.certificate.CertificateRepository;
import com.certichain.security.CurrentUser;
import com.certichain.student.StudentRepository;
import com.certichain.verification.VerificationLogEntity;
import com.certichain.verification.VerificationLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class IssuerWorkspaceController {

    private final CurrentUser current;
    private final BulkIssuanceService bulk;
    private final StudentRepository students;
    private final CertificateRepository certificates;
    private final VerificationLogRepository logs;
    private final ChainService chain;
    private final AnomalyFlagRepository anomalies;
    private final CredentialAmendmentRepository amendments;

    public IssuerWorkspaceController(
            CurrentUser current,
            BulkIssuanceService bulk,
            StudentRepository students,
            CertificateRepository certificates,
            VerificationLogRepository logs,
            ChainService chain,
            AnomalyFlagRepository anomalies,
            CredentialAmendmentRepository amendments) {
        this.current = current;
        this.bulk = bulk;
        this.students = students;
        this.certificates = certificates;
        this.logs = logs;
        this.chain = chain;
        this.anomalies = anomalies;
        this.amendments = amendments;
    }

    @GetMapping(value = "/api/issuer/bulk-issuance/template", produces = "text/csv")
    public String template() {
        current.institution();
        return bulk.template();
    }

    @PostMapping(value = "/api/issuer/bulk-issuance/preview", consumes = "multipart/form-data")
    public BulkIssuanceService.JobView preview(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.getSize() > 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The CSV must be under 1 MB.");
        }
        return bulk.preview(new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @PostMapping("/api/issuer/bulk-issuance/{id}/confirm")
    public BulkIssuanceService.JobView confirm(@PathVariable UUID id) {
        return bulk.confirm(id);
    }

    @GetMapping("/api/issuer/bulk-issuance/{id}")
    public BulkIssuanceService.JobView job(@PathVariable UUID id) {
        return bulk.get(id);
    }

    @GetMapping(value = "/api/issuer/bulk-issuance/{id}/report", produces = "text/csv")
    public String report(@PathVariable UUID id) {
        return bulk.report(id);
    }

    @GetMapping("/api/issuer/anchors")
    public List<AnchorRow> anchors() {
        List<AnchorRow> rows = new ArrayList<>();
        for (CertificateEntity certificate : ownCertificates()) {
            ChainService.AnchorInspection inspection = chain.inspect(certificate.getChainTxHash(), certificate.getChainNetwork());
            rows.add(new AnchorRow(
                    certificate.getId(),
                    certificate.getDocumentHash(),
                    certificate.getStatus(),
                    inspection.state(),
                    inspection.txHash(),
                    inspection.network(),
                    inspection.blockNumber(),
                    inspection.explorerUrl(),
                    Instant.now().toString()));
        }
        return rows;
    }

    @GetMapping("/api/issuer/analytics/verification")
    public Analytics analytics() {
        List<CertificateEntity> owned = ownCertificates();
        List<String> ids = owned.stream().map(CertificateEntity::getId).toList();
        List<VerificationLogEntity> events = ids.isEmpty() ? List.of() : logs.findByCertificateIdInOrderByVerifiedAtDesc(ids);
        Map<String, Integer> outcomes = new LinkedHashMap<>();
        int recent = 0;
        Instant week = Instant.now().minus(7, ChronoUnit.DAYS);
        for (VerificationLogEntity event : events) {
            outcomes.merge(event.getResult(), 1, Integer::sum);
            if (event.getVerifiedAt().isAfter(week)) {
                recent++;
            }
        }
        long anchored = owned.stream().filter(item -> "ANCHORED".equals(item.getChainStatus())).count();
        return new Analytics(events.size(), recent, outcomes, owned.size(), anchored, "A lookup is one saved verification event. Repeating a check counts again. Charts do not include student names.");
    }

    @GetMapping("/api/issuer/anomalies")
    public List<AnomalyFlag> anomalyList() {
        return anomalies.findByInstitutionIdOrderByCreatedAtDesc(current.institution().getId());
    }

    @PostMapping("/api/issuer/anomalies/{id}/review")
    public AnomalyFlag review(@PathVariable UUID id, @RequestBody ReviewRequest request) {
        AnomalyFlag flag = anomalies.findById(id)
                .filter(item -> current.institution().getId().equals(item.getInstitutionId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag was not found."));
        flag.setReviewStatus("REVIEWED");
        flag.setReviewNote(request.note() == null ? "" : request.note().trim());
        return anomalies.save(flag);
    }

    @GetMapping("/api/issuer/credentials/{id}/amendments")
    public List<CredentialAmendment> amendmentList(@PathVariable String id) {
        ownCertificates().stream().filter(item -> item.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificate was not found."));
        return amendments.findByCertificateIdOrderByCreatedAtDesc(id);
    }

    private List<CertificateEntity> ownCertificates() {
        List<String> studentIds = students.findByInstitutionIdOrderByNameAsc(current.institution().getId()).stream()
                .map(item -> item.getId())
                .toList();
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return certificates.findByStudentIdInOrderByIssueDateDesc(studentIds);
    }

    public record AnchorRow(
            String certificateId,
            String documentHash,
            String credentialStatus,
            String anchorState,
            String txHash,
            String network,
            Long blockNumber,
            String explorerUrl,
            String checkedAt) {
    }

    public record Analytics(
            int lookups,
            int lookupsLast7Days,
            Map<String, Integer> outcomes,
            int credentials,
            long anchored,
            String definition) {
    }

    public record ReviewRequest(String note) {
    }
}
