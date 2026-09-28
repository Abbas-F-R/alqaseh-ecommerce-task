package com.alqaseh.ecommerce.features.discount.service;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.shared.result.Result;

import java.math.BigDecimal;

public interface DiscountService {

    /**
     * Validates the code against the order subtotal and, if valid, marks it as used.
     * Must run inside the order transaction so the redemption is rolled back if the order fails.
     */
    Result<DiscountCode> redeem(String code, BigDecimal subtotal);
}
