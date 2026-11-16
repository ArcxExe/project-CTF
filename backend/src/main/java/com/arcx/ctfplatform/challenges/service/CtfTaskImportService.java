package com.arcx.ctfplatform.challenges.service;

import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.entity.TaskType;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.util.SimpleXlsxUtil;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CtfTaskImportService {

    public static final List<String> TEMPLATE_HEADERS = List.of(
            "task_id",
            "title",
            "description",
            "category",
            "difficulty",
            "points",
            "first_blood_only",
            "flag",
            "bank_id",
            "pool",
            "wave",
            "artifact_url",
            "solution_internal"
    );

    private static final Set<String> SUMMARY_SECTION_PREFIXES = Set.of(
            "ctf — план",
            "ctf - план",
            "план наполнения",
            "шаблон импорта ctf",
            "счётчик заполненных",
            "счетчик заполненных",
            "план рассчитан",
            "категория (импорт)",
            "итого",
            "показатели"
    );

    private final CtfTaskRepository ctfTaskRepository;
    private final CompetitionRepository competitionRepository;

    public record TaskImportResultDto(
            boolean dryRun,
            int createdCount,
            int updatedCount,
            int mainPoolCount,
            int reservePoolCount,
            int totalPoints,
            int skippedRows,
            int linkedToCompetitionCount,
            List<String> errors,
            List<String> warnings
    ) {}

    @Transactional
    public TaskImportResultDto importTasks(
            InputStream inputStream,
            String filename,
            boolean dryRun,
            UUID competitionId
    ) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            List<List<String>> rows;
            if (SimpleXlsxUtil.isXlsxFile(filename, bytes)) {
                List<List<List<String>>> sheets = SimpleXlsxUtil.readAllSheets(new ByteArrayInputStream(bytes));
                rows = selectTaskSheet(sheets);
            } else {
                rows = parseCsvRows(new String(bytes, StandardCharsets.UTF_8));
            }
            return processRows(rows, dryRun, competitionId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось разобрать файл импорта заданий: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportTemplateXlsx() {
        List<List<Object>> rows = List.of(
                List.of(
                        "LIN-001",
                        "Права аудитора",
                        "Файл /srv/audit.log имеет права -rw-r----- и принадлежит root:auditors. Пользователь nina входит в auditors и не является владельцем. Введите доступ: read_only, write_only, full или none.",
                        "Linux",
                        "Легко",
                        3,
                        "TRUE",
                        "CTF{read_only}",
                        "GLOBAL",
                        "MAIN",
                        1,
                        "",
                        "Группа auditors имеет право чтения и не имеет права записи."
                ),
                List.of(
                        "IR-013",
                        "Задание 13. «Утечка, которой не было»",
                        "Артефакт 1. Политика доступа\nAccount: analyst\nAllowed sources: 198.51.100.0/24\n\nАртефакт 2. Журнал шлюза (UTC)\n11:04:07 user=analyst src=203.0.113.24 result=200 session=S72\n\nАртефакт 3. Журнал приложения (UTC+3)\n14:04:12 session=S72 action=POST /export job=J9\n\nАртефакт 4. База данных и сетевой шлюз (UTC)\n11:04:17 job=J9 export=payroll_q4.csv rows=1842\n11:04:27 job=J9 dst=198.51.100.88 result=201\n\nТребуется определить: источник неразрешённой успешной аутентификации, экспортированный файл (без .csv) и внешний адрес назначения, объединив через _ (в IP точки заменить на _).",
                        "Forensics",
                        "Сложно",
                        18,
                        "TRUE",
                        "CTF{203_0_113_24_payroll_q4_198_51_100_88}",
                        "GLOBAL",
                        "MAIN",
                        1,
                        "",
                        "session=S72 с 203.0.113.24 -> job=J9 -> payroll_q4.csv -> dst=198.51.100.88 (201)."
                )
        );
        return SimpleXlsxUtil.writeSheet("Задания", TEMPLATE_HEADERS, rows);
    }

    @Transactional(readOnly = true)
    public byte[] exportTasksXlsx() {
        List<CtfTask> tasks = ctfTaskRepository.findAll();
        List<List<Object>> rows = new ArrayList<>(tasks.size());
        for (CtfTask task : tasks) {
            String artifactUrl = "";
            if (task.getAttachments() != null) {
                Object files = task.getAttachments().get("files");
                if (files instanceof List<?> list && !list.isEmpty()) {
                    artifactUrl = list.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("; "));
                }
            }
            rows.add(List.of(
                    task.getTaskCode() != null ? task.getTaskCode() : "",
                    task.getTitle() != null ? task.getTitle() : "",
                    task.getDescription() != null ? task.getDescription() : "",
                    task.getCategory() != null ? task.getCategory() : "",
                    task.getDifficulty() != null ? task.getDifficulty() : "",
                    task.getBaseScore() != null ? task.getBaseScore() : 0,
                    task.isFirstBloodOnly() ? "TRUE" : "FALSE",
                    task.getFlag() != null ? task.getFlag() : "",
                    task.getBankId() != null ? task.getBankId() : "GLOBAL",
                    task.getPool() != null ? task.getPool() : "MAIN",
                    task.getWave() != null ? task.getWave() : "",
                    artifactUrl,
                    task.getSolutionInternal() != null ? task.getSolutionInternal() : ""
            ));
        }
        return SimpleXlsxUtil.writeSheet("Задания", TEMPLATE_HEADERS, rows);
    }

    private List<List<String>> selectTaskSheet(List<List<List<String>>> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            return List.of();
        }
        List<List<String>> bestSheet = sheets.get(0);
        int bestScore = -1;
        for (List<List<String>> sheet : sheets) {
            int headerIdx = findHeaderRowIndex(sheet);
            if (headerIdx >= 0) {
                ColumnMap map = mapHeaderRow(sheet.get(headerIdx));
                int score = map.matchedColumnsCount();
                if (score > bestScore) {
                    bestScore = score;
                    bestSheet = sheet;
                }
            }
        }
        return bestSheet;
    }

    private TaskImportResultDto processRows(List<List<String>> rows, boolean dryRun, UUID competitionId) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (rows == null || rows.isEmpty()) {
            errors.add("Файл пуст или не содержит данных.");
            return new TaskImportResultDto(dryRun, 0, 0, 0, 0, 0, 0, 0, errors, warnings);
        }

        int headerRowIdx = findHeaderRowIndex(rows);
        if (headerRowIdx < 0) {
            errors.add("Не найдена строка заголовков (ожидаются столбцы title, flag, points и т.д.).");
            return new TaskImportResultDto(dryRun, 0, 0, 0, 0, 0, rows.size(), 0, errors, warnings);
        }

        ColumnMap colMap = mapHeaderRow(rows.get(headerRowIdx));
        if (colMap.titleCol < 0 || colMap.flagCol < 0 || colMap.pointsCol < 0) {
            errors.add("В шапке таблицы отсутствуют обязательные столбцы (title, points, flag).");
            return new TaskImportResultDto(dryRun, 0, 0, 0, 0, 0, 0, 0, errors, warnings);
        }

        Competition competition = null;
        if (competitionId != null) {
            competition = competitionRepository.findById(competitionId)
                    .orElseThrow(() -> new IllegalArgumentException("Соревнование не найдено: " + competitionId));
        }

        List<CtfTask> existingTasks = ctfTaskRepository.findAll();
        Map<String, CtfTask> existingByCode = new HashMap<>();
        Map<String, CtfTask> existingByTitle = new HashMap<>();
        for (CtfTask t : existingTasks) {
            if (t.getTaskCode() != null && !t.getTaskCode().isBlank()) {
                existingByCode.put(t.getTaskCode().trim().toLowerCase(Locale.ROOT), t);
            }
            if (t.getTitle() != null && !t.getTitle().isBlank()) {
                existingByTitle.put(t.getTitle().trim().toLowerCase(Locale.ROOT), t);
            }
        }

        Set<String> seenTaskCodes = new HashSet<>();
        Map<String, String> seenFlags = new HashMap<>();

        int createdCount = 0;
        int updatedCount = 0;
        int mainPoolCount = 0;
        int reservePoolCount = 0;
        int totalPoints = 0;
        int skippedRows = 0;

        List<CtfTask> tasksToSave = new ArrayList<>();
        List<CtfTask> mainPoolTasks = new ArrayList<>();

        for (int r = headerRowIdx + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int excelRowNum = r + 1;

            if (isAllBlank(row)) {
                skippedRows++;
                continue;
            }
            if (isSummarySectionRow(row)) {
                skippedRows += (rows.size() - r);
                break;
            }

            String taskCode = normalizeOptional(getCell(row, colMap.taskIdCol));
            String title = trimCell(getCell(row, colMap.titleCol));
            String description = trimOuterPreserveLines(getCell(row, colMap.descriptionCol));
            String category = trimCell(getCell(row, colMap.categoryCol));
            String rawDifficulty = trimCell(getCell(row, colMap.difficultyCol));
            String rawPoints = trimCell(getCell(row, colMap.pointsCol));
            String rawFirstBlood = trimCell(getCell(row, colMap.firstBloodOnlyCol));
            String flag = trimCell(getCell(row, colMap.flagCol));
            String bankId = normalizeBankId(getCell(row, colMap.bankIdCol));
            String pool = normalizePool(getCell(row, colMap.poolCol));
            String rawWave = trimCell(getCell(row, colMap.waveCol));
            String artifactUrl = trimCell(getCell(row, colMap.artifactUrlCol));
            String solutionInternal = trimOuterPreserveLines(getCell(row, colMap.solutionInternalCol));

            if (title.isEmpty() && flag.isEmpty() && rawPoints.isEmpty() && taskCode == null) {
                skippedRows++;
                continue;
            }

            boolean rowHasError = false;
            if (taskCode != null) {
                if (taskCode.length() > 64) {
                    errors.add("Строка " + excelRowNum + ": task_id превышает 64 символа.");
                    rowHasError = true;
                } else {
                    String codeKey = taskCode.toLowerCase(Locale.ROOT);
                    if (!seenTaskCodes.add(codeKey)) {
                        errors.add("Строка " + excelRowNum + ": дубликат task_id '" + taskCode + "' внутри файла.");
                        rowHasError = true;
                    }
                }
            }

            if (title.isEmpty()) {
                errors.add("Строка " + excelRowNum + ": отсутствует название задания (title).");
                rowHasError = true;
            } else if (title.length() > 200) {
                errors.add("Строка " + excelRowNum + ": название (title) превышает 200 символов.");
                rowHasError = true;
            }

            Integer points = parsePositiveInt(rawPoints);
            if (points == null || points < 1) {
                errors.add("Строка " + excelRowNum + ": некорректное количество баллов (points='" + rawPoints + "').");
                rowHasError = true;
            }

            if (flag.isEmpty()) {
                errors.add("Строка " + excelRowNum + ": отсутствует флаг (flag).");
                rowHasError = true;
            } else {
                String refLabel = taskCode != null ? taskCode : title;
                if (!flag.startsWith("CTF{") || !flag.endsWith("}")) {
                    warnings.add("Строка " + excelRowNum + " (" + refLabel + "): флаг '" + flag + "' не соответствует формату CTF{...}.");
                }
                String prevRef = seenFlags.putIfAbsent(flag, "строка " + excelRowNum + " (" + refLabel + ")");
                if (prevRef != null) {
                    warnings.add("Строка " + excelRowNum + " (" + refLabel + "): дублирующийся флаг '" + flag + "' (уже в " + prevRef + ").");
                }
            }

            if (!"MAIN".equals(pool) && !"RESERVE".equals(pool)) {
                warnings.add("Строка " + excelRowNum + ": нестандартное значение pool='" + pool + "' (ожидается MAIN или RESERVE).");
            }

            Integer wave = null;
            if (!rawWave.isEmpty()) {
                wave = parsePositiveInt(rawWave);
                if (wave == null) {
                    warnings.add("Строка " + excelRowNum + ": некорректный номер волны wave='" + rawWave + "', пропущено.");
                }
            }

            if (category.length() > 100) {
                warnings.add("Строка " + excelRowNum + ": category обрезана до 100 символов.");
                category = category.substring(0, 100);
            }

            if (rowHasError) {
                continue;
            }

            CtfTask target = null;
            if (taskCode != null) {
                target = existingByCode.get(taskCode.toLowerCase(Locale.ROOT));
            } else if (!title.isEmpty()) {
                target = existingByTitle.get(title.toLowerCase(Locale.ROOT));
            }

            boolean isNew = (target == null);
            if (isNew) {
                target = new CtfTask();
                createdCount++;
            } else {
                updatedCount++;
            }

            target.setTaskCode(taskCode);
            target.setTitle(title);
            target.setDescription(description);
            target.setCategory(category.isEmpty() ? null : category);
            target.setDifficulty(normalizeDifficulty(rawDifficulty));
            target.setBaseScore(points);
            target.setMaxScore(points);
            target.setFirstBloodOnly(parseBoolean(rawFirstBlood, false));
            target.setTaskType(TaskType.FLAG);
            target.setFlag(flag);
            target.setBankId(bankId);
            target.setPool(pool);
            target.setWave(wave);
            target.setSolutionInternal(solutionInternal.isEmpty() ? null : solutionInternal);

            if (!artifactUrl.isEmpty()) {
                List<String> urls = new ArrayList<>();
                for (String part : artifactUrl.split("[;\\n]+")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        urls.add(trimmed);
                    }
                }
                Map<String, Object> att = new HashMap<>();
                att.put("files", urls);
                target.setAttachments(att);
            }

            tasksToSave.add(target);
            if ("RESERVE".equalsIgnoreCase(pool)) {
                reservePoolCount++;
            } else {
                mainPoolCount++;
                mainPoolTasks.add(target);
            }
            totalPoints += points;
        }

        int linkedCount = 0;
        if (!dryRun && errors.isEmpty() && !tasksToSave.isEmpty()) {
            List<CtfTask> saved = ctfTaskRepository.saveAll(tasksToSave);
            if (competition != null) {
                Set<CtfTask> compTasks = competition.getTasks() != null
                        ? new LinkedHashSet<>(competition.getTasks())
                        : new LinkedHashSet<>();
                for (CtfTask t : saved) {
                    if (!"RESERVE".equalsIgnoreCase(t.getPool())) {
                        if (compTasks.add(t)) {
                            linkedCount++;
                        }
                    }
                }
                competition.setTasks(compTasks);
                competitionRepository.save(competition);
            }
        } else if (dryRun && competition != null) {
            Set<UUID> existingLinkedIds = new HashSet<>();
            if (competition.getTasks() != null) {
                competition.getTasks().forEach(t -> {
                    if (t != null && t.getId() != null) {
                        existingLinkedIds.add(t.getId());
                    }
                });
            }
            for (CtfTask t : mainPoolTasks) {
                if (t.getId() == null || !existingLinkedIds.contains(t.getId())) {
                    linkedCount++;
                }
            }
        }

        return new TaskImportResultDto(
                dryRun,
                createdCount,
                updatedCount,
                mainPoolCount,
                reservePoolCount,
                totalPoints,
                skippedRows,
                linkedCount,
                errors,
                warnings
        );
    }

    private List<List<String>> parseCsvRows(String rawContent) {
        String content = rawContent;
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        char delimiter = detectDelimiter(content);
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    currentCell.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == delimiter && !inQuotes) {
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !inQuotes) {
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
                if (!isAllBlank(currentRow)) {
                    rows.add(currentRow);
                }
                currentRow = new ArrayList<>();
            } else {
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    currentCell.append('\n');
                    i++;
                } else if (ch == '\r') {
                    currentCell.append('\n');
                } else {
                    currentCell.append(ch);
                }
            }
        }

        if (currentCell.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentCell.toString());
            if (!isAllBlank(currentRow)) {
                rows.add(currentRow);
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

    private int findHeaderRowIndex(List<List<String>> rows) {
        int limit = Math.min(rows.size(), 15);
        for (int i = 0; i < limit; i++) {
            ColumnMap map = mapHeaderRow(rows.get(i));
            if (map.titleCol >= 0 && (map.flagCol >= 0 || map.pointsCol >= 0)) {
                return i;
            }
        }
        return -1;
    }

    private ColumnMap mapHeaderRow(List<String> headerRow) {
        ColumnMap map = new ColumnMap();
        for (int c = 0; c < headerRow.size(); c++) {
            String norm = normalizeHeader(headerRow.get(c));
            if (norm.isEmpty()) {
                continue;
            }
            switch (norm) {
                case "task_id", "taskid", "task_code", "taskcode", "id", "код", "код задания", "шифр" ->
                        map.taskIdCol = c;
                case "title", "название", "заголовок", "имя задания" ->
                        map.titleCol = c;
                case "description", "описание", "условие", "текст" ->
                        map.descriptionCol = c;
                case "category", "категория", "тема" ->
                        map.categoryCol = c;
                case "difficulty", "сложность", "уровень" ->
                        map.difficultyCol = c;
                case "points", "base_score", "basescore", "score", "баллы", "балл" ->
                        map.pointsCol = c;
                case "first_blood_only", "firstbloodonly", "first_blood", "firstblood", "первый победитель" ->
                        map.firstBloodOnlyCol = c;
                case "flag", "флаг", "ответ" ->
                        map.flagCol = c;
                case "bank_id", "bankid", "bank", "банк" ->
                        map.bankIdCol = c;
                case "pool", "пул" ->
                        map.poolCol = c;
                case "wave", "волна" ->
                        map.waveCol = c;
                case "artifact_url", "artifacturl", "artifact", "attachment", "attachments", "артефакт", "вложение", "ссылка" ->
                        map.artifactUrlCol = c;
                case "solution_internal", "solutioninternal", "solution", "решение", "разбор" ->
                        map.solutionInternalCol = c;
                default -> {
                    // ignore unknown columns
                }
            }
        }
        return map;
    }

    private boolean isSummarySectionRow(List<String> row) {
        String c0 = trimCell(getCell(row, 0)).toLowerCase(Locale.ROOT);
        String c1 = trimCell(getCell(row, 1)).toLowerCase(Locale.ROOT);
        if ("категория".equals(c0) && (c1.contains("балл") || "значение".equals(c1))) {
            return true;
        }
        for (int i = 0; i < Math.min(row.size(), 3); i++) {
            String norm = trimCell(row.get(i)).toLowerCase(Locale.ROOT);
            if (norm.isEmpty()) {
                continue;
            }
            for (String prefix : SUMMARY_SECTION_PREFIXES) {
                if (norm.equals(prefix) || norm.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAllBlank(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String getCell(List<String> row, int colIdx) {
        if (colIdx < 0 || row == null || colIdx >= row.size() || row.get(colIdx) == null) {
            return "";
        }
        return row.get(colIdx);
    }

    private String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\uFEFF", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String trimCell(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").trim();
    }

    private String trimOuterPreserveLines(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeBankId(String value) {
        String trimmed = trimCell(value);
        return trimmed.isEmpty() ? "GLOBAL" : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizePool(String value) {
        String trimmed = trimCell(value);
        return trimmed.isEmpty() ? "MAIN" : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return "easy";
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        if (norm.contains("легк") || norm.startsWith("easy")) {
            return "easy";
        }
        if (norm.contains("средн") || norm.startsWith("medium")) {
            return "medium";
        }
        if (norm.contains("слож") || norm.startsWith("hard") || norm.equals("boss")) {
            return "hard";
        }
        return raw.trim();
    }

    private Integer parsePositiveInt(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            double d = Double.parseDouble(val.trim().replace(',', '.'));
            if (!Double.isFinite(d)) {
                return null;
            }
            int n = (int) Math.round(d);
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(norm) || "1".equals(norm) || "yes".equals(norm) || "да".equals(norm) || "истина".equals(norm) || "y".equals(norm)) {
            return true;
        }
        if ("false".equals(norm) || "0".equals(norm) || "no".equals(norm) || "нет".equals(norm) || "ложь".equals(norm) || "n".equals(norm)) {
            return false;
        }
        return defaultValue;
    }

    private static class ColumnMap {
        int taskIdCol = -1;
        int titleCol = -1;
        int descriptionCol = -1;
        int categoryCol = -1;
        int difficultyCol = -1;
        int pointsCol = -1;
        int firstBloodOnlyCol = -1;
        int flagCol = -1;
        int bankIdCol = -1;
        int poolCol = -1;
        int waveCol = -1;
        int artifactUrlCol = -1;
        int solutionInternalCol = -1;

        int matchedColumnsCount() {
            int count = 0;
            if (taskIdCol >= 0) count++;
            if (titleCol >= 0) count++;
            if (descriptionCol >= 0) count++;
            if (categoryCol >= 0) count++;
            if (difficultyCol >= 0) count++;
            if (pointsCol >= 0) count++;
            if (firstBloodOnlyCol >= 0) count++;
            if (flagCol >= 0) count++;
            if (bankIdCol >= 0) count++;
            if (poolCol >= 0) count++;
            if (waveCol >= 0) count++;
            if (artifactUrlCol >= 0) count++;
            if (solutionInternalCol >= 0) count++;
            return count;
        }
    }
}
