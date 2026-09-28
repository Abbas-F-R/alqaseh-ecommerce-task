package com.alqaseh.ecommerce.features.product.controller;

import com.alqaseh.ecommerce.features.product.dto.request.ProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.service.ProductService;
import com.alqaseh.ecommerce.shared.controller.BaseController;
import com.alqaseh.ecommerce.shared.response.ApiResponse;
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
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog operations")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductController extends BaseController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a product (admin only)",
            description = "The name must be unique (case-insensitive).")
    public ResponseEntity<?> createProduct(@Valid @RequestBody ProductRequest request, HttpServletRequest http) {
        return toResponseEntity(productService.createProduct(request), HttpStatus.CREATED, http);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a product (admin only)",
            description = "Replaces all fields; send the complete product.")
    public ResponseEntity<?> updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request,
                                           HttpServletRequest http) {
        return toResponseEntity(productService.updateProduct(id, request), http);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    @Operation(summary = "List products (admin and customer)",
            description = "Optional filters: name, category. Oldest first. Admin sees cost and availableQuantity; customer sees stockStatus.")
    public ResponseEntity<ApiResponse<?>> listProducts(@Valid @ParameterObject ProductFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(productService.listProducts(filter)));
    }
}
