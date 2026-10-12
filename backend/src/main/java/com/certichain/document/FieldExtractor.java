package com.certichain.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FieldExtractor {

    private static final Pattern LABEL = Pattern.compile(
            "^(student name|student|name|institution|university|degree|course|credential number|certificate id|certificate number|credential|certificate|issue date|date of issue|expiry date|expiry|grade)\\s*[:\\-]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private FieldExtractor() {
    }

    public static List<Candidate> parse(String text) {
        List<Candidate> fields = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return fields;
        }
        String[] lines = text.replace("\r", "").split("\n");
        for (String line : lines) {
            Matcher matcher = LABEL.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            String value = matcher.group(2).trim();
            if (value.isBlank() || value.length() > 500) {
                continue;
            }
            fields.add(new Candidate(canonical(matcher.group(1)), value, "MEDIUM", 1, "LABEL"));
        }
        return fields;
    }

    public static Suggestion suggestCategory(String text) {
        String haystack = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (haystack.contains("internship")) {
            return new Suggestion("INTERNSHIP", "HIGH");
        }
        if (haystack.contains("transcript")) {
            return new Suggestion("TRANSCRIPT", "HIGH");
        }
        if (haystack.contains("marksheet") || haystack.contains("mark sheet")) {
            return new Suggestion("MARKSHEET", "HIGH");
        }
        if (haystack.contains("degree") || haystack.contains("diploma")) {
            return new Suggestion("DEGREE", haystack.contains("conferred") || haystack.contains("bachelor") || haystack.contains("master") ? "HIGH" : "LOW");
        }
        if (haystack.contains("passport") || haystack.contains("aadhaar") || haystack.contains("identity")) {
            return new Suggestion("IDENTITY", "LOW");
        }
        if (haystack.contains("certificate")) {
            return new Suggestion("SKILL", "LOW");
        }
        return new Suggestion("OTHER", "LOW");
    }

    private static String canonical(String label) {
        String normalized = label.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "student name", "student", "name" -> "studentName";
            case "institution", "university" -> "institution";
            case "degree", "course", "credential" -> "degree";
            case "certificate id", "certificate number", "credential number", "certificate" -> "credentialNumber";
            case "issue date", "date of issue" -> "issueDate";
            case "expiry", "expiry date" -> "expiryDate";
            case "grade" -> "grade";
            default -> normalized;
        };
    }

    public record Candidate(String name, String value, String confidence, int page, String method) {
    }

    public record Suggestion(String category, String confidence) {
    }
}
