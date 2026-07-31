package com.firedemo.edumind.shared.exception;

import com.firedemo.edumind.shared.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>返回给用户的 message 是能看懂的中文，说明"哪里出了问题"</li>
 *   <li>技术细节（堆栈、完整字段名）只打在日志里，不暴露给客户端</li>
 *   <li>每个响应携带 requestId，用户反馈问题时提供该 ID 即可快速定位</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_ID_KEY = "requestId";

    // ─────────────────── 业务 / 安全 ───────────────────

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return response(e.getHttpStatus(), Result.error(e.getCode(), e.getMessage(), requestId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return response(HttpStatus.FORBIDDEN, Result.error(ErrorCode.FORBIDDEN, requestId()));
    }

    // ─────────────────── 参数校验 ───────────────────

    /** GET 请求参数 / 表单绑定校验 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        String fields = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", fields);
        return badRequest("参数校验失败 — " + fields);
    }

    /** POST JSON @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String fields = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("JSON 参数校验失败: {}", fields);
        return badRequest("参数校验失败 — " + fields);
    }

    /** Service / Controller 层 @Validated 校验失败（路径参数、方法参数） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String fields = e.getConstraintViolations().stream()
                .map(v -> propertyName(v) + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("方法参数校验失败: {}", fields);
        return badRequest("参数校验失败 — " + fields);
    }

    // ─────────────────── 请求格式 ───────────────────

    /** JSON 格式错误（少引号、多逗号、字符串写成了数字等） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体不可读（JSON 格式错误）: {}", e.getMessage());
        String hint = e.getMessage() != null && e.getMessage().contains("JSON")
                ? "请检查 JSON 格式（逗号、引号、括号是否配对）"
                : "请检查请求体格式";
        return badRequest("请求体格式错误 — " + hint);
    }

    /** 缺少必填的 @RequestParam */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return badRequest("缺少必填参数「" + e.getParameterName() + "」");
    }

    /** 参数类型转换失败（如 ?id=abc 但 id 是 Long） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expected = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知";
        log.warn("参数类型错误: {} 应为 {} 类型", e.getName(), expected);
        return badRequest("参数「" + e.getName() + "」格式不正确，应为" + typeName(expected));
    }

    /** 请求 Content-Type 不支持（如只接受 JSON 但发了 form-data） */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的 Content-Type: {}", e.getContentType());
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                Result.error(ErrorCode.PARAM_ERROR.getCode(),
                        "不支持的请求格式，Content-Type 应为 application/json", requestId()));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        log.warn("无法生成客户端接受的响应格式: {}", e.getMessage());
        return response(HttpStatus.NOT_ACCEPTABLE,
                Result.error(ErrorCode.PARAM_ERROR.getCode(), "不支持请求的 Accept 响应格式", requestId()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: method={}", e.getMethod());
        return response(HttpStatus.METHOD_NOT_ALLOWED,
                Result.error(ErrorCode.PARAM_ERROR.getCode(), "请求方法不支持", requestId()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("请求资源不存在: {}", e.getResourcePath());
        return response(HttpStatus.NOT_FOUND,
                Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "请求资源不存在", requestId()));
    }

    // ─────────────────── 文件上传 ───────────────────

    /** 上传文件超出大小限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        long max = e.getMaxUploadSize() / 1024 / 1024;
        log.warn("文件大小超出限制: max={}MB", max);
        return response(HttpStatus.PAYLOAD_TOO_LARGE,
                Result.error(ErrorCode.FILE_UPLOAD_ERROR.getCode(),
                        "文件大小超出限制，最大支持 " + max + " MB", requestId()));
    }

    // ─────────────────── 非法参数 ───────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return badRequest(e.getMessage());
    }

    // ─────────────────── 数据库 ───────────────────

    /** 数据库异常 — 不暴露内部细节给客户端，但日志记录完整堆栈 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccess(DataAccessException e) {
        log.error("数据库异常: type={}, message={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                Result.error(ErrorCode.SYSTEM_ERROR.getCode(),
                        "数据操作失败，请稍后重试或联系管理员", requestId()));
    }

    // ─────────────────── 兜底 ───────────────────

    /** 未预期异常兜底（NPE、IO 等） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("未预期异常: type={}, message={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                Result.error(ErrorCode.SYSTEM_ERROR.getCode(),
                        "服务器内部错误，请联系管理员并提供以下请求ID", requestId()));
    }

    // ─────────────────── 内部工具 ───────────────────

    /** 从 MDC 取当前请求的 requestId */
    private static String requestId() {
        String id = MDC.get(REQUEST_ID_KEY);
        return id != null ? id : "N/A";
    }

    private static ResponseEntity<Result<Void>> badRequest(String message) {
        return response(HttpStatus.BAD_REQUEST,
                Result.error(ErrorCode.PARAM_ERROR.getCode(), message, requestId()));
    }

    private static ResponseEntity<Result<Void>> response(HttpStatus status, Result<Void> body) {
        return ResponseEntity.status(status).body(body);
    }

    /** 从 ConstraintViolation 中提取简洁的属性名（取路径最后一段） */
    private static String propertyName(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    /** 将 Java 类型名转成用户能看懂的描述 */
    private static String typeName(String javaType) {
        return switch (javaType) {
            case "Long", "Integer", "int" -> "整数";
            case "Double", "Float", "BigDecimal" -> "数字";
            case "Boolean", "boolean" -> "true/false";
            default -> javaType;
        };
    }
}
