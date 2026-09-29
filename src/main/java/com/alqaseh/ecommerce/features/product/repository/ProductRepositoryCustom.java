package com.alqaseh.ecommerce.features.product.repository;

import com.alqaseh.ecommerce.features.product.dto.request.ProductCriteria;
import com.alqaseh.ecommerce.features.product.entity.Product;

import java.util.List;
import java.util.UUID;

public interface ProductRepositoryCustom {

    List<Product> findProducts(ProductCriteria filter, UUID afterId, int limit);
}
