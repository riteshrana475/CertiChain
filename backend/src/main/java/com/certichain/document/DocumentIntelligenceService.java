package com.certichain.document;

import com.certichain.certificate.CertificateEntity;
import com.certichain.certificate.CertificateRepository;
import com.certichain.institution.InstitutionRepository;
import com.certichain.security.CurrentUser;
import com.certichain.student.StudentEntity;
import com.certichain.student.StudentRepository;
import com.certichain.wallet.ActivityRecorder;
import com.certichain.wallet.FileSignatures;
import com.certichain.wallet.WalletDocument;
import com.certichain.wallet.WalletDocumentRepository;
import com.certichain.wallet.WalletService;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentIntelligenceService {

    private final WalletService wallet;
    private final WalletDocumentRepository documents;
    private final DocumentExtractionRepository extractions;
    private final ExtractedFieldRepository fields;
    private final OcrClient ocr;
    private final ActivityRecorder activity;
    private final StudentRepository students;
    private final CertificateRepository certificates;
    private final InstitutionRepository institutions;
    private final CurrentUser current;

    public DocumentIntelligenceService(
            WalletService wallet,
            WalletDocumentRepository documents,
            DocumentExtractionRepository extractions,
            ExtractedFieldRepository fields,
            OcrClient ocr,
            ActivityRecorder activity,
            StudentRepository students,
            CertificateRepository certificates,
            InstitutionRepository institutions,
            CurrentUser current) {
        this.wallet = wallet;
        this.documents = documents;
        this.extractions = extractions;
        this.fields = fields;
        this.ocr = ocr;
        this.activity = activity;
        this.students = students;
        this.certificates = certificates;
        this.institutions = institutions;
        this.current = current;
    }

    public ExtractionView extract(UUID documentId) {
        WalletDocument document = wallet.ownDocument(documentId);
        byte[] bytes = wallet.bytes(document);
        Read read = read(document, bytes);
        FieldExtractor.Suggestion suggestion = FieldExtractor.suggestCategory(read.text());
        DocumentExtraction extraction = new DocumentExtraction();
        extraction.setId(UUID.randomUUID());
        extraction.setDocumentId(document.getId());
        extraction.setStatus(read.status());
        extraction.setMethod(read.method());
        extraction.setRawText(read.text().length() > 4000 ? read.text().substring(0, 4000) : read.text());
        extraction.setNote(read.note());
        extraction.setSuggestedCategory(suggestion.category());
        extraction.setCategoryConfidence(suggestion.confidence());
        extraction.setCreatedAt(Instant.now());
        extractions.save(extraction);
        List<FieldView> saved = new ArrayList<>();
        for (FieldExtractor.Candidate candidate : FieldExtractor.parse(read.text())) {
            ExtractedField field = new ExtractedField();
            field.setId(UUID.randomUUID());
            field.setExtractionId(extraction.getId());
            field.setFieldName(candidate.name());
            field.setCandidateValue(candidate.value());
            field.setCorrectedValue("");
            field.setConfidence(candidate.confidence());
            field.setSourcePage(candidate.page());
            field.setMethod(candidate.method());
            field.setReviewStatus("PENDING");
            fields.save(field);
            saved.add(FieldView.from(field));
        }
        activity.notify(
                document.getOwnerId(),
                "EXTRACTED".equals(read.status()) ? "OCR_COMPLETED" : "OCR_FAILED",
                "EXTRACTED".equals(read.status()) ? "Fields ready for review" : "Document could not be read",
                read.note(),
                "/wallet/documents/" + document.getId());
        return view(document, extraction, saved);
    }

    public ExtractionView latest(UUID documentId) {
        WalletDocument document = wallet.ownDocument(documentId);
        DocumentExtraction extraction = extractions.findByDocumentIdOrderByCreatedAtDesc(document.getId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This document has not been read yet."));
        List<FieldView> saved = fields.findByExtractionId(extraction.getId()).stream().map(FieldView::from).toList();
        return view(document, extraction, saved);
    }

    public ExtractionView confirm(UUID documentId, ConfirmRequest request) {
        WalletDocument document = wallet.ownDocument(documentId);
        DocumentExtraction extraction = extractions.findByDocumentIdOrderByCreatedAtDesc(document.getId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Read the document before confirming fields."));
        if (request.fields() != null) {
            for (FieldCorrection correction : request.fields()) {
                fields.findByExtractionId(extraction.getId()).stream()
                        .filter(field -> field.getFieldName().equals(correction.fieldName()))
                        .findFirst()
                        .ifPresent(field -> {
                            field.setCorrectedValue(correction.value() == null ? "" : correction.value().trim());
                            field.setReviewStatus("CONFIRMED");
                            fields.save(field);
                        });
            }
        }
        if (request.category() != null && !request.category().isBlank()) {
            document.setCategory(FileSignatures.category(request.category()));
            documents.save(document);
        }
        extraction.setStatus("CONFIRMED");
        extractions.save(extraction);
        return latest(documentId);
    }

    private ExtractionView view(WalletDocument document, DocumentExtraction extraction, List<FieldView> saved) {
        return new ExtractionView(
                extraction.getId().toString(),
                extraction.getStatus(),
                extraction.getMethod(),
                extraction.getNote(),
                extraction.getSuggestedCategory(),
                extraction.getCategoryConfidence(),
                saved,
                compare(document, saved));
    }

    private List<Comparison> compare(WalletDocument document, List<FieldView> saved) {
        if (document.getCredentialNumber() == null || document.getCredentialNumber().isBlank()) {
            return List.of();
        }
        CertificateEntity certificate = certificates.findById(document.getCredentialNumber()).orElse(null);
        if (certificate == null || "DRAFT".equals(certificate.getStatus())) {
            return List.of();
        }
        StudentEntity student = students.findById(certificate.getStudentId()).orElse(null);
        if (student == null || student.getEmail() == null || !student.getEmail().equalsIgnoreCase(current.require().getEmail())) {
            return List.of();
        }
        String institution = student.getInstitutionId() == null
                ? ""
                : institutions.findById(student.getInstitutionId()).map(item -> item.getName()).orElse("");
        return List.of(
                comparison("studentName", value(saved, "studentName"), student.getName()),
                comparison("degree", value(saved, "degree"), certificate.getDegree()),
                comparison("issueDate", value(saved, "issueDate"), certificate.getIssueDate().toString()),
                comparison("institution", value(saved, "institution"), institution),
                comparison("credentialNumber", value(saved, "credentialNumber"), certificate.getId()));
    }

    private Read read(WalletDocument document, byte[] bytes) {
        try {
            if ("application/pdf".equals(document.getContentType())) {
                String text = pdfText(bytes);
                if (text.isBlank()) {
                    return new Read("", "PDF_TEXT", "UNREADABLE", "No embedded text was found. A scanned PDF is not treated as verified, and this reader did not OCR it.");
                }
                return new Read(text, "PDF_TEXT", "EXTRACTED", "Embedded PDF text was read. These values are suggestions until you confirm them.");
            }
            String text = ocr.extract(bytes);
            return new Read(text, "OCR", "EXTRACTED", "Image text was read locally. OCR does not make this document genuine.");
        } catch (RuntimeException exception) {
            return new Read("", "NONE", "UNAVAILABLE", "The reader is unavailable. The file stays in your wallet and is not verified.");
        }
    }

    private static String pdfText(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(pdf);
        } catch (IOException exception) {
            throw new IllegalStateException("The PDF could not be read.");
        }
    }

    private static String value(List<FieldView> fields, String name) {
        return fields.stream()
                .filter(field -> field.fieldName().equals(name))
                .map(field -> field.correctedValue().isBlank() ? field.candidateValue() : field.correctedValue())
                .findFirst()
                .orElse("");
    }

    private static Comparison comparison(String field, String extracted, String record) {
        if (extracted.isBlank()) {
            return new Comparison(field, extracted, record, "MISSING");
        }
        if (extracted.equalsIgnoreCase(record)) {
            return new Comparison(field, extracted, record, "MATCH");
        }
        return new Comparison(field, extracted, record, "CONFLICT");
    }

    private record Read(String text, String method, String status, String note) {
    }

    public record FieldCorrection(String fieldName, String value) {
    }

    public record ConfirmRequest(List<FieldCorrection> fields, String category) {
    }

    public record FieldView(
            String fieldName,
            String candidateValue,
            String correctedValue,
            String confidence,
            int page,
            String method,
            String reviewStatus) {
        static FieldView from(ExtractedField field) {
            return new FieldView(field.getFieldName(), field.getCandidateValue(), field.getCorrectedValue(), field.getConfidence(), field.getSourcePage(), field.getMethod(), field.getReviewStatus());
        }
    }

    public record Comparison(String field, String extracted, String record, String outcome) {
    }

    public record ExtractionView(
            String id,
            String status,
            String method,
            String note,
            String suggestedCategory,
            String categoryConfidence,
            List<FieldView> fields,
            List<Comparison> comparisons) {
    }
}
