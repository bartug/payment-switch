/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.exception;

import com.bartugsevindik.paymentswitch.common.helpers.ResponseHelper;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Hidden
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Log4j2
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NotNull MethodArgumentNotValidException ex,
                                                                  @NotNull HttpHeaders headers,
                                                                  @NotNull HttpStatusCode status,
                                                                  @NotNull WebRequest request) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : "İstek doğrulanamadı.";
        return ResponseEntity.badRequest().body(ResponseHelper.badRequest(message));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NotNull HttpMessageNotReadableException ex,
                                                                  @NotNull HttpHeaders headers,
                                                                  @NotNull HttpStatusCode status,
                                                                  @NotNull WebRequest request) {
        return ResponseEntity.badRequest().body(ResponseHelper.badRequest("İstek gövdesi okunamadı."));
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(@NotNull ServletRequestBindingException ex,
                                                                          @NotNull HttpHeaders headers,
                                                                          @NotNull HttpStatusCode status,
                                                                          @NotNull WebRequest request) {
        String message = ex instanceof MissingRequestHeaderException missingHeader
                ? missingHeader.getHeaderName() + " header'ı zorunludur."
                : "İstek parametreleri okunamadı.";
        return ResponseEntity.badRequest().body(ResponseHelper.badRequest(message));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(@NotNull HandlerMethodValidationException ex,
                                                                            @NotNull HttpHeaders headers,
                                                                            @NotNull HttpStatusCode status,
                                                                            @NotNull WebRequest request) {
        // Header gibi bir parametrede constraint varsa Spring body'yi de method validation ile doğrular
        String message = ex.getAllErrors().stream()
                .findFirst()
                .map(error -> error instanceof FieldError fieldError
                        ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                        : error.getDefaultMessage())
                .orElse("İstek doğrulanamadı.");
        return ResponseEntity.badRequest().body(ResponseHelper.badRequest(message));
    }

    @ExceptionHandler(value = NotFoundException.class)
    public @NotNull ResponseEntity<Object> handleNotFoundException(@NotNull NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResponseHelper.notFound(ex.getMessage()));
    }

    @ExceptionHandler(value = BadRequestException.class)
    public @NotNull ResponseEntity<Object> handleBadRequestException(@NotNull BadRequestException ex) {
        return ResponseEntity.badRequest()
                .body(ResponseHelper.badRequest(ex.getMessage()));
    }

    @ExceptionHandler(value = ConflictException.class)
    public @NotNull ResponseEntity<Object> handleConflictException(@NotNull ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CONFLICT);
        if (ex.getRetryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        return builder.body(ResponseHelper.conflict(ex.getMessage()));
    }

    @ExceptionHandler(value = UnprocessableEntityException.class)
    public @NotNull ResponseEntity<Object> handleUnprocessableEntityException(@NotNull UnprocessableEntityException ex) {
        log.warn("Unprocessable entity: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ResponseHelper.unprocessable(ex.getMessage()));
    }

    @ExceptionHandler(value = Exception.class)
    public @NotNull ResponseEntity<Object> handleException(@NotNull Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseHelper.error("Beklenmeyen bir hata oluştu."));
    }
}
