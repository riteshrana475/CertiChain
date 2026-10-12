package com.certichain.issuer;

import java.util.ArrayList;
import java.util.List;

public final class BulkCsv {

    private BulkCsv() {
    }

    public static List<ParsedRow> parse(String csv) {
        List<ParsedRow> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String[] lines = csv.replace("\uFEFF", "").replace("\r", "").split("\n");
        int start = 0;
        if (lines.length > 0 && lines[0].toLowerCase().contains("studentnumber")) {
            start = 1;
        }
        for (int index = start; index < lines.length; index++) {
            if (lines[index].isBlank()) {
                continue;
            }
            String[] cells = split(lines[index]);
            rows.add(new ParsedRow(
                    index + 1,
                    cell(cells, 0),
                    cell(cells, 1),
                    cell(cells, 2),
                    cell(cells, 3),
                    cell(cells, 4),
                    cell(cells, 5),
                    cell(cells, 6),
                    cell(cells, 7)));
        }
        return rows;
    }

    public static String excelSafe(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
            return "'" + value;
        }
        return value;
    }

    private static String[] split(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                quoted = !quoted;
                continue;
            }
            if (character == ',' && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        cells.add(current.toString().trim());
        return cells.toArray(String[]::new);
    }

    private static String cell(String[] cells, int index) {
        return index < cells.length ? cells[index] : "";
    }

    public record ParsedRow(
            int rowNumber,
            String studentNumber,
            String email,
            String name,
            String degree,
            String certificateType,
            String issueDate,
            String grade,
            String certificateId) {
    }
}
