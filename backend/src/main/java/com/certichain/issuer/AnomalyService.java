package com.certichain.issuer;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AnomalyService {

    private final AnomalyFlagRepository flags;

    public AnomalyService(AnomalyFlagRepository flags) {
        this.flags = flags;
    }

    public void record(UUID institutionId, String rule, String severity, String targetId, String detail) {
        AnomalyFlag flag = new AnomalyFlag();
        flag.setId(UUID.randomUUID());
        flag.setInstitutionId(institutionId);
        flag.setRuleName(rule);
        flag.setSeverity(severity);
        flag.setTargetId(targetId == null ? "" : targetId);
        flag.setDetail(detail);
        flag.setReviewStatus("OPEN");
        flag.setCreatedAt(Instant.now());
        flags.save(flag);
    }
}
