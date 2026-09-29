package com.alqaseh.ecommerce.features.product.repository;

import com.alqaseh.ecommerce.features.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product>, ProductRepositoryCustom {

    // lower() on both sides matches the functional unique index uq_products_name (LOWER(name)).
    @Query("select count(p) > 0 from Product p where lower(p.name) = lower(:name)")
    boolean existsByName(String name);

    /**
     * Takes stock with one guarded statement: it never oversells, and (unlike changing the entity) it leaves "updated by/at" alone,
     * which record the last edit of the product by an admin. Returns 0 when the stock is not enough.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.availableQuantity = p.availableQuantity - :quantity, p.version = p.version + 1 "
            + "where p.id = :id and p.availableQuantity >= :quantity")
    int reserveStock(UUID id, int quantity);

    @Query("select count(p) > 0 from Product p where lower(p.name) = lower(:name) and p.id <> :id")
    boolean existsByNameAndIdNot(String name, UUID id);
}
