package com.certichain.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StudentRosterTest {

    @Test
    void readsARegistrarHeaderAndSkipsABlankLine() {
        var rows = StudentRoster.parse("""
                studentNumber,email,name,department,course,graduationYear
                CC2022991,riya@university.edu,Riya Sen,Computer Science,B.Tech Computer Science,2026

                """);

        assertEquals(1, rows.size());
        assertEquals("CC2022991", rows.get(0).studentNumber());
        assertEquals("riya@university.edu", rows.get(0).email());
        assertEquals(2026, rows.get(0).graduationYear());
    }

    @Test
    void rejectsAFileWithoutTheRosterColumns() {
        var rows = StudentRoster.parse("name,grade\nRiya,8.0\n");

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).error().contains("header"));
    }
}