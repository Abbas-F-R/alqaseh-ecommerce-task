package com.alqaseh.ecommerce.features.discount.entity;

import com.alqaseh.ecommerce.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single-use fixed-amount discount. Codes are stored upper-case; uniqueness is
 * enforced by the unique index {@code uq_discount_codes_code} (Flyway).
 */
@Entity
@Table(name = "discount_codes")
@Check(constraints = "amount > 0 AND minimum_order_total >= 0")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class DiscountCode extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "minimum_order_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumOrderTotal;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used;

    /** Optimistic lock: two concurrent orders can never both redeem the same code. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void markAsUsed() {
        this.used = true;
    }
}
