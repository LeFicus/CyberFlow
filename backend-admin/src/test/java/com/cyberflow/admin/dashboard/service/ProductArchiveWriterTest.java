package com.cyberflow.admin.dashboard.service;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductArchiveWriterTest {
    @Test void csvSplitsPartsAndEscapesFormulaAndQuotedText() throws Exception {
        var out = new ByteArrayOutputStream();
        try (var writer = new ProductArchiveWriter(out, "csv", 2)) {
            for (int i = 0; i < 5; i++) writer.write(Map.of("sku", "=" + i, "name", "hello,\"world\""));
        }
        var entries = unzip(out.toByteArray()); assertEquals(3, entries.size());
        String csv = new String(entries.get(0), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF")); assertTrue(csv.contains("\"'=0\""));
        assertTrue(csv.contains("\"hello,\"\"world\"\"\""));
        assertEquals(3, csv.lines().count());
    }
    @Test void excelTruncatesAllLongFieldsAndPreservesTextCells() throws Exception {
        var out = new ByteArrayOutputStream();
        try (var writer = new ProductArchiveWriter(out, "xlsx", 2)) {
            writer.write(Map.of("sku", "000123", "images", "x".repeat(40000), "name", "=SUM(1,1)"));
        }
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(unzip(out.toByteArray()).get(0)))) {
            var sheet = workbook.getSheetAt(0); assertEquals(1, sheet.getLastRowNum());
            assertEquals("000123", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("=SUM(1,1)", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals(32767, sheet.getRow(1).getCell(5).getStringCellValue().length());
        }
    }
    @Test void emptyResultStillHasAHeaderFile() throws Exception {
        var out = new ByteArrayOutputStream();
        try (var writer = new ProductArchiveWriter(out, "csv", 2)) {}
        assertEquals(1, unzip(out.toByteArray()).size());
    }
    private List<byte[]> unzip(byte[] bytes) throws IOException {
        List<byte[]> result = new ArrayList<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zip.getNextEntry() != null) result.add(zip.readAllBytes());
        }
        return result;
    }
}
