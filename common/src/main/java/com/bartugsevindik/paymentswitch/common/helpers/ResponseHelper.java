/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.common.helpers;

import com.bartugsevindik.paymentswitch.common.dto.ResponseMessage;
import org.springframework.http.HttpStatus;

public enum ResponseHelper {
    ;

    public static ResponseMessage success(String message, Object object) {
        return build(true, message, object, HttpStatus.OK);
    }

    public static ResponseMessage accepted(String message, Object object) {
        return build(true, message, object, HttpStatus.ACCEPTED);
    }

    public static ResponseMessage badRequest(String message) {
        return build(false, message, null, HttpStatus.BAD_REQUEST);
    }

    public static ResponseMessage notFound(String message) {
        return build(false, message, null, HttpStatus.NOT_FOUND);
    }

    public static ResponseMessage conflict(String message) {
        return build(false, message, null, HttpStatus.CONFLICT);
    }

    public static ResponseMessage unprocessable(String message) {
        return build(false, message, null, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static ResponseMessage error(String message) {
        return build(false, message, null, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ResponseMessage build(boolean success, String message, Object object, HttpStatus status) {
        return ResponseMessage.builder()
                .success(success)
                .message(message)
                .object(object)
                .httpStatus(status)
                .httpStatusCode(status.value())
                .build();
    }
}
