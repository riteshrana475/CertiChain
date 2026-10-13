package com.certichain.student;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StudentRoster {

    private StudentRoster() {
    }

    public static List<Row> parse(String csv) {
        List<Row> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String[] lines = csv.replace("\uFEFF", "").replace("\r", "").split("\n");
        int header = -1;
        for (int index = 0; index < lines.length; index++) {
            if (!lines[index].isBlank()) {
                header = index;
                break;
            }
        }
        if (header < 0) {
            return rows;
        }
        String[] titles = split(lines[header]);
        int number = indexOf(titles, "studentnumber", "student number");
        int email = indexOf(titles, "email");
        int name = indexOf(titles, "name", "studentname", "student name");
        int department = indexOf(titles, "department");
        int course = indexOf(titles, "course", "program");
        int year = indexOf(titles, "graduationyear", "graduation year", "year");
        if (number < 0 || email < 0 || name < 0 || department < 0 || course < 0 || year < 0) {
            rows.add(new Row(header + 1, "", "", "", "", "", 0, "The header must include studentNumber, email, name, department, course, and graduationYear."));
            return rows;
        }
        for (int index = header + 1; index < lines.length; index++) {
            if (lines[index].isBlank()) {
                continue;
            }
            String[] cells = split(lines[index]);
            rows.add(new Row(
                    index + 1,
                    cell(cells, number),
                    cell(cells, email),
                    cell(cells, name),
                    cell(cells, department),
                    cell(cells, course),
                    year(cell(cells, year)),
                    ""));
        }
        return rows;
    }

    private static int year(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static int indexOf(String[] titles, String... names) {
        for (int index = 0; index < titles.length; index++) {
            String title = titles[index].toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
            for (String name : names) {
                if (title.equals(name.replace(" ", ""))) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String cell(String[] cells, int index) {
        return index >= 0 && index < cells.length ? cells[index].trim() : "";
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

    public record Row(
            int line,
            String studentNumber,
            String email,
            String name,
            String department,
            String course,
            int graduationYear,
            String error) {
    }
}
