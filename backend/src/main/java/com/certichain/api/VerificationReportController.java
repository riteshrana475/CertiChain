package com.certichain.api;

import com.certichain.verification.VerificationReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerificationReportController {

    private final VerificationReportService reports;

    public VerificationReportController(VerificationReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/api/public/verify/{id}/report")
    public VerificationReportService.ReportView report(@PathVariable String id) {
        return reports.report(id);
    }
}
