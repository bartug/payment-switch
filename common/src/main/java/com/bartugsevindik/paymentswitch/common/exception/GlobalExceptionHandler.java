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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
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
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ResponseHelper.conflict(ex.getMessage()));
    }

    @ExceptionHandler(value = Exception.class)
    public @NotNull ResponseEntity<Object> handleException(@NotNull Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseHelper.error("Beklenmeyen bir hata oluştu."));
    }
}
