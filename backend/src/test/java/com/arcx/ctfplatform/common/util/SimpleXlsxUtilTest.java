package com.arcx.ctfplatform.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleXlsxUtilTest {

    private byte[] buildWorksheetZip(String sheetXml) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zos.write(sheetXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Rejects malicious XLSX cell reference ZZZZZZ1 instead of triggering OutOfMemoryError")
    void testRejectsOomColumnReference() throws IOException {
        byte[] malicious = buildWorksheetZip(
                "<?xml version=\"1.0\"?><worksheet><sheetData><row r=\"1\">"
                        + "<c r=\"ZZZZZZ1\" t=\"inlineStr\"><is><t>boom</t></is></c>"
                        + "</row></sheetData></worksheet>"
        );

        assertThatThrownBy(() -> SimpleXlsxUtil.readSheet(new ByteArrayInputStream(malicious)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds maximum allowed column count");
    }

    @Test
    @DisplayName("Rejects 7-letter column reference ZZZZZZZ1 instead of int overflow / ArrayIndexOutOfBoundsException")
    void testRejectsIntOverflowColumnReference() throws IOException {
        byte[] malicious = buildWorksheetZip(
                "<?xml version=\"1.0\"?><worksheet><sheetData><row r=\"1\">"
                        + "<c r=\"ZZZZZZZ1\" t=\"inlineStr\"><is><t>boom</t></is></c>"
                        + "</row></sheetData></worksheet>"
        );

        assertThatThrownBy(() -> SimpleXlsxUtil.readSheet(new ByteArrayInputStream(malicious)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds maximum allowed column count");
    }

    @Test
    @DisplayName("Allows column up to MAX_COLUMNS (IV1 = 256) and rejects IW1 (257)")
    void testColumnLimitBoundary() throws IOException {
        byte[] validBoundary = buildWorksheetZip(
                "<?xml version=\"1.0\"?><worksheet><sheetData><row r=\"1\">"
                        + "<c r=\"IV1\" t=\"inlineStr\"><is><t>ok</t></is></c>"
                        + "</row></sheetData></worksheet>"
        );
        List<List<String>> rows = SimpleXlsxUtil.readSheet(new ByteArrayInputStream(validBoundary));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).hasSize(SimpleXlsxUtil.MAX_COLUMNS);
        assertThat(rows.get(0).get(SimpleXlsxUtil.MAX_COLUMNS - 1)).isEqualTo("ok");

        byte[] overBoundary = buildWorksheetZip(
                "<?xml version=\"1.0\"?><worksheet><sheetData><row r=\"1\">"
                        + "<c r=\"IW1\" t=\"inlineStr\"><is><t>too_far</t></is></c>"
                        + "</row></sheetData></worksheet>"
        );
        assertThatThrownBy(() -> SimpleXlsxUtil.readSheet(new ByteArrayInputStream(overBoundary)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds maximum allowed column count");
    }
}
