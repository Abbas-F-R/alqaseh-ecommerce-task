package com.alqaseh.ecommerce.features.product.entity;

import com.alqaseh.ecommerce.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

/**
 * Name uniqueness (case-insensitive) is enforced by the unique index
 * {@code uq_products_name} on {@code LOWER(name)} (Flyway), which cannot be expressed in JPA annotations.
 */
@Entity
@Table(name = "products", indexes = @Index(name = "idx_products_category_id", columnList = "category, id"))
@Check(constraints = "price > 0 AND cost >= 0 AND cost <= price AND available_quantity >= 0 AND available_quantity <= 1000000")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Product extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ProductCategory category;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    /** Optimistic lock: concurrent orders on the same product cannot oversell. Left null for new entities. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public void deductStock(int quantity) {
        this.availableQuantity -= quantity;
    }

    public boolean hasSufficientStock(int quantity) {
        return this.availableQuantity >= quantity;
    }

    public StockStatus getStockStatus() {
        return StockStatus.fromQuantity(availableQuantity);
    }

    public void updateDetails(String name, ProductCategory category, BigDecimal price, BigDecimal cost, Integer availableQuantity) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.cost = cost;
        this.availableQuantity = availableQuantity;
    }

}
