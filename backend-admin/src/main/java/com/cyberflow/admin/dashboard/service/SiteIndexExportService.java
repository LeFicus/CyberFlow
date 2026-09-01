package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.SiteIndexingHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.cursor.Cursor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;

/** Generates filtered latest-indexing workbooks while preserving the current user's data scope. */
@Service
@RequiredArgsConstructor
public class SiteIndexExportService {
    private static final int MAX_DATA_ROWS_PER_SHEET = 1_000_000;

    private static final List<Column> SITE_COLUMNS = List.of(
            new Column("站点域名", "site_domain", 28),
            new Column("收录数量", "index_count", 14),
            new Column("较上次变化", "index_change", 14),
            new Column("商品数", "product_count", 12),
            new Column("建站者", "admin_name", 16),
            new Column("建站账号", "builder_username", 18),
            new Column("分组", "user_group", 10),
            new Column("服务器名称", "server_name", 20),
            new Column("服务器 IP", "server_ip", 18),
            new Column("最近收录更新", "index_updated_at", 20),
            new Column("Sitemap 提交", "last_submitted_at", 20),
            new Column("主题", "theme_name", 20),
            new Column("商品分类", "product_category", 20),
            new Column("建站日期", "created_at", 20)
    );
    private static final List<Column> BUILDER_COLUMNS = List.of(
            new Column("建站者", "dimension_name", 18),
            new Column("建站账号", "builder_username", 18),
            new Column("站点数", "site_count", 12),
            new Column("收录总数", "index_count", 14),
            new Column("较上次变化", "index_change", 14),
            new Column("平均收录", "average_index_count", 14),
            new Column("平均变化", "average_index_change", 14),
            new Column("商品总数", "product_count", 14),
            new Column("最近收录更新", "index_updated_at", 20),
            new Column("Sitemap 提交", "last_submitted_at", 20)
    );
    private static final List<Column> SERVER_COLUMNS = List.of(
            new Column("服务器", "dimension_name", 20),
            new Column("服务器 IP", "server_ip", 18),
            new Column("站点数", "site_count", 12),
            new Column("收录总数", "index_count", 14),
            new Column("较上次变化", "index_change", 14),
            new Column("平均收录", "average_index_count", 14),
            new Column("平均变化", "average_index_change", 14),
            new Column("商品总数", "product_count", 14),
            new Column("最近收录更新", "index_updated_at", 20),
            new Column("Sitemap 提交", "last_submitted_at", 20)
    );

    private final DashboardService dashboardService;
    private final SiteIndexingHistoryMapper indexingMapper;

    public record Filter(String userGroup, String adminName, String builderUsername,
                         String serverName, String serverIp, String domain, String themeName,
                         String productCategory, String siteStartDate, String siteEndDate,
                         String submittedStartDate, String submittedEndDate,
                         String updatedStartDate, String updatedEndDate,
                         Integer minIndexCount, Integer maxIndexCount, String changeDirection,
                         String serverNameExact, boolean serverIpEmpty, String builderNameExact) {}

    @Transactional(readOnly = true)
    public void writeExcel(String dimension, Filter filter, OutputStream outputStream) throws IOException {
        String normalizedDimension = dashboardService.normalizeSiteIndexDimension(dimension);
        Map<String, Object> filters = dashboardService.siteIndexFilters(
                filter.userGroup, filter.adminName, filter.builderUsername, filter.serverName,
                filter.serverIp, filter.domain, filter.themeName, filter.productCategory,
                filter.siteStartDate, filter.siteEndDate, filter.submittedStartDate,
                filter.submittedEndDate, filter.updatedStartDate, filter.updatedEndDate,
                filter.minIndexCount, filter.maxIndexCount, filter.changeDirection,
                filter.serverNameExact, filter.serverIpEmpty, filter.builderNameExact);

        if ("site".equals(normalizedDimension)) {
            try (Cursor<Map<String, Object>> rows = indexingMapper.streamLatestSites(filters)) {
                writeWorkbook(normalizedDimension, rows, outputStream);
            }
            return;
        }
        List<Map<String, Object>> rows = "builder".equals(normalizedDimension)
                ? indexingMapper.summarizeByBuilder(filters)
                : indexingMapper.summarizeByServer(filters);
        writeWorkbook(normalizedDimension, rows, outputStream);
    }

    void writeWorkbook(String dimension, Iterable<Map<String, Object>> rows,
                       OutputStream outputStream) throws IOException {
        List<Column> columns = columns(dimension);
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        try (workbook) {
            workbook.setCompressTempFiles(true);
            CellStyle headerStyle = headerStyle(workbook);
            Sheet sheet = createSheet(workbook, dimension, 1, columns, headerStyle);
            int sheetNumber = 1;
            int rowIndex = 1;
            for (Map<String, Object> values : rows) {
                if (rowIndex > MAX_DATA_ROWS_PER_SHEET) {
                    finishSheet(sheet, rowIndex, columns.size());
                    sheet = createSheet(workbook, dimension, ++sheetNumber, columns, headerStyle);
                    rowIndex = 1;
                }
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = exportValue(dimension, columns.get(i).key, values);
                    writeCell(row.createCell(i), value);
                }
            }
            finishSheet(sheet, rowIndex, columns.size());
            workbook.write(outputStream);
        } finally {
            workbook.dispose();
        }
    }

    private static Object exportValue(String dimension, String key, Map<String, Object> row) {
        if ("site".equals(dimension) && ("index_count".equals(key) || "index_change".equals(key))
                && row.get("index_updated_at") == null) return null;
        if ("site".equals(dimension) && "user_group".equals(key) && row.get(key) != null) {
            return row.get(key) + "组";
        }
        return row.get(key);
    }

    private static void writeCell(Cell cell, Object value) {
        if (value == null) return;
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof TemporalAccessor) {
            cell.setCellValue(value.toString().replace('T', ' '));
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private static Sheet createSheet(SXSSFWorkbook workbook, String dimension, int number,
                                     List<Column> columns, CellStyle headerStyle) {
        String baseName = switch (dimension) {
            case "builder" -> "建站者汇总";
            case "server" -> "服务器汇总";
            default -> "站点明细";
        };
        Sheet sheet = workbook.createSheet(number == 1 ? baseName : baseName + "-" + number);
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns.get(i).header);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, columns.get(i).width * 256);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private static void finishSheet(Sheet sheet, int rowIndex, int columnCount) {
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, columnCount - 1));
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static List<Column> columns(String dimension) {
        return switch (dimension) {
            case "builder" -> BUILDER_COLUMNS;
            case "server" -> SERVER_COLUMNS;
            default -> SITE_COLUMNS;
        };
    }

    private record Column(String header, String key, int width) {}
}
