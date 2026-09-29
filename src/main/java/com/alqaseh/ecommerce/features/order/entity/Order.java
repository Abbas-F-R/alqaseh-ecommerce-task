package com.alqaseh.ecommerce.features.order.entity;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Indexes follow the actual queries: "my orders" (customer + newest first), admin listing (newest first)
 * and admin listing filtered by payment method.
 */
@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_created_at", columnList = "created_at"),
                @Index(name = "idx_orders_customer_created", columnList = "customer_id, created_at"),
                @Index(name = "idx_orders_payment_created", columnList = "payment_method, created_at")
        }
)
@Check(constraints = "subtotal_amount >= 0 AND discount_amount >= 0 AND total_amount >= 0 AND total_cost >= 0")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_customer"))
    private User customer;

    @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_code_id", foreignKey = @ForeignKey(name = "fk_orders_discount_code"))
    private DiscountCode discountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    /** Items only live and die with their order: persisted through it, deleted by the FK's ON DELETE CASCADE. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /** Revenue actually collected (after discount) minus the cost of the goods sold. */
    public BigDecimal getProfit() {
        return totalAmount.subtract(totalCost);
    }
}
