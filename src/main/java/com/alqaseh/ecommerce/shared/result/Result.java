package com.alqaseh.ecommerce.shared.result;

import com.alqaseh.ecommerce.shared.error.ErrorCode;
import lombok.Getter;

import java.util.Objects;

/**
 * Outcome of an <em>expected</em> business operation: either a value or a stable {@link ErrorCode}.
 * Unexpected technical failures are never wrapped here; they propagate as exceptions to the
 * global exception handler.
 */
@Getter
public final class Result<T> {

    private final T value;
    private final ErrorCode errorCode;
    private final Object[] args;

    private Result(T value, ErrorCode errorCode, Object[] args) {
        this.value = value;
        this.errorCode = errorCode;
        this.args = args;
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(value, null, new Object[0]);
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, Object... args) {
        Objects.requireNonNull(errorCode, "errorCode");
        return new Result<>(null, errorCode, args);
    }

    public boolean isSuccess() {
        return errorCode == null;
    }

    public boolean isFailure() {
        return errorCode != null;
    }
}
