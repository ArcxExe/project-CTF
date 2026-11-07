package com.arcx.ctfplatform.common.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Lightweight, zero-dependency OOXML (.xlsx) reader and writer using standard JDK ZIP + XML APIs.
 */
public final class SimpleXlsxUtil {

    private SimpleXlsxUtil() {}

    public static boolean isXlsxFile(String filename, byte[] bytes) {
        if (filename != null && filename.toLowerCase().endsWith(".xlsx")) {
            return true;
        }
        return bytes != null
                && bytes.length >= 4
                && bytes[0] == 0x50
                && bytes[1] == 0x4B
                && bytes[2] == 0x03
                && bytes[3] == 0x04;
    }

    public static List<List<String>> readSheet(InputStream xlsxStream) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(xlsxStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zis.readAllBytes());
                }
            }
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            List<String> sharedStrings = new ArrayList<>();
            byte[] sharedStringsBytes = entries.get("xl/sharedStrings.xml");
            if (sharedStringsBytes != null) {
                Document ssDoc = builder.parse(new ByteArrayInputStream(sharedStringsBytes));
                NodeList siNodes = ssDoc.getElementsByTagName("si");
                for (int i = 0; i < siNodes.getLength(); i++) {
                    Element si = (Element) siNodes.item(i);
                    NodeList tNodes = si.getElementsByTagName("t");
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < tNodes.getLength(); j++) {
                        sb.append(tNodes.item(j).getTextContent());
                    }
                    sharedStrings.add(sb.toString());
                }
            }

            byte[] sheetBytes = entries.get("xl/worksheets/sheet1.xml");
            if (sheetBytes == null) {
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    if (e.getKey().startsWith("xl/worksheets/sheet") && e.getKey().endsWith(".xml")) {
                        sheetBytes = e.getValue();
                        break;
                    }
                }
            }
            if (sheetBytes == null) {
                return List.of();
            }

            Document sheetDoc = builder.parse(new ByteArrayInputStream(sheetBytes));
            NodeList rowNodes = sheetDoc.getElementsByTagName("row");
            List<List<String>> rows = new ArrayList<>();

            for (int r = 0; r < rowNodes.getLength(); r++) {
                Element rowElem = (Element) rowNodes.item(r);
                NodeList cellNodes = rowElem.getElementsByTagName("c");
                List<String> rowValues = new ArrayList<>();
                int nextColIndex = 0;

                for (int c = 0; c < cellNodes.getLength(); c++) {
                    Element cellElem = (Element) cellNodes.item(c);
                    String ref = cellElem.getAttribute("r");
                    int colIndex = parseColumnIndex(ref, nextColIndex);
                    while (rowValues.size() < colIndex) {
                        rowValues.add("");
                    }

                    String type = cellElem.getAttribute("t");
                    String value = extractCellValue(cellElem, type, sharedStrings);
                    if (rowValues.size() == colIndex) {
                        rowValues.add(value);
                    } else {
                        rowValues.set(colIndex, value);
                    }
                    nextColIndex = colIndex + 1;
                }
                rows.add(rowValues);
            }

            return rows;
        } catch (Exception e) {
            throw new IOException("Failed to parse XLSX stream: " + e.getMessage(), e);
        }
    }

    private static String extractCellValue(Element cellElem, String type, List<String> sharedStrings) {
        if ("inlineStr".equals(type)) {
            NodeList tNodes = cellElem.getElementsByTagName("t");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tNodes.getLength(); i++) {
                sb.append(tNodes.item(i).getTextContent());
            }
            return sb.toString();
        }
        NodeList vNodes = cellElem.getElementsByTagName("v");
        if (vNodes.getLength() == 0) {
            return "";
        }
        String raw = vNodes.item(0).getTextContent();
        if ("s".equals(type)) {
            try {
                int idx = Integer.parseInt(raw.trim());
                if (idx >= 0 && idx < sharedStrings.size()) {
                    return sharedStrings.get(idx);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return raw != null ? raw : "";
    }

    private static int parseColumnIndex(String cellRef, int fallback) {
        if (cellRef == null || cellRef.isEmpty()) {
            return fallback;
        }
        int col = 0;
        int len = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char ch = Character.toUpperCase(cellRef.charAt(i));
            if (ch >= 'A' && ch <= 'Z') {
                col = col * 26 + (ch - 'A' + 1);
                len++;
            } else {
                break;
            }
        }
        return len > 0 ? col - 1 : fallback;
    }

    public static String toColumnName(int colIndexZeroBased) {
        int col = colIndexZeroBased + 1;
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }

    public static byte[] writeSheet(String sheetName, List<String> headers, List<List<Object>> rows) {
        String safeSheetName = (sheetName == null || sheetName.isBlank())
                ? "Sheet1"
                : sheetName.replaceAll("[\\\\/?*\\[\\]:]", "_");
        if (safeSheetName.length() > 31) {
            safeSheetName = safeSheetName.substring(0, 31);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml());
            writeZipEntry(zos, "_rels/.rels", buildRootRelsXml());
            writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(safeSheetName));
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml());
            writeZipEntry(zos, "xl/styles.xml", buildStylesXml());
            writeZipEntry(zos, "xl/worksheets/sheet1.xml", buildWorksheetXml(headers, rows));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate XLSX archive", e);
        }
        return baos.toByteArray();
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, String xml) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(xml.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String buildContentTypesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """.stripIndent();
    }

    private static String buildRootRelsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """.stripIndent();
    }

    private static String buildWorkbookXml(String sheetName) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="%s" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """.formatted(escapeXml(sheetName)).stripIndent();
    }

    private static String buildWorkbookRelsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """.stripIndent();
    }

    private static String buildStylesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="2">
                    <font><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="11"/><name val="Calibri"/></font>
                  </fonts>
                  <fills count="3">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FFEFEFEF"/><bgColor indexed="64"/></patternFill></fill>
                  </fills>
                  <borders count="2">
                    <border><left/><right/><top/><bottom/><diagonal/></border>
                    <border>
                      <left style="thin"><color auto="1"/></left>
                      <right style="thin"><color auto="1"/></right>
                      <top style="thin"><color auto="1"/></top>
                      <bottom style="thin"><color auto="1"/></bottom>
                      <diagonal/>
                    </border>
                  </borders>
                  <cellStyleXfs count="1">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
                  </cellStyleXfs>
                  <cellXfs count="3">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyBorder="1"/>
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1"/>
                    <xf numFmtId="2" fontId="0" fillId="0" borderId="1" applyNumberFormat="1" applyBorder="1"/>
                  </cellXfs>
                </styleSheet>
                """.stripIndent();
    }

    private static String buildWorksheetXml(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
        sb.append("  <sheetData>\n");

        int rowNum = 1;
        if (headers != null && !headers.isEmpty()) {
            sb.append("    <row r=\"").append(rowNum).append("\">");
            for (int c = 0; c < headers.size(); c++) {
                String cellRef = toColumnName(c) + rowNum;
                String val = headers.get(c) != null ? headers.get(c) : "";
                sb.append("<c r=\"").append(cellRef).append("\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escapeXml(val))
                        .append("</t></is></c>");
            }
            sb.append("</row>\n");
            rowNum++;
        }

        if (rows != null) {
            for (List<Object> row : rows) {
                boolean isBoldRow = row != null && row.size() > 2 && "MAX".equalsIgnoreCase(String.valueOf(row.get(2)));
                sb.append("    <row r=\"").append(rowNum).append("\">");
                if (row != null) {
                    for (int c = 0; c < row.size(); c++) {
                        Object cell = row.get(c);
                        String cellRef = toColumnName(c) + rowNum;
                        int styleIdx = isBoldRow ? 1 : 0;
                        if (cell == null) {
                            sb.append("<c r=\"").append(cellRef).append("\" s=\"").append(styleIdx).append("\"/>");
                        } else if (cell instanceof Integer || cell instanceof Long || cell instanceof Short) {
                            sb.append("<c r=\"").append(cellRef).append("\" s=\"").append(styleIdx).append("\"><v>")
                                    .append(cell)
                                    .append("</v></c>");
                        } else if (cell instanceof Number num) {
                            double d = num.doubleValue();
                            if (Double.isFinite(d)) {
                                int numStyle = isBoldRow ? 1 : 2;
                                sb.append("<c r=\"").append(cellRef).append("\" s=\"").append(numStyle).append("\"><v>")
                                        .append(d)
                                        .append("</v></c>");
                            } else {
                                sb.append("<c r=\"").append(cellRef).append("\" s=\"").append(styleIdx).append("\"/>");
                            }
                        } else {
                            String str = String.valueOf(cell);
                            sb.append("<c r=\"").append(cellRef).append("\" s=\"").append(styleIdx)
                                    .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                                    .append(escapeXml(str))
                                    .append("</t></is></c>");
                        }
                    }
                }
                sb.append("</row>\n");
                rowNum++;
            }
        }

        sb.append("  </sheetData>\n");
        sb.append("</worksheet>\n");
        return sb.toString();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
