package com.alqaseh.ecommerce.features.product.controller;

import com.alqaseh.ecommerce.features.product.dto.request.AdminProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.CustomerProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.service.ProductService;
import com.alqaseh.ecommerce.shared.controller.BaseController;
import com.alqaseh.ecommerce.shared.response.ApiResponse;
import com.alqaseh.ecommerce.features.product.dto.response.AdminProductResponse;
import com.alqaseh.ecommerce.features.product.dto.response.CustomerProductResponse;
import com.alqaseh.ecommerce.shared.response.CursorResponse;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog operations")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductController extends BaseController {

    private final ProductService productService;

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a product (admin only)",
            description = "The name must be unique (case-insensitive).")
    public ResponseEntity<?> createProduct(@Valid @RequestBody ProductRequest request, HttpServletRequest http) {
        return toResponseEntity(productService.createProduct(request), HttpStatus.CREATED, http);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a product (admin only)",
            description = "Replaces all fields; send the complete product.")
    public ResponseEntity<?> updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request,
                                           HttpServletRequest http) {
        return toResponseEntity(productService.updateProduct(id, request), http);
    }

    @GetMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List products (admin only)",
            description = "Page/offset pagination (page from 0, size 1-50) with totals. Optional filters: name, category. Ordered by id. Shows cost, the exact availableQuantity and who created / last updated each product.")
    public ResponseEntity<PageResponse<AdminProductResponse>> listProductsForAdmin(@Valid @ParameterObject AdminProductFilterRequest filter) {
        return ResponseEntity.ok(productService.listProductsForAdmin(filter));
    }

    @GetMapping("/customer/products")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List products (customer only)",
            description = "Keyset pagination (limit, cursor). Optional filters: name, category. Oldest first. Shows stockStatus (low, limited, available) instead of the exact quantity; never the cost.")
    public ResponseEntity<CursorResponse<CustomerProductResponse>> listProductsForCustomer(@Valid @ParameterObject CustomerProductFilterRequest filter) {
        return ResponseEntity.ok(productService.listProductsForCustomer(filter));
    }
}
