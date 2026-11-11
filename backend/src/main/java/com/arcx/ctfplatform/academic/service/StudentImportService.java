package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.entity.AcademicFlow;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.common.util.SimpleXlsxUtil;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import com.arcx.ctfplatform.users.entity.User;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentImportService {

    private static final Set<String> SUMMARY_MARKERS = Set.of(
            "max", "макс", "максимум", "min", "мин", "итого", "всего", "среднее", "total", "average", "avg"
    );

    private static final Pattern LAB_HEADER_PATTERN = Pattern.compile("^(?:лр|лаб|lab)\\s*№?\\s*([1-8])$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern KT_HEADER_PATTERN = Pattern.compile("^(?:кт|kt)\\s*№?\\s*([1-4])$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final AcademicFlowRepository flowRepository;
    private final AcademicGroupRepository groupRepository;
    private final StudentRepository studentRepository;

    @Autowired(required = false)
    private LabScoreService labScoreService;

    public record ImportResultDto(
            int importedCount,
            int updatedCount,
            int labsImported,
            int ktImported,
            int skippedRows
    ) {}

    @Transactional
    public void parseAndImportStudents(InputStream csvStream, User currentUser) {
        parseAndImportStudents(csvStream, null, null, currentUser);
    }

    @Transactional
    public ImportResultDto parseAndImportStudents(
            InputStream inputStream,
            String filename,
            UUID targetGroupId,
            User currentUser
    ) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            List<List<String>> rows;
            if (SimpleXlsxUtil.isXlsxFile(filename, bytes)) {
                rows = SimpleXlsxUtil.readSheet(new ByteArrayInputStream(bytes));
            } else {
                rows = readCsvRows(bytes);
            }
            return importRows(rows, targetGroupId, currentUser);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse and import students spreadsheet: " + e.getMessage(), e);
        }
    }

    private List<List<String>> readCsvRows(byte[] bytes) throws IOException {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        char delimiter = detectDelimiter(content);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                rows.add(parseCsvLine(line, delimiter));
            }
        }
        return rows;
    }

    private char detectDelimiter(String content) {
        String firstLine = content.lines().filter(l -> !l.trim().isEmpty()).findFirst().orElse("");
        long semicolons = firstLine.chars().filter(c -> c == ';').count();
        long commas = firstLine.chars().filter(c -> c == ',').count();
        long tabs = firstLine.chars().filter(c -> c == '\t').count();
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        if (semicolons > commas) {
            return ';';
        }
        return ',';
    }

    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == delimiter && !inQuotes) {
                cells.add(clean(current.toString()));
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(clean(current.toString()));
        return cells;
    }

    private ImportResultDto importRows(List<List<String>> rows, UUID targetGroupId, User currentUser) {
        if (rows == null || rows.isEmpty()) {
            return new ImportResultDto(0, 0, 0, 0, 0);
        }

        int headerRowIdx = findHeaderRowIndex(rows);
        ColumnMap colMap;
        int startDataRow;
        if (headerRowIdx >= 0) {
            colMap = mapHeaderRow(rows.get(headerRowIdx));
            startDataRow = headerRowIdx + 1;
        } else {
            // Legacy positional 6-column CSV: lastName, firstName, middleName, studentCode, groupName, flowName
            colMap = ColumnMap.legacyPositional();
            startDataRow = 0;
        }

        AcademicGroup defaultGroup = null;
        if (targetGroupId != null) {
            defaultGroup = groupRepository.findById(targetGroupId).orElse(null);
        }

        int importedCount = 0;
        int updatedCount = 0;
        int labsImported = 0;
        int ktImported = 0;
        int skippedRows = 0;

        UUID creatorId = currentUser != null ? currentUser.getId() : null;

        if (labScoreService != null) {
            labScoreService.setSuppressBroadcast(true);
        }
        try {
            for (int r = startDataRow; r < rows.size(); r++) {
                List<String> row = rows.get(r);
                if (isEmptyOrSummaryRow(row, colMap)) {
                    skippedRows++;
                    continue;
                }

                String lastName = "";
                String firstName = "";
                String middleName = "";

                if (colMap.fioCol >= 0) {
                    String fio = getCell(row, colMap.fioCol);
                    if (fio.isEmpty() || isSummaryMarker(fio)) {
                        skippedRows++;
                        continue;
                    }
                    String[] parts = fio.trim().split("\\s+");
                    if (parts.length == 1) {
                        lastName = parts[0];
                        firstName = "-";
                    } else if (parts.length == 2) {
                        lastName = parts[0];
                        firstName = parts[1];
                    } else {
                        lastName = parts[0];
                        firstName = parts[1];
                        StringBuilder mid = new StringBuilder();
                        for (int i = 2; i < parts.length; i++) {
                            if (!mid.isEmpty()) mid.append(" ");
                            mid.append(parts[i]);
                        }
                        middleName = mid.toString();
                    }
                } else {
                    lastName = getCell(row, colMap.lastNameCol);
                    firstName = getCell(row, colMap.firstNameCol);
                    middleName = getCell(row, colMap.middleNameCol);
                }

                if (lastName.isEmpty() || firstName.isEmpty() || isSummaryMarker(lastName) || isSummaryMarker(firstName)) {
                    skippedRows++;
                    continue;
                }

                String studentCode = getCell(row, colMap.studentCodeCol);
                String groupName = getCell(row, colMap.groupNameCol);
                String flowName = getCell(row, colMap.flowNameCol);

                AcademicGroup group = resolveGroup(groupName, flowName, defaultGroup);
                if (group == null) {
                    skippedRows++;
                    continue;
                }

                Integer pairNumber = parsePositiveInt(getCell(row, colMap.pairCol));
                String tokenVal = getCell(row, colMap.tokenCol);

                Optional<Student> existingOpt = findExistingStudent(studentCode, lastName, firstName, middleName, group.getId());
                Student student;
                boolean isNew = false;

                if (existingOpt.isPresent()) {
                    student = existingOpt.get();
                    student.setLastName(lastName);
                    student.setFirstName(firstName);
                    student.setMiddleName(middleName.isEmpty() ? null : middleName);
                    student.setAcademicGroup(group);
                    if (pairNumber != null) {
                        student.setPairNumber(pairNumber);
                    }
                    if (student.getCreatedBy() == null && creatorId != null) {
                        student.setCreatedBy(creatorId);
                    }
                } else {
                    isNew = true;
                    String finalCode = (studentCode != null && !studentCode.isEmpty())
                            ? studentCode
                            : generateStudentCode(group.getName(), r + 1);
                    student = Student.builder()
                            .lastName(lastName)
                            .firstName(firstName)
                            .middleName(middleName.isEmpty() ? null : middleName)
                            .studentCode(finalCode)
                            .academicGroup(group)
                            .pairNumber(pairNumber)
                            .status(StudentStatus.ACTIVE)
                            .createdBy(creatorId)
                            .build();
                }

                if (colMap.tokenCol >= 0 && !tokenVal.isEmpty()) {
                    student.setTokenInfo(tokenVal);
                }

                for (Map.Entry<Integer, Integer> ktEntry : colMap.ktCols.entrySet()) {
                    int ktNum = ktEntry.getKey();
                    String rawKt = getCell(row, ktEntry.getValue());
                    Double val = parseDoubleOrNull(rawKt);
                    if (val != null) {
                        double clamped = Math.max(0.0, Math.min(val, 25.0));
                        switch (ktNum) {
                            case 1 -> student.setKt1(clamped);
                            case 2 -> student.setKt2(clamped);
                            case 3 -> student.setKt3(clamped);
                            case 4 -> student.setKt4(clamped);
                        }
                        ktImported++;
                    }
                }

                student = studentRepository.save(student);
                if (isNew) {
                    importedCount++;
                } else {
                    updatedCount++;
                }

                if (labScoreService != null && !colMap.labCols.isEmpty() && student.getId() != null) {
                    for (Map.Entry<Integer, Integer> labEntry : colMap.labCols.entrySet()) {
                        int labNum = labEntry.getKey();
                        String rawLab = getCell(row, labEntry.getValue());
                        Double val = parseDoubleOrNull(rawLab);
                        if (val != null) {
                            LabScoreService.LabSpec spec = LabScoreService.getLabSpec(labNum);
                            double clamped = Math.max(0.0, Math.min(val, spec.maxTotal()));
                            boolean accepted = clamped >= 0.40 * spec.maxTotal();
                            labScoreService.recordLab(student.getId(), labNum, clamped, accepted);
                            labsImported++;
                        }
                    }
                }
            }
        } finally {
            if (labScoreService != null) {
                labScoreService.setSuppressBroadcast(false);
                if (importedCount > 0 || updatedCount > 0 || labsImported > 0 || ktImported > 0) {
                    labScoreService.broadcastAfterBulkUpdate();
                }
            }
        }

        return new ImportResultDto(importedCount, updatedCount, labsImported, ktImported, skippedRows);
    }

    private Optional<Student> findExistingStudent(
            String studentCode,
            String lastName,
            String firstName,
            String middleName,
            UUID groupId
    ) {
        if (studentCode != null && !studentCode.isEmpty()) {
            Optional<Student> byCode = studentRepository.findByStudentCode(studentCode);
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        List<Student> candidates = (groupId != null)
                ? studentRepository.findByAcademicGroupId(groupId)
                : studentRepository.findAll();
        if (candidates == null || candidates.isEmpty()) {
            candidates = studentRepository.findAll();
        }
        if (candidates == null) {
            return Optional.empty();
        }
        for (Student s : candidates) {
            if (equalsIgnoreCaseTrim(s.getLastName(), lastName)
                    && equalsIgnoreCaseTrim(s.getFirstName(), firstName)
                    && equalsIgnoreCaseTrim(s.getMiddleName() != null ? s.getMiddleName() : "", middleName != null ? middleName : "")) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    private boolean equalsIgnoreCaseTrim(String a, String b) {
        String sa = a != null ? a.trim() : "";
        String sb = b != null ? b.trim() : "";
        return sa.equalsIgnoreCase(sb);
    }

    private AcademicGroup resolveGroup(String groupName, String flowName, AcademicGroup defaultGroup) {
        if (groupName != null && !groupName.isEmpty()) {
            String effectiveFlowName = (flowName != null && !flowName.isEmpty()) ? flowName : "Основной поток";
            AcademicFlow flow = flowRepository.findByName(effectiveFlowName)
                    .orElseGet(() -> flowRepository.save(
                            AcademicFlow.builder()
                                    .name(effectiveFlowName)
                                    .academicYear("2025/2026")
                                    .build()
                    ));
            return groupRepository.findByName(groupName)
                    .orElseGet(() -> groupRepository.save(
                            AcademicGroup.builder()
                                    .name(groupName)
                                    .academicFlow(flow)
                                    .build()
                    ));
        }
        if (defaultGroup != null) {
            return defaultGroup;
        }
        AcademicFlow fallbackFlow = flowRepository.findByName("Основной поток")
                .orElseGet(() -> flowRepository.save(
                        AcademicFlow.builder()
                                .name("Основной поток")
                                .academicYear("2025/2026")
                                .build()
                ));
        return groupRepository.findByName("Общая группа")
                .orElseGet(() -> groupRepository.save(
                        AcademicGroup.builder()
                                .name("Общая группа")
                                .academicFlow(fallbackFlow)
                                .build()
                ));
    }

    private String generateStudentCode(String groupName, int rowNum) {
        String prefix = (groupName != null && !groupName.isBlank())
                ? groupName.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT)
                : "STU";
        String candidate = prefix + "-" + rowNum + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        return candidate;
    }

    private int findHeaderRowIndex(List<List<String>> rows) {
        int limit = Math.min(rows.size(), 10);
        for (int i = 0; i < limit; i++) {
            List<String> row = rows.get(i);
            for (String cell : row) {
                String norm = normalizeHeader(cell);
                if (norm.equals("фио студента") || norm.equals("фио") || norm.equals("фамилия")
                        || norm.equals("lastname") || norm.equals("last_name")
                        || norm.equals("studentcode") || norm.equals("student_code")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private ColumnMap mapHeaderRow(List<String> headerRow) {
        ColumnMap map = new ColumnMap();
        for (int c = 0; c < headerRow.size(); c++) {
            String raw = clean(headerRow.get(c));
            String norm = normalizeHeader(raw);
            if (norm.isEmpty()) {
                continue;
            }
            // Strictly ignore row index headers ("№ п/п", "п/п", "№") so they never collide with "пара"
            if (norm.equals("№ п/п") || norm.equals("п/п") || norm.equals("№") || norm.equals("n п/п") || norm.equals("номер")) {
                map.rowNumberCol = c;
                continue;
            }
            if (norm.equals("пара") || norm.equals("pair") || norm.equals("подгруппа") || norm.equals("pairnumber") || norm.equals("pair_number")) {
                map.pairCol = c;
                continue;
            }
            if (norm.equals("фио студента") || norm.equals("фио") || norm.equals("студент") || norm.equals("ф.и.о.")
                    || norm.equals("fullname") || norm.equals("full_name")) {
                map.fioCol = c;
                continue;
            }
            if (norm.equals("фамилия") || norm.equals("lastname") || norm.equals("last_name")) {
                map.lastNameCol = c;
                continue;
            }
            if (norm.equals("имя") || norm.equals("firstname") || norm.equals("first_name")) {
                map.firstNameCol = c;
                continue;
            }
            if (norm.equals("отчество") || norm.equals("middlename") || norm.equals("middle_name")) {
                map.middleNameCol = c;
                continue;
            }
            if (norm.equals("studentcode") || norm.equals("student_code") || norm.equals("код") || norm.equals("шифр") || norm.equals("зачетка")) {
                map.studentCodeCol = c;
                continue;
            }
            if (norm.equals("группа") || norm.equals("group") || norm.equals("groupname") || norm.equals("group_name")) {
                map.groupNameCol = c;
                continue;
            }
            if (norm.equals("поток") || norm.equals("flow") || norm.equals("flowname") || norm.equals("flow_name") || norm.equals("stream")) {
                map.flowNameCol = c;
                continue;
            }
            if (norm.equals("токен") || norm.equals("token") || norm.equals("token_info")) {
                map.tokenCol = c;
                continue;
            }
            Matcher labMatcher = LAB_HEADER_PATTERN.matcher(norm);
            if (labMatcher.matches()) {
                int labNum = Integer.parseInt(labMatcher.group(1));
                map.labCols.put(labNum, c);
                continue;
            }
            Matcher ktMatcher = KT_HEADER_PATTERN.matcher(norm);
            if (ktMatcher.matches()) {
                int ktNum = Integer.parseInt(ktMatcher.group(1));
                map.ktCols.put(ktNum, c);
            }
        }
        return map;
    }

    private boolean isEmptyOrSummaryRow(List<String> row, ColumnMap colMap) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        boolean allBlank = true;
        for (int i = 0; i < row.size(); i++) {
            String cell = clean(row.get(i));
            if (!cell.isEmpty()) {
                allBlank = false;
            }
            if (i <= 3 && isSummaryMarker(cell)) {
                return true;
            }
        }
        if (allBlank) {
            return true;
        }
        if (colMap.fioCol >= 0 && isSummaryMarker(getCell(row, colMap.fioCol))) {
            return true;
        }
        if (colMap.lastNameCol >= 0 && isSummaryMarker(getCell(row, colMap.lastNameCol))) {
            return true;
        }
        return false;
    }

    private boolean isSummaryMarker(String val) {
        if (val == null) return false;
        String norm = val.trim().toLowerCase(Locale.ROOT);
        return SUMMARY_MARKERS.contains(norm);
    }

    private String normalizeHeader(String value) {
        if (value == null) return "";
        return clean(value).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String getCell(List<String> row, int colIdx) {
        if (colIdx < 0 || row == null || colIdx >= row.size()) {
            return "";
        }
        return clean(row.get(colIdx));
    }

    private Integer parsePositiveInt(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            double d = Double.parseDouble(val.trim().replace(',', '.'));
            int n = (int) Math.round(d);
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDoubleOrNull(String val) {
        if (val == null || val.isBlank()) return null;
        String trimmed = val.trim().replace(',', '.');
        if (trimmed.equals("-")) return null;
        try {
            double d = Double.parseDouble(trimmed);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replace("\uFEFF", "").trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static class ColumnMap {
        int rowNumberCol = -1;
        int pairCol = -1;
        int fioCol = -1;
        int lastNameCol = -1;
        int firstNameCol = -1;
        int middleNameCol = -1;
        int studentCodeCol = -1;
        int groupNameCol = -1;
        int flowNameCol = -1;
        int tokenCol = -1;
        Map<Integer, Integer> labCols = new HashMap<>();
        Map<Integer, Integer> ktCols = new HashMap<>();

        static ColumnMap legacyPositional() {
            ColumnMap m = new ColumnMap();
            m.lastNameCol = 0;
            m.firstNameCol = 1;
            m.middleNameCol = 2;
            m.studentCodeCol = 3;
            m.groupNameCol = 4;
            m.flowNameCol = 5;
            return m;
        }
    }
}
