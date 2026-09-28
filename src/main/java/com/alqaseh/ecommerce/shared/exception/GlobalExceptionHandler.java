package com.alqaseh.ecommerce.shared.exception;

import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.localization.LocalizationService;
import com.alqaseh.ecommerce.shared.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single place where exceptions become HTTP responses.
 *
 * <ul>
 *   <li>Request-shape problems (Bean Validation, malformed JSON, bad UUID, wrong method, ...) become 4xx.</li>
 *   <li>Known concurrency / unique-constraint races become 409.</li>
 *   <li>Everything else is a bug or an infrastructure failure: 500 with a safe body, stack trace only in the log.</li>
 * </ul>
 * Extending {@link ResponseEntityExceptionHandler} keeps Spring's own MVC exceptions on their proper 4xx status
 * instead of letting a catch-all handler turn them into 500s.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** DB constraint (the final guard against races) to business error. */
    private static final Map<String, ErrorCode> CONSTRAINT_ERRORS = Map.of(
            "uq_products_name", ErrorCode.PRODUCT_NAME_ALREADY_EXISTS
    );

    private final LocalizationService localization;
    private final boolean includeDebugDetails;

    public GlobalExceptionHandler(LocalizationService localization,
                                  @Value("${application.error.include-debug-details:false}") boolean includeDebugDetails) {
        this.localization = localization;
        this.includeDebugDetails = includeDebugDetails;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        // A binding failure ("abc" for a number, unknown enum constant, malformed UUID) has a default message that names
        // internal classes; the client only needs to know which field is invalid.
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(),
                e.isBindingFailure() ? localization.getMessage("validation.invalid_value") : e.getDefaultMessage()));
        log.warn("Validation failed for {}: fields={}", path(request), errors.keySet());
        return ResponseEntity.badRequest()
                .body(body(ErrorCode.VALIDATION_ERROR, path(request)).withValidationErrors(errors));
    }

    /** Every other Spring MVC exception (malformed JSON, type mismatch, 404, 405, 415, ...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            return ResponseEntity.internalServerError().headers(headers).body(unexpected(ex, path(request)));
        }
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ApiErrorResponse response = status == HttpStatus.BAD_REQUEST
                ? body(ErrorCode.BAD_REQUEST, path(request))
                : ApiErrorResponse.of(status.value(), status.name(), status.getReasonPhrase(), path(request));
        log.warn("Rejected request {}: {} ({})", path(request), status, ex.getClass().getSimpleName());
        return ResponseEntity.status(statusCode).headers(headers).body(response);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent modification on {}", request.getRequestURI());
        return respond(ErrorCode.CONCURRENT_MODIFICATION, request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String cause = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase();
        return CONSTRAINT_ERRORS.entrySet().stream()
                .filter(entry -> cause.contains(entry.getKey()))
                .findFirst()
                .map(entry -> {
                    log.warn("Constraint {} violated on {}", entry.getKey(), request.getRequestURI());
                    return respond(entry.getValue(), request.getRequestURI());
                })
                .orElseGet(() -> handleUnexpected(ex, request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied for {}", request.getRequestURI());
        return respond(ErrorCode.FORBIDDEN, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(unexpected(ex, request.getRequestURI()));
    }

    private ApiErrorResponse unexpected(Exception ex, String path) {
        log.error("Unexpected error on {}", path, ex);
        ApiErrorResponse response = body(ErrorCode.INTERNAL_SERVER_ERROR, path);
        return includeDebugDetails ? response.withDebug(ex.getClass().getName() + ": " + ex.getMessage()) : response;
    }

    private ResponseEntity<ApiErrorResponse> respond(ErrorCode code, String path) {
        return ResponseEntity.status(code.getHttpStatus()).body(body(code, path));
    }

    private ApiErrorResponse body(ErrorCode code, String path) {
        return ApiErrorResponse.of(code, localization.getMessage(code.getMessageKey()), path);
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
