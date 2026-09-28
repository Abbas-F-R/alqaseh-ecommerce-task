package com.alqaseh.ecommerce.features.discount.service;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.features.discount.repository.DiscountCodeRepository;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    private static final Instant NOW = Instant.parse("2030-06-01T12:00:00Z");

    @Mock
    private DiscountCodeRepository discountCodeRepository;

    private DiscountServiceImpl discountService;

    @BeforeEach
    void setUp() {
        discountService = new DiscountServiceImpl(discountCodeRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DiscountCode.DiscountCodeBuilder<?, ?> validDiscount() {
        return DiscountCode.builder()
                .code("SAVE20")
                .amount(BigDecimal.valueOf(20))
                .minimumOrderTotal(BigDecimal.valueOf(100))
                .expiresAt(NOW.plusSeconds(3600))
                .used(false);
    }

    @Test
    @DisplayName("A valid code is redeemed: success and marked as used")
    void redeemsValidCode() {
        DiscountCode discount = validDiscount().build();
        when(discountCodeRepository.findByCode("SAVE20")).thenReturn(Optional.of(discount));

        Result<DiscountCode> result = discountService.redeem("SAVE20", BigDecimal.valueOf(150));

        assertThat(result.isSuccess()).isTrue();
        assertThat(discount.isUsed()).isTrue();
    }

    @Test
    @DisplayName("Codes are matched case-insensitively and ignoring surrounding whitespace")
    void normalizesCode() {
        DiscountCode discount = validDiscount().build();
        when(discountCodeRepository.findByCode("SAVE20")).thenReturn(Optional.of(discount));

        assertThat(discountService.redeem("  save20 ", BigDecimal.valueOf(150)).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Unknown code: DISCOUNT_NOT_FOUND")
    void rejectsUnknownCode() {
        when(discountCodeRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThat(discountService.redeem("NOPE", BigDecimal.TEN).getErrorCode()).isEqualTo(ErrorCode.DISCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("Already used code: DISCOUNT_ALREADY_USED, and it stays used")
    void rejectsUsedCode() {
        DiscountCode discount = validDiscount().used(true).build();
        when(discountCodeRepository.findByCode("SAVE20")).thenReturn(Optional.of(discount));

        Result<DiscountCode> result = discountService.redeem("SAVE20", BigDecimal.valueOf(150));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.DISCOUNT_ALREADY_USED);
    }

    @Test
    @DisplayName("Expired code: DISCOUNT_EXPIRED, and it is not consumed")
    void rejectsExpiredCode() {
        DiscountCode discount = validDiscount().expiresAt(NOW.minusSeconds(1)).build();
        when(discountCodeRepository.findByCode("SAVE20")).thenReturn(Optional.of(discount));

        Result<DiscountCode> result = discountService.redeem("SAVE20", BigDecimal.valueOf(150));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.DISCOUNT_EXPIRED);
        assertThat(discount.isUsed()).isFalse();
    }

    @Test
    @DisplayName("Subtotal below the minimum: MINIMUM_ORDER_TOTAL_NOT_MET, and it is not consumed")
    void rejectsBelowMinimum() {
        DiscountCode discount = validDiscount().build();
        when(discountCodeRepository.findByCode("SAVE20")).thenReturn(Optional.of(discount));

        Result<DiscountCode> result = discountService.redeem("SAVE20", BigDecimal.valueOf(99.99));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.MINIMUM_ORDER_TOTAL_NOT_MET);
        assertThat(discount.isUsed()).isFalse();
    }

    @Test
    @DisplayName("Discount larger than the subtotal: DISCOUNT_EXCEEDS_TOTAL")
    void rejectsDiscountLargerThanSubtotal() {
        DiscountCode discount = validDiscount().amount(BigDecimal.valueOf(150)).minimumOrderTotal(BigDecimal.ZERO).build();
        when(discountCodeRepository.findByCode("SAVE20")).thenReturn(Optional.of(discount));

        Result<DiscountCode> result = discountService.redeem("SAVE20", BigDecimal.valueOf(100));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.DISCOUNT_EXCEEDS_TOTAL);
        assertThat(discount.isUsed()).isFalse();
    }

    @Test
    @DisplayName("A code expiring exactly now is still valid")
    void codeExpiringNowIsValid() {
        DiscountCode discount = validDiscount().expiresAt(NOW).build();
        when(discountCodeRepository.findByCode("SAVE20")).thenReturn(Optional.of(discount));

        assertThat(discountService.redeem("SAVE20", BigDecimal.valueOf(150)).isSuccess()).isTrue();
    }
}
