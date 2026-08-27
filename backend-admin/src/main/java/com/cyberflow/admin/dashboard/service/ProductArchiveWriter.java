package com.cyberflow.admin.dashboard.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Bounded-memory ZIP writer. Each part is independently usable in Excel/import tools. */
public final class ProductArchiveWriter implements AutoCloseable {
    static final List<String> HEADERS = List.of("SKU", "Name", "Description", "Regular price", "Categories",
            "Images", "cf_opingts", "自定义分类", "原站域名", "分布网站识别", "语言", "产品标签");
    static final List<String> KEYS = List.of("sku", "name", "description", "regular_price", "categories",
            "images", "cf_opingts", "custom_category", "source_domain", "", "language", "product_role");
    private final ZipOutputStream zip;
    private final String format;
    private final int partRows;
    private int part = 0, rows = 0;
    private Writer csv;
    private SXSSFWorkbook workbook;
    private Sheet sheet;

    public ProductArchiveWriter(OutputStream output, String format, int partRows) {
        this.zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
        this.format = format; this.partRows = partRows;
    }
    public int parts() { return part; }

    public void write(Map<String, Object> product) throws IOException {
        if (part == 0 || rows == partRows) { finishPart(); startPart(); }
        List<String> values = new ArrayList<>();
        for (String key : KEYS) values.add(key.isEmpty() ? "0" : Objects.toString(product.get(key), ""));
        if ("csv".equals(format)) writeCsv(csv, values);
        else {
            Row row = sheet.createRow(rows + 1);
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                // Every text field can exceed Excel's cell limit, not only descriptions.
                row.createCell(i, CellType.STRING).setCellValue(value.substring(0, Math.min(32767, value.length())));
            }
        }
        rows++;
    }
    private void startPart() throws IOException {
        part++; rows = 0;
        zip.putNextEntry(new ZipEntry(String.format(Locale.ROOT, "products-%04d.%s", part, format)));
        if ("csv".equals(format)) {
            csv = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
            csv.write('\uFEFF'); writeCsv(csv, HEADERS);
        } else {
            workbook = new SXSSFWorkbook(100); workbook.setCompressTempFiles(true);
            sheet = workbook.createSheet("Products"); sheet.createFreezePane(0, 1);
            var style = workbook.createCellStyle(); var font = workbook.createFont(); font.setBold(true); style.setFont(font);
            var header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                var cell = header.createCell(i); cell.setCellValue(HEADERS.get(i)); cell.setCellStyle(style);
                sheet.setColumnWidth(i, (i == 2 ? 60 : 25) * 256);
            }
        }
    }
    private void finishPart() throws IOException {
        if (part == 0) return;
        if (csv != null) { csv.flush(); csv = null; }
        if (workbook != null) {
            SXSSFWorkbook current = workbook; workbook = null;
            try {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rows, 0, HEADERS.size() - 1));
                current.write(zip);
            } finally { try { current.close(); } finally { current.dispose(); } }
        }
        zip.closeEntry();
    }
    static void writeCsv(Writer writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(',');
            String value = values.get(i);
            // Untrusted scraped text must not become spreadsheet formulas when opened.
            if (value.matches("(?s)^[\\s]*[=+@-].*") || value.startsWith("\t") || value.startsWith("\r")) value = "'" + value;
            writer.write('"'); writer.write(value.replace("\"", "\"\"")); writer.write('"');
        }
        writer.write("\r\n");
    }
    @Override public void close() throws IOException {
        try { if (part == 0) startPart(); finishPart(); }
        finally { zip.close(); }
    }
}
