package com.alqaseh.ecommerce.features.product.repository;

import com.alqaseh.ecommerce.features.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product>, ProductRepositoryCustom {

    // lower() on both sides matches the functional unique index uq_products_name (LOWER(name)).
    @Query("select count(p) > 0 from Product p where lower(p.name) = lower(:name)")
    boolean existsByName(String name);

    @Query("select count(p) > 0 from Product p where lower(p.name) = lower(:name) and p.id <> :id")
    boolean existsByNameAndIdNot(String name, UUID id);
}
