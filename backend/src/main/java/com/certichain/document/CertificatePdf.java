package com.certichain.document;

import com.certichain.certificate.CertificateEntity;
import com.certichain.crypto.CanonicalHasher;
import com.certichain.student.StudentEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class CertificatePdf {

    private CertificatePdf() {
    }

    public static byte[] render(CertificateEntity certificate, StudentEntity student) {
        return render(certificate, student, CanonicalHasher.INSTITUTION);
    }

    public static byte[] render(CertificateEntity certificate, StudentEntity student, String institution) {
        return render(certificate, student, institution, "Registrar", "Registrar");
    }

    public static byte[] render(
            CertificateEntity certificate,
            StudentEntity student,
            String institution,
            String signatoryName,
            String signatoryTitle) {
        String issuer = institution == null || institution.isBlank() ? CanonicalHasher.INSTITUTION : institution;
        String signer = signatoryName == null || signatoryName.isBlank() ? "Registrar" : signatoryName;
        String office = signatoryTitle == null || signatoryTitle.isBlank() ? "Registrar" : signatoryTitle;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font title = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            PDType1Font body = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setStrokingColor(0.05f, 0.11f, 0.18f);
                content.setLineWidth(1.6f);
                content.addRect(36, 36, page.getMediaBox().getWidth() - 72, page.getMediaBox().getHeight() - 72);
                content.stroke();
                content.setStrokingColor(0.77f, 0.64f, 0.40f);
                content.setLineWidth(0.6f);
                content.addRect(44, 44, page.getMediaBox().getWidth() - 88, page.getMediaBox().getHeight() - 88);
                content.stroke();
                seal(content);
                content.beginText();
                content.setNonStrokingColor(0.05f, 0.11f, 0.18f);
                content.setFont(title, 22);
                content.newLineAtOffset(64, 730);
                content.setLeading(18);
                content.showText(safe(issuer));
                content.setFont(body, 13);
                content.newLine();
                content.newLine();
                content.showText(safe(heading(certificate.getCertificateType())));
                content.newLine();
                content.newLine();
                content.showText(safe(statement(certificate, student)));
                content.newLine();
                content.newLine();
                line(content, "Certificate ID: " + certificate.getId());
                line(content, "Student: " + safe(student.getName()));
                line(content, "Credential: " + safe(certificate.getDegree()));
                line(content, "Issue date: " + certificate.getIssueDate());
                line(content, "Grade: " + safe(certificate.getGrade()));
                line(content, "Fingerprint: " + certificate.getDocumentHash());
                content.newLine();
                line(content, "Signatory: " + safe(signer));
                line(content, "Office: " + safe(office));
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("The certificate PDF could not be written", exception);
        }
    }

    private static void seal(PDPageContentStream content) throws IOException {
        content.setStrokingColor(0.77f, 0.64f, 0.40f);
        content.setLineWidth(1.2f);
        float x = 470;
        float y = 690;
        content.moveTo(x + 28, y);
        for (int step = 1; step <= 48; step++) {
            double angle = step * Math.PI * 2 / 48;
            content.lineTo((float) (x + 28 + Math.cos(angle) * 28), (float) (y + Math.sin(angle) * 28));
        }
        content.stroke();
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD), 8);
        content.newLineAtOffset(x + 14, y - 3);
        content.showText("SEAL");
        content.endText();
    }

    static String heading(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase();
        if (normalized.contains("transcript")) {
            return "Academic transcript";
        }
        if (normalized.contains("bonafide") || normalized.contains("bona fide")) {
            return "Bonafide certificate";
        }
        if (normalized.contains("degree")) {
            return "Degree certificate";
        }
        return (type == null || type.isBlank() ? "Credential" : type.trim()) + " certificate";
    }

    private static String statement(CertificateEntity certificate, StudentEntity student) {
        String normalized = certificate.getCertificateType() == null ? "" : certificate.getCertificateType().trim().toLowerCase();
        String name = student.getName() == null ? "" : student.getName();
        String program = certificate.getDegree() == null ? "" : certificate.getDegree();
        if (normalized.contains("transcript")) {
            return "This transcript records the studies of " + name + " in " + program + ".";
        }
        if (normalized.contains("bonafide") || normalized.contains("bona fide")) {
            return "This certifies that " + name + " is a bonafide student of " + program + ".";
        }
        return "This certifies that " + name + " has been awarded " + program + ".";
    }

    private static void line(PDPageContentStream content, String value) throws IOException {
        content.showText(value);
        content.newLine();
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\x20-\\x7E]", "");
    }
}
