package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.EcommerceProductMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
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

    public List<List<String>> buildRows(String engine, String domain) {
        List<Map<String, Object>> products = productMapper.listProductsForExport(domain);
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

    /** Write the crawler's normalized schema as a genuine streaming XLSX workbook. */
    public void writeExcel(String domain, String customCategory, OutputStream outputStream) throws IOException {
        List<Map<String, Object>> products = productMapper.listProductsForExcelExport(domain, customCategory);
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Products");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int i = 0; i < EXCEL_HEADERS.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(EXCEL_HEADERS.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (Map<String, Object> product : products) {
                Row row = sheet.createRow(rowIndex++);
                List<String> values = List.of(
                        value(product, "sku"),
                        value(product, "name"),
                        excelText(value(product, "description")),
                        price2(product.get("regular_price")),
                        value(product, "categories"),
                        value(product, "images"),
                        value(product, "cf_opingts"),
                        value(product, "custom_category"),
                        value(product, "source_domain"),
                        "0",
                        value(product, "language").isBlank() ? "en" : value(product, "language")
                );
                for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
            }

            int[] widths = {20, 36, 80, 16, 30, 60, 16, 22, 28, 16, 10};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            if (rowIndex > 1) sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIndex - 1, 0, EXCEL_HEADERS.size() - 1));
            workbook.write(outputStream);
        }
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
