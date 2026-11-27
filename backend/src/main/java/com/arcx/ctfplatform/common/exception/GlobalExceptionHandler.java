package com.arcx.ctfplatform.common.exception;

import com.arcx.ctfplatform.common.response.ApiErrorResponse;
import com.arcx.ctfplatform.common.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final TimeProvider timeProvider;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), localizeMessage(fieldError.getDefaultMessage()));
        }
        return build(HttpStatus.BAD_REQUEST, "Ошибка проверки полей формы", request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, localizeMessage(ex.getMessage()), request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, "Некорректный формат данных запроса", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, "Некорректное значение параметра запроса", request, null);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, localizeMessage(ex.getMessage()), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.UNAUTHORIZED, localizeMessage(ex.getMessage()), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.FORBIDDEN, localizeMessage(ex.getMessage()), request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Размер загружаемого файла превышает допустимый лимит", request, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatusCode status = ex.getStatusCode();
        String rawReason = ex.getReason();
        String message = (rawReason != null && !rawReason.isBlank())
                ? localizeMessage(rawReason)
                : resolveReasonPhrase(status);
        return build(status, message, request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        Throwable mostSpecific = ex.getMostSpecificCause();
        String causeMessage = mostSpecific != null ? mostSpecific.getMessage() : ex.getMessage();
        log.warn("Data integrity violation on path {}: {}", request.getRequestURI(), causeMessage);

        String sqlState = extractSqlState(ex);
        String lower = causeMessage != null ? causeMessage.toLowerCase(Locale.ROOT) : "";

        if ("22001".equals(sqlState) || "23502".equals(sqlState) || "23514".equals(sqlState)
                || lower.contains("value too long")
                || lower.contains("data truncation")
                || lower.contains("not-null")
                || lower.contains("null value in column")
                || lower.contains("check constraint")
                || lower.contains("chk_")) {
            return build(HttpStatus.BAD_REQUEST,
                    "Некорректные данные: превышена допустимая длина или нарушены ограничения", request, null);
        }
        if ("23505".equals(sqlState)
                || lower.contains("duplicate key")
                || lower.contains("unique constraint")
                || lower.contains("already exists")) {
            return build(HttpStatus.CONFLICT, "Запись с такими данными уже существует", request, null);
        }
        if ("23503".equals(sqlState) || lower.contains("foreign key")) {
            return build(HttpStatus.CONFLICT,
                    "Операция невозможна: нарушена целостность связанных данных", request, null);
        }
        return build(HttpStatus.CONFLICT,
                "Конфликт данных: запись уже существует или связана с другими объектами", request, null);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNoSuchElement(
            NoSuchElementException ex,
            HttpServletRequest request
    ) {
        String raw = ex.getMessage();
        String message = (raw == null || raw.isBlank()) ? "Ресурс не найден" : localizeMessage(raw);
        return build(HttpStatus.NOT_FOUND, message, request, null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST,
                "Отсутствует обязательная часть запроса: " + ex.getRequestPartName(), request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST,
                "Отсутствует обязательный параметр запроса: " + ex.getParameterName(), request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Метод запроса не поддерживается", request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.NOT_FOUND, "Ресурс не найден", request, null);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(
            AsyncRequestNotUsableException ex,
            HttpServletRequest request
    ) {
        log.debug("Async request closed by client on path {}: {}", request.getRequestURI(), ex.getMessage());
        return null;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandled(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception on path: {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", request, null);
    }

    private String localizeMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Некорректный запрос";
        }
        return switch (raw) {
            case "Invalid credentials", "Bad credentials" -> "Неверный логин (email) или пароль";
            case "User is blocked", "Student account is blocked", "User account is blocked" ->
                    "Учётная запись заблокирована. Обратитесь к преподавателю или администратору";
            case "Email already registered", "User with this email already exists" ->
                    "Этот email уже зарегистрирован";
            case "Username already taken", "User with this username already exists" ->
                    "Это имя пользователя уже занято";
            case "Email is invalid" -> "Некорректный формат email";
            case "Student code not registered", "Invalid student code" ->
                    "Шифр / токен студента не найден в списке курса";
            case "This student code is already linked", "Student code already claimed" ->
                    "Этот шифр студента уже привязан к другой учётной записи";
            case "Student code already exists" -> "Студент с таким шифром уже существует";
            case "Student is not pending binding verification" ->
                    "Учётная запись студента не ожидает подтверждения привязки";
            case "Student not found" -> "Профиль студента не найден";
            case "studentId is required" -> "Не выбран студент";
            case "Student ID cannot be null" -> "Не выбран студент";
            case "Score must be non-negative" -> "Баллы не могут быть отрицательными";
            case "Reason cannot be blank" -> "Необходимо указать причину";
            case "Competition not found" -> "Соревнование не найдено";
            case "Competition is not available" -> "Соревнование недоступно";
            case "Competition is not active" -> "Соревнование сейчас не активно";
            case "startDate cannot be after endDate" ->
                    "Дата начала соревнования не может быть позже даты окончания";
            case "Quiz not found", "Test not found" -> "Тест не найден";
            case "Question not found" -> "Вопрос не найден";
            case "Option not found" -> "Вариант ответа не найден";
            case "Challenge not found in this test" -> "Задание не найдено в этом тесте";
            case "Quiz is not published", "Test is not published" -> "Тест ещё не опубликован";
            case "Active quiz attempt is required to view questions" ->
                    "Для просмотра вопросов необходимо начать попытку прохождения теста";
            case "No active attempt found for this quiz" -> "Активная попытка прохождения теста не найдена";
            case "Quiz time limit exceeded" -> "Время на прохождение теста истекло";
            case "Invalid promo code" -> "Промокод не найден или введён неверно";
            case "Promo code already used by this student" -> "Вы уже использовали этот промокод ранее";
            case "Promo code usage limit reached" -> "Лимит использований этого промокода исчерпан";
            case "Group not found" -> "Группа не найдена";
            case "Group exists with this name", "Group with this name already exists" ->
                    "Группа с таким названием уже существует";
            case "Academic flow not found" -> "Учебный поток не найден";
            case "Flow with this name already exists" -> "Поток с таким названием уже существует";
            case "Scale not found" -> "Шкала оценивания не найдена";
            case "Task not found" -> "Задание не найдено";
            case "Task is not available" -> "Задание недоступно";
            case "Flag is required for FLAG tasks" -> "Для задания с флагом необходимо указать флаг";
            case "Task is not associated with any competition" -> "Задание не привязано к соревнованию";
            case "Invalid task type for this endpoint" -> "Для этого задания отправка флага недоступна";
            case "Invalid task type for file upload" -> "Для этого задания загрузка файла недоступна";
            case "First Blood Only challenge has already been solved" ->
                    "Задание «Первая кровь» уже решено другим участником";
            case "Challenge already solved" -> "Это задание уже решено вами";
            case "Too many wrong attempts. Please wait before trying again." ->
                    "Слишком много неверных попыток. Подождите немного перед следующей отправкой";
            case "Attempt not found" -> "Попытка не найдена";
            case "No file associated with this attempt" -> "К этой попытке не прикреплён файл";
            case "Uploaded file cannot be empty" -> "Загружаемый файл не может быть пустым";
            case "Invalid file name" -> "Некорректное имя файла";
            case "Unsupported file extension. Allowed extensions: .pdf, .zip, .txt" ->
                    "Недопустимое расширение файла. Разрешены: .pdf, .zip, .txt";
            case "Invalid file path" -> "Некорректный путь к файлу";
            case "Session type is required" -> "Необходимо указать тип занятия";
            case "Invalid CSV row: expected studentCode and score" ->
                    "Некорректная строка CSV: ожидаются шифр студента и баллы";
            case "Cycle number must be between" -> "Номер занятия вне допустимого диапазона";
            case "Token must not exceed 64 characters" -> "Токен не должен превышать 64 символа";
            case "Exam score must be between 0 and 100" -> "Балл за экзамен должен быть в диапазоне от 0 до 100";
            case "Penalty coefficient must be between 0.0 and 1.0" ->
                    "Штрафной коэффициент должен быть в диапазоне от 0.0 до 1.0";
            case "No value present" -> "Ресурс не найден";
            case "Access Denied", "Access is denied" -> "Недостаточно прав для выполнения операции";
            default -> {
                if (raw.startsWith("Student not found:")) {
                    yield "Профиль студента не найден";
                }
                if (raw.startsWith("Student is not allowed to start quiz in status:")) {
                    yield "Прохождение теста недоступно для текущего статуса учётной записи";
                }
                if (raw.startsWith("Student status does not allow submissions:")) {
                    yield "Отправка решений недоступна для текущего статуса учётной записи";
                }
                if (raw.startsWith("Task with this task_id already exists:")) {
                    yield "Задание с таким идентификатором (task_id) уже существует";
                }
                if (raw.startsWith("Invalid lab number:")) {
                    yield "Некорректный номер лабораторной работы (допустимы 1–8)";
                }
                if (raw.startsWith("Invalid score in CSV")) {
                    yield "Некорректное значение баллов в CSV-файле";
                }
                if (raw.startsWith("Failed to import CSV:") || raw.startsWith("Failed to parse and import students spreadsheet:")) {
                    yield "Не удалось загрузить файл: проверьте формат таблицы и данные";
                }
                if (raw.startsWith("Cycle number must be between")) {
                    yield "Номер занятия вне допустимого диапазона";
                }
                yield raw;
            }
        };
    }

    private String extractSqlState(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLException sqlEx && sqlEx.getSQLState() != null) {
                return sqlEx.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private String resolveReasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatusCode status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(timeProvider.now())
                .status(status.value())
                .error(resolveReasonPhrase(status))
                .message(message)
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
