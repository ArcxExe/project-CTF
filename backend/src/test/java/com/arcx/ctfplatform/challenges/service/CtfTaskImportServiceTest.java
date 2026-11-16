package com.arcx.ctfplatform.challenges.service;

import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.util.SimpleXlsxUtil;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CtfTaskImportServiceTest {

    @Mock
    private CtfTaskRepository ctfTaskRepository;
    @Mock
    private CompetitionRepository competitionRepository;

    private CtfTaskImportService importService;

    @BeforeEach
    void setUp() {
        importService = new CtfTaskImportService(ctfTaskRepository, competitionRepository);
    }

    @Test
    @DisplayName("Imports template CSV with 8 tasks and ignores summary/plan tables below")
    void testImportTemplateCsvWithSummaryFooter() {
        when(ctfTaskRepository.findAll()).thenReturn(List.of());
        when(ctfTaskRepository.saveAll(any())).thenAnswer(inv -> {
            List<CtfTask> list = inv.getArgument(0);
            list.forEach(t -> {
                if (t.getId() == null) t.setId(UUID.randomUUID());
            });
            return list;
        });

        String csv = """
                task_id,title,description,category,difficulty,points,first_blood_only,flag,bank_id,pool,wave,artifact_url,solution_internal
                LIN-001,Права аудитора,"Файл /srv/audit.log имеет права -rw-r----- и принадлежит root:auditors. Пользователь nina входит в auditors и не является владельцем. Введите доступ: read_only, write_only, full или none.",Linux,Легко,3,True,CTF{read_only},GLOBAL,MAIN,1,,Группа auditors имеет право чтения и не имеет права записи.
                REC-001,Незащищённый сервис,"В перечне разрешённых портов для web-01 указаны TCP 22 и 443. В учебном скане найдены 22/open, 443/open, 8443/open. Введите номер незапланированного открытого порта.",Recon,Легко,3,True,CTF{8443},GLOBAL,MAIN,1,,8443 отсутствует в списке разрешённых портов.
                AUT-001,Механизм входа,Строка журнала: Accepted publickey for deploy from 192.0.2.15 port 42424 ssh2. Каким способом успешно аутентифицировался пользователь? Укажите одно слово из журнала.,Auth,Легко,1,True,CTF{publickey},GLOBAL,MAIN,1,,Метод входа указан сразу после Accepted.
                NET-001,Источник ARP-подмены,"В сетевом дампе шлюз 10.0.0.1 объявляется с MAC 66:66:66:66:66:66. Таблица DHCP связывает этот MAC с 10.0.0.77. Укажите IPv4 подозрительного узла, заменив точки символами _.",Network,Средне,6,True,CTF{10_0_0_77},GLOBAL,MAIN,1,,DHCP запись связывает поддельный MAC с IP 10.0.0.77.
                WEB-001,Cookie под защитой,Сервер прислал заголовок Set-Cookie: sid=demo; Secure; SameSite=Lax. Какой атрибут отсутствует для ограничения доступа к cookie со стороны JavaScript? Ответ строчными буквами.,Web,Легко,3,True,CTF{httponly},GLOBAL,MAIN,1,,HttpOnly препятствует чтению cookie из JavaScript.
                CRY-001,Цепочка доверия,"Сервер отправляет только сертификат leaf.pem, выпущенный учебным Intermediate CA. Клиент доверяет Root CA, но промежуточного сертификата у него нет. Какой сертификат должен передать сервер? Ответ: intermediate или root.",Crypto,Средне,6,True,CTF{intermediate},GLOBAL,MAIN,1,,Сервер должен передать недостающий промежуточный сертификат.
                CON-001,Секрет при сборке,"Dockerfile использует COPY . /app, а в каталоге проекта лежит файл .env, который не исключён в .dockerignore. Укажите имя файла, которое рискует попасть в образ, в формате dot_имя.",Containers,Легко,3,True,CTF{dot_env},GLOBAL,MAIN,1,,Файл .env будет доступен в контексте копирования.
                IR-001,Хронология событий,Логи: 12:00:04 — успешный вход пользователя; 12:00:25 — создание новой привилегированной учётной записи; 12:01:03 — начало архивирования. Укажите время изменения привилегий в формате HHMMSS.,Forensics,Сложно,10,True,CTF{120025},GLOBAL,MAIN,1,,Нужное событие — создание учётной записи в 12:00:25.
                CTF — план наполнения банка,,,,,,,,
                Категория,1 балл,3 балла,6 баллов,10 баллов,18 баллов,Основных / группу,Резерв / группу,Основных / 3 группы
                Linux,12,11,11,5,1,40,4,120
                ИТОГО,70,65,95,55,15,300,30,900
                Шаблон импорта CTF — технические договорённости,
                """;

        CtfTaskImportService.TaskImportResultDto result = importService.importTasks(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "tasks.csv",
                false,
                null
        );

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.createdCount()).isEqualTo(8);
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.mainPoolCount()).isEqualTo(8);
        assertThat(result.reservePoolCount()).isEqualTo(0);
        assertThat(result.totalPoints()).isEqualTo(35);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CtfTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(ctfTaskRepository).saveAll(captor.capture());
        List<CtfTask> saved = captor.getValue();
        assertThat(saved).hasSize(8);

        CtfTask lin001 = saved.get(0);
        assertThat(lin001.getTaskCode()).isEqualTo("LIN-001");
        assertThat(lin001.getDifficulty()).isEqualTo("easy");
        assertThat(lin001.getBaseScore()).isEqualTo(3);
        assertThat(lin001.isFirstBloodOnly()).isTrue();
        assertThat(lin001.getFlag()).isEqualTo("CTF{read_only}");
        assertThat(lin001.getBankId()).isEqualTo("GLOBAL");
        assertThat(lin001.getPool()).isEqualTo("MAIN");
        assertThat(lin001.getWave()).isEqualTo(1);
        assertThat(lin001.getSolutionInternal()).isEqualTo("Группа auditors имеет право чтения и не имеет права записи.");
    }

    @Test
    @DisplayName("Imports multi-line forensics Задание 13 from XLSX and preserves all artifacts and newlines")
    void testImportMultiLineForensicsTaskFromXlsx() {
        when(ctfTaskRepository.findAll()).thenReturn(List.of());
        when(ctfTaskRepository.saveAll(any())).thenAnswer(inv -> {
            List<CtfTask> list = inv.getArgument(0);
            list.forEach(t -> {
                if (t.getId() == null) t.setId(UUID.randomUUID());
            });
            return list;
        });

        String multiLineDescription = """
                Служба информационной безопасности расследует возможную утечку данных с корпоративного аналитического сервера.
                Имеются четыре независимых журнала и действующие правила доступа.
                Артефакт 1. Политика доступа
                Account: analyst
                Allowed sources: 198.51.100.0/24

                Артефакт 2. Журнал шлюза (UTC)
                11:04:07
                user=analyst
                src=203.0.113.24
                result=200
                session=S72

                Артефакт 3. Журнал приложения (UTC+3)
                14:04:12
                session=S72
                action=POST /export
                job=J9

                Артефакт 4. База данных и сетевой шлюз (UTC)
                DATABASE
                11:04:17
                job=J9
                export=payroll_q4.csv
                rows=1842

                EGRESS GATEWAY
                11:04:27
                job=J9
                dst=198.51.100.88
                result=201
                """.trim();

        byte[] xlsxBytes = SimpleXlsxUtil.writeSheet(
                "Задания",
                CtfTaskImportService.TEMPLATE_HEADERS,
                List.of(List.of(
                        "IR-013",
                        "Задание 13. «Утечка, которой не было»",
                        multiLineDescription,
                        "Forensics",
                        "Сложно",
                        18,
                        "TRUE",
                        "CTF{203_0_113_24_payroll_q4_198_51_100_88}",
                        "GLOBAL",
                        "MAIN",
                        1,
                        "https://example.org/ir-013.zip",
                        "session=S72 -> job=J9 -> payroll_q4.csv -> dst=198.51.100.88"
                ))
        );

        CtfTaskImportService.TaskImportResultDto result = importService.importTasks(
                new ByteArrayInputStream(xlsxBytes),
                "forensics_bank.xlsx",
                false,
                null
        );

        assertThat(result.errors()).isEmpty();
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.totalPoints()).isEqualTo(18);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CtfTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(ctfTaskRepository).saveAll(captor.capture());
        CtfTask saved = captor.getValue().get(0);

        assertThat(saved.getTaskCode()).isEqualTo("IR-013");
        assertThat(saved.getDifficulty()).isEqualTo("hard");
        assertThat(saved.getBaseScore()).isEqualTo(18);
        assertThat(saved.getFlag()).isEqualTo("CTF{203_0_113_24_payroll_q4_198_51_100_88}");
        assertThat(saved.getDescription()).contains("Артефакт 1. Политика доступа");
        assertThat(saved.getDescription()).contains("Артефакт 4. База данных и сетевой шлюз (UTC)");
        assertThat(saved.getDescription()).contains("\n\n");
        assertThat(saved.getAttachments().get("files")).isEqualTo(List.of("https://example.org/ir-013.zip"));
    }

    @Test
    @DisplayName("Idempotently updates existing task by task_id and respects dryRun & MAIN/RESERVE pool linking")
    void testUpsertDryRunAndCompetitionLinking() {
        UUID existingId = UUID.randomUUID();
        CtfTask existing = new CtfTask();
        existing.setId(existingId);
        existing.setTaskCode("LIN-001");
        existing.setTitle("Old Title");
        existing.setBaseScore(1);
        existing.setFlag("CTF{old}");

        UUID compId = UUID.randomUUID();
        Competition competition = Competition.builder()
                .id(compId)
                .title("Spring CTF")
                .tasks(new LinkedHashSet<>())
                .build();

        when(ctfTaskRepository.findAll()).thenReturn(List.of(existing));
        when(competitionRepository.findById(compId)).thenReturn(Optional.of(competition));

        String csv = """
                task_id,title,description,category,difficulty,points,first_blood_only,flag,bank_id,pool,wave,artifact_url,solution_internal
                lin-001,Updated Linux,New desc,Linux,Легко,3,True,CTF{new_flag},G1,MAIN,1,,Sol1
                RES-001,Reserve Task,Hidden task,Web,Сложно,18,True,CTF{reserve_flag},G1,RESERVE,2,,Sol2
                """;

        // 1. Dry run must not persist anything
        CtfTaskImportService.TaskImportResultDto dryResult = importService.importTasks(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "tasks.csv",
                true,
                compId
        );

        assertThat(dryResult.dryRun()).isTrue();
        assertThat(dryResult.errors()).isEmpty();
        assertThat(dryResult.updatedCount()).isEqualTo(1);
        assertThat(dryResult.createdCount()).isEqualTo(1);
        assertThat(dryResult.mainPoolCount()).isEqualTo(1);
        assertThat(dryResult.reservePoolCount()).isEqualTo(1);
        assertThat(dryResult.linkedToCompetitionCount()).isEqualTo(1);
        verify(ctfTaskRepository, never()).saveAll(any());
        verify(competitionRepository, never()).save(any());

        // 2. Real import saves both tasks and links only MAIN pool task to competition
        when(ctfTaskRepository.saveAll(any())).thenAnswer(inv -> {
            List<CtfTask> list = inv.getArgument(0);
            list.forEach(t -> {
                if (t.getId() == null) t.setId(UUID.randomUUID());
            });
            return list;
        });

        CtfTaskImportService.TaskImportResultDto realResult = importService.importTasks(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "tasks.csv",
                false,
                compId
        );

        assertThat(realResult.dryRun()).isFalse();
        assertThat(realResult.updatedCount()).isEqualTo(1);
        assertThat(realResult.createdCount()).isEqualTo(1);
        assertThat(realResult.linkedToCompetitionCount()).isEqualTo(1);
        assertThat(existing.getId()).isEqualTo(existingId);
        assertThat(existing.getTitle()).isEqualTo("Updated Linux");
        assertThat(existing.getFlag()).isEqualTo("CTF{new_flag}");
        assertThat(competition.getTasks()).hasSize(1);
        assertThat(competition.getTasks().iterator().next().getTaskCode()).isEqualTo("lin-001");
    }

    @Test
    @DisplayName("Validates duplicate task_id as error and duplicate flags / invalid format as warnings")
    void testValidationErrorsAndWarnings() {
        when(ctfTaskRepository.findAll()).thenReturn(List.of());

        String csv = """
                task_id,title,description,category,difficulty,points,first_blood_only,flag,bank_id,pool
                DUP-001,Task A,Desc A,Web,Легко,3,True,plain_flag,GLOBAL,MAIN
                dup-001,Task B,Desc B,Web,Легко,3,True,plain_flag,GLOBAL,MAIN
                """;

        CtfTaskImportService.TaskImportResultDto result = importService.importTasks(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "bad.csv",
                false,
                null
        );

        assertThat(result.errors()).anyMatch(e -> e.contains("дубликат task_id"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("не соответствует формату CTF{...}"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("дублирующийся флаг"));
        verify(ctfTaskRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Imports XLSX generated with x: namespace prefixes (e.g. Excel/openpyxl) and ignores empty styled rows")
    void testImportPrefixedNamespaceXlsxAndEmptyStyledRows() throws Exception {
        when(ctfTaskRepository.findAll()).thenReturn(List.of());
        when(ctfTaskRepository.saveAll(any())).thenAnswer(inv -> {
            List<CtfTask> list = inv.getArgument(0);
            list.forEach(t -> {
                if (t.getId() == null) t.setId(UUID.randomUUID());
            });
            return list;
        });

        String sheet1Xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <x:worksheet xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <x:sheetData>
                    <x:row r="1">
                      <x:c r="A1" t="str"><x:v>task_id</x:v></x:c>
                      <x:c r="B1" t="str"><x:v>title</x:v></x:c>
                      <x:c r="C1" t="str"><x:v>description</x:v></x:c>
                      <x:c r="D1" t="str"><x:v>category</x:v></x:c>
                      <x:c r="E1" t="str"><x:v>difficulty</x:v></x:c>
                      <x:c r="F1" t="str"><x:v>points</x:v></x:c>
                      <x:c r="G1" t="str"><x:v>first_blood_only</x:v></x:c>
                      <x:c r="H1" t="str"><x:v>flag</x:v></x:c>
                    </x:row>
                    <x:row r="2">
                      <x:c r="A2" t="str"><x:v>LIN-001</x:v></x:c>
                      <x:c r="B2" t="str"><x:v>Права аудитора</x:v></x:c>
                      <x:c r="C2" t="str"><x:v>Описание задания</x:v></x:c>
                      <x:c r="D2" t="str"><x:v>Linux</x:v></x:c>
                      <x:c r="E2" t="str"><x:v>Легко</x:v></x:c>
                      <x:c r="F2" t="n"><x:v>3</x:v></x:c>
                      <x:c r="G2" t="b"><x:v>1</x:v></x:c>
                      <x:c r="H2" t="str"><x:v>CTF{read_only}</x:v></x:c>
                    </x:row>
                    <x:row r="3">
                      <x:c r="A3" s="12" />
                      <x:c r="H3" s="12" />
                    </x:row>
                  </x:sheetData>
                </x:worksheet>
                """;

        String sharedStringsXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <x:sst xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main" />
                """;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zos.write(sharedStringsXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zos.write(sheet1Xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        CtfTaskImportService.TaskImportResultDto result = importService.importTasks(
                new ByteArrayInputStream(baos.toByteArray()),
                "CTF_template.xlsx",
                false,
                null
        );

        assertThat(result.errors()).isEmpty();
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.totalPoints()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CtfTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(ctfTaskRepository).saveAll(captor.capture());
        CtfTask saved = captor.getValue().get(0);
        assertThat(saved.getTaskCode()).isEqualTo("LIN-001");
        assertThat(saved.getTitle()).isEqualTo("Права аудитора");
        assertThat(saved.getBaseScore()).isEqualTo(3);
        assertThat(saved.isFirstBloodOnly()).isTrue();
        assertThat(saved.getFlag()).isEqualTo("CTF{read_only}");
    }
}
