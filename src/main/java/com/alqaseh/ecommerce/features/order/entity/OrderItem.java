package com.alqaseh.ecommerce.features.order.entity;

import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.shared.util.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/** One order line. Name, price and cost are snapshots taken at checkout and never change afterwards. */
@Entity
@Table(name = "order_items", indexes = @Index(name = "idx_order_items_order_id", columnList = "order_id"))
@Check(constraints = "quantity > 0 AND unit_price >= 0 AND unit_cost >= 0")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderItem {

    @Id
    @UuidGenerator(algorithm = UuidV7Generator.class)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_items_order"))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_items_product"))
    private Product product;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getTotalCost() {
        return unitCost.multiply(BigDecimal.valueOf(quantity));
    }
}
