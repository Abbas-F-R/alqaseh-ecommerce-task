package com.alqaseh.ecommerce.features.product.service;

import com.alqaseh.ecommerce.features.product.dto.request.AdminProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.CustomerProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.dto.response.AdminProductResponse;
import com.alqaseh.ecommerce.features.product.dto.response.CustomerProductResponse;
import com.alqaseh.ecommerce.shared.response.CursorResponse;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import com.alqaseh.ecommerce.shared.result.Result;

import java.util.UUID;

public interface ProductService {

    Result<AdminProductResponse> createProduct(ProductRequest request);

    Result<AdminProductResponse> updateProduct(UUID id, ProductRequest request);

    /** Admins get {@code AdminProductResponse} rows (cost, exact stock); customers get {@code CustomerProductResponse}. */
    /** Admin view (page/offset pagination with totals): cost, exact quantity and audit fields. */
    PageResponse<AdminProductResponse> listProductsForAdmin(AdminProductFilterRequest filter);

    /** Customer view (cursor pagination): stock status instead of the exact quantity, no cost. */
    CursorResponse<CustomerProductResponse> listProductsForCustomer(CustomerProductFilterRequest filter);

}
