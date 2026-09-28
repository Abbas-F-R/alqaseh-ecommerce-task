package com.alqaseh.ecommerce.features.discount.service;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.features.discount.repository.DiscountCodeRepository;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements DiscountService {

    private final DiscountCodeRepository discountCodeRepository;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result<DiscountCode> redeem(String code, BigDecimal subtotal) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);

        DiscountCode discount = discountCodeRepository.findByCode(normalized).orElse(null);
        if (discount == null) {
            return Result.failure(ErrorCode.DISCOUNT_NOT_FOUND, normalized);
        }
        if (discount.isUsed()) {
            return Result.failure(ErrorCode.DISCOUNT_ALREADY_USED, normalized);
        }
        if (discount.isExpiredAt(clock.instant())) {
            return Result.failure(ErrorCode.DISCOUNT_EXPIRED, normalized);
        }
        if (subtotal.compareTo(discount.getMinimumOrderTotal()) < 0) {
            return Result.failure(ErrorCode.MINIMUM_ORDER_TOTAL_NOT_MET);
        }
        if (discount.getAmount().compareTo(subtotal) > 0) {
            return Result.failure(ErrorCode.DISCOUNT_EXCEEDS_TOTAL);
        }

        // Managed entity + @Version: a concurrent redemption of the same code fails at flush (409), never twice.
        discount.markAsUsed();
        return Result.success(discount);
    }
}
