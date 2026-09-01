package com.cyberflow.admin.dashboard.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
class SiteIndexExportServiceTest {
    private final SiteIndexExportService service = new SiteIndexExportService(null, null);

    @Test
    void siteWorkbookContainsExpectedColumnsAndLeavesUncollectedCountsBlank() throws Exception {
        var rows = List.of(
                Map.<String, Object>ofEntries(
                        Map.entry("site_domain", "collected.test"),
                        Map.entry("index_count", 42L),
                        Map.entry("index_change", 3L),
                        Map.entry("product_count", 10L),
                        Map.entry("admin_name", "张三"),
                        Map.entry("user_group", "A"),
                        Map.entry("index_updated_at", LocalDateTime.of(2026, 9, 1, 10, 30))
                ),
                Map.<String, Object>ofEntries(
                        Map.entry("site_domain", "uncollected.test"),
                        Map.entry("index_count", 0L),
                        Map.entry("index_change", 0L),
                        Map.entry("product_count", 5L)
                )
        );
        var output = new ByteArrayOutputStream();

        service.writeWorkbook("site", rows, output);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheet("站点明细");
            assertEquals("站点域名", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("最近收录更新", sheet.getRow(0).getCell(9).getStringCellValue());
            assertEquals(42, sheet.getRow(1).getCell(1).getNumericCellValue());
            assertEquals("A组", sheet.getRow(1).getCell(6).getStringCellValue());
            assertEquals("2026-09-01 10:30", sheet.getRow(1).getCell(9).getStringCellValue());
            assertEquals("uncollected.test", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(2).getCell(1).getCellType());
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(2).getCell(2).getCellType());
        }
    }

    @Test
    void groupedWorkbookUsesDimensionSpecificHeaders() throws Exception {
        var output = new ByteArrayOutputStream();
        service.writeWorkbook("server", List.of(Map.of(
                "dimension_name", "香港服务器", "server_ip", "1.2.3.4",
                "site_count", 2L, "index_count", 100L)), output);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheet("服务器汇总");
            assertEquals("服务器", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("服务器 IP", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("香港服务器", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(2, sheet.getRow(1).getCell(2).getNumericCellValue());
        }
    }
}
