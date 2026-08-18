package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.EcommerceProductMapper;
import com.cyberflow.admin.common.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.cursor.Cursor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts normalized crawl records into import-compatible CSV columns. */
@Service
@RequiredArgsConstructor
public class ProductExportService {
    private static final List<String> EXCEL_HEADERS = List.of(
            "SKU", "Name", "Description", "Regular price", "Categories", "Images",
            "cf_opingts", "自定义分类", "原站域名", "分布网站识别", "语言"
    );
    private final EcommerceProductMapper productMapper;
    private final DataScopeService dataScopeService;

    public List<List<String>> buildRows(String engine, String domain) {
        List<Map<String, Object>> products = productMapper.listProductsForExport(domain, ownerName());
        return switch (engine) {
            case "shopify" -> shopify(products);
            case "woocommerce" -> woo(products);
            case "bigcommerce" -> bigCommerce(products);
            default -> throw new IllegalArgumentException("Unsupported export engine: " + engine);
        };
    }

    public List<String> headers(String engine) {
        return switch (engine) {
            case "shopify" -> List.of("Handle", "Title", "Body (HTML)", "Vendor", "Type", "Tags", "Published", "Variant SKU", "Variant Price", "Image Src");
            case "woocommerce" -> List.of("Type", "SKU", "Name", "Published", "Regular price", "Categories", "Images", "Description");
            case "bigcommerce" -> List.of("Item Type", "Product Name", "Product Code/SKU", "Price", "Category", "Product Description", "Product Image File - 1");
            default -> throw new IllegalArgumentException("Unsupported export engine: " + engine);
        };
    }

    /** Backward-compatible entry point for callers that only provide domain/category. */
    public void writeExcel(String domain, String customCategory, OutputStream outputStream) throws IOException {
        writeExcel(domain == null || domain.isBlank() ? List.of() : List.of(domain),
                customCategory == null || customCategory.isBlank() ? List.of() : List.of(customCategory),
                List.of(), List.of(), null, outputStream);
    }

