package com.alqaseh.ecommerce.features.product.validation;

import com.alqaseh.ecommerce.features.product.util.ProductCursor;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidCursorValidator implements ConstraintValidator<ValidCursor, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return ProductCursor.isValid(value);
    }
}
