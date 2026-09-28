package com.alqaseh.ecommerce.shared.controller;

import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.localization.LocalizationService;
import com.alqaseh.ecommerce.shared.response.ApiErrorResponse;
import com.alqaseh.ecommerce.shared.response.ApiResponse;
import com.alqaseh.ecommerce.shared.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Translates a service {@link Result} into the HTTP response (success envelope or localized error). */
public abstract class BaseController {

    @Autowired
    private LocalizationService localizationService;

    protected <T> ResponseEntity<?> toResponseEntity(Result<T> result, HttpStatus successStatus, HttpServletRequest request) {
        if (result.isSuccess()) {
            return ResponseEntity.status(successStatus).body(ApiResponse.success(result.getValue()));
        }
        return error(result, request);
    }

    protected <T> ResponseEntity<?> toResponseEntity(Result<T> result, HttpServletRequest request) {
        return toResponseEntity(result, HttpStatus.OK, request);
    }

    private ResponseEntity<ApiErrorResponse> error(Result<?> result, HttpServletRequest request) {
        ErrorCode code = result.getErrorCode();
        String message = localizationService.getMessage(code.getMessageKey(), result.getArgs());
        return ResponseEntity.status(code.getHttpStatus()).body(ApiErrorResponse.of(code, message, request.getRequestURI()));
    }
}
