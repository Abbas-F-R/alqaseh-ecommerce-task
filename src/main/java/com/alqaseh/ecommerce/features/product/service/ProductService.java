package com.alqaseh.ecommerce.features.product.service;

import com.alqaseh.ecommerce.features.product.dto.request.ProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.dto.response.AdminProductResponse;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import com.alqaseh.ecommerce.shared.result.Result;

import java.util.UUID;

public interface ProductService {

    Result<AdminProductResponse> createProduct(ProductRequest request);

    Result<AdminProductResponse> updateProduct(UUID id, ProductRequest request);

    /** Admins get {@code AdminProductResponse} rows (cost, exact stock); customers get {@code CustomerProductResponse}. */
    PageResponse<?> listProducts(ProductFilterRequest filter);

}