    /** Write a filtered XLSX export without loading the matching rows into memory. */
    @Transactional(readOnly = true)
    public void writeExcel(List<String> domains, List<String> customCategories,
                           List<String> productCategories, List<String> productRoles, String name,
                           OutputStream outputStream) throws IOException {
        List<String> domainFilter = normalizeDomains(domains);
        List<String> customCategoryFilter = normalizeCategories(customCategories);
        List<String> productCategoryFilter = normalizeCategories(productCategories);
        List<String> productRoleFilter = normalizeProductRoles(productRoles);
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            List<Sheet> sheets = new ArrayList<>();
            Sheet sheet = createSheet(workbook, sheets, 1, headerStyle);
            int rowIndex = 1;
            try (Cursor<Map<String, Object>> products = productMapper.streamProductsForExport(
                    domainFilter, customCategoryFilter, productCategoryFilter, productRoleFilter, name, ownerName())) {
                for (Map<String, Object> product : products) {
                    // Excel has a hard limit of 1,048,576 rows per sheet. Split
                    // large exports instead of failing after the first million rows.
                    if (rowIndex > 1_000_000) {
                        finishSheet(sheet, rowIndex);
                        sheet = createSheet(workbook, sheets, sheets.size() + 1, headerStyle);
                        rowIndex = 1;
                    }
                    Row row = sheet.createRow(rowIndex++);
                    List<String> values = excelValues(product);
                    for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
                }
            }
            finishSheet(sheet, rowIndex);
            workbook.write(outputStream);
        }
    }

    /** Stream a CSV export directly to the HTTP response writer. */
    @Transactional(readOnly = true)
    public void writeCsv(String engine, List<String> domains, List<String> customCategories,
                         List<String> productCategories, List<String> productRoles,
                         String name, Writer writer) throws IOException {
        writeCsvRow(writer, headers(engine));
        try (Cursor<Map<String, Object>> products = productMapper.streamProductsForExport(
                normalizeDomains(domains), normalizeCategories(customCategories),
                normalizeCategories(productCategories), normalizeProductRoles(productRoles), name, ownerName())) {
            for (Map<String, Object> product : products) writeCsvRow(writer, csvValues(engine, product));
        }
    }

    private static List<String> normalizeCategories(List<String> categories) {
        if (categories == null) return List.of();
        return categories.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).distinct().toList();
    }

    private static List<String> normalizeProductRoles(List<String> roles) {
        if (roles == null) return List.of();
        return roles.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase())
                .filter(value -> value.equals("main") || value.equals("supplement"))
                .distinct().toList();
    }

    private String ownerName() {
        var scope = dataScopeService.current();
        return scope.administrator() ? null : scope.ownerName();
    }

    private static List<String> normalizeDomains(List<String> domains) {
        if (domains == null) return List.of();
        return domains.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).distinct().toList();
    }

    private static Sheet createSheet(SXSSFWorkbook workbook, List<Sheet> sheets, int number, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(number == 1 ? "Products" : "Products-" + number);
        sheet.createFreezePane(0, 1);
        Row header = sheet.createRow(0);
        for (int i = 0; i < EXCEL_HEADERS.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(EXCEL_HEADERS.get(i));
            cell.setCellStyle(headerStyle);
        }
        int[] widths = {20, 36, 80, 16, 30, 60, 16, 22, 28, 16, 10};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
        sheets.add(sheet);
        return sheet;
    }

    private static void finishSheet(Sheet sheet, int rowIndex) {
        if (rowIndex > 1) sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIndex - 1, 0, EXCEL_HEADERS.size() - 1));
    }

    private static List<String> excelValues(Map<String, Object> product) {
        return List.of(value(product, "sku"), value(product, "name"), excelText(value(product, "description")),
                price2(product.get("regular_price")), value(product, "categories"), value(product, "images"),
                value(product, "cf_opingts"), value(product, "custom_category"), value(product, "source_domain"),
                "0", value(product, "language").isBlank() ? "en" : value(product, "language"));
    }

    private static List<String> csvValues(String engine, Map<String, Object> product) {
        return switch (engine) {
            case "shopify" -> List.of(slug(value(product, "name"), value(product, "sku")), value(product, "name"), value(product, "description"),
                    value(product, "source_domain"), value(product, "categories"), value(product, "categories"), "TRUE",
                    value(product, "sku"), price(product.get("regular_price")), value(product, "images"));
            case "woocommerce" -> List.of("simple", value(product, "sku"), value(product, "name"), "1",
                    price(product.get("regular_price")), value(product, "categories"), value(product, "images"), value(product, "description"));
            case "bigcommerce" -> List.of("Product", value(product, "name"), value(product, "sku"), price(product.get("regular_price")),
                    value(product, "categories"), value(product, "description"), value(product, "images"));
            default -> throw new IllegalArgumentException("Unsupported export engine: " + engine);
        };
    }

    private static void writeCsvRow(Writer writer, List<String> row) throws IOException {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) writer.write(',');
            writer.write('"');
            writer.write(row.get(i).replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write('\n');
    }

    private List<List<String>> shopify(List<Map<String, Object>> products) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> p : products) rows.add(List.of(
                slug(value(p, "name"), value(p, "sku")), value(p, "name"), value(p, "description"),
                value(p, "source_domain"), value(p, "categories"), value(p, "categories"), "TRUE",
                value(p, "sku"), price(p.get("regular_price")), value(p, "images")));
        return rows;
    }

    private List<List<String>> woo(List<Map<String, Object>> products) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> p : products) rows.add(List.of("simple", value(p, "sku"), value(p, "name"), "1",
                price(p.get("regular_price")), value(p, "categories"), value(p, "images"), value(p, "description")));
        return rows;
    }

    private List<List<String>> bigCommerce(List<Map<String, Object>> products) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> p : products) rows.add(List.of("Product", value(p, "name"), value(p, "sku"),
                price(p.get("regular_price")), value(p, "categories"), value(p, "description"), value(p, "images")));
        return rows;
    }

    private static String value(Map<String, Object> p, String key) { return p.get(key) == null ? "" : String.valueOf(p.get(key)); }
    private static String price(Object value) { return value instanceof BigDecimal d ? d.toPlainString() : value == null ? "" : String.valueOf(value); }
    private static String price2(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return "0.00";
        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException ignored) {
            return "0.00";
        }
    }
    private static String excelText(String value) {
        return value.length() <= 32767 ? value : value.substring(0, 32767);
    }
    private static String slug(String name, String fallback) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? fallback : slug;
    }
}
