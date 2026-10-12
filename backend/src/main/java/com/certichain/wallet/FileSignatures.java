package com.certichain.wallet;

import java.util.Set;

public final class FileSignatures {

    public static final Set<String> CATEGORIES = Set.of(
            "DEGREE",
            "MARKSHEET",
            "TRANSCRIPT",
            "SCHOOL",
            "INTERNSHIP",
            "SKILL",
            "IDENTITY",
            "ADMISSION",
            "OTHER");

    private FileSignatures() {
    }

    public static String contentType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw new IllegalArgumentException("Upload a PDF, PNG, or JPEG.");
        }
        if (bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            return "application/pdf";
        }
        if ((bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        throw new IllegalArgumentException("Upload a PDF, PNG, or JPEG. The file contents do not match those formats.");
    }

    public static String category(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("Choose a document category.");
        }
        return normalized;
    }
}
