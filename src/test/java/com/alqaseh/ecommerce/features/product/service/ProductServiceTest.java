package com.alqaseh.ecommerce.features.product.service;

import com.alqaseh.ecommerce.features.product.dto.request.ProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.dto.response.AdminProductResponse;
import com.alqaseh.ecommerce.features.product.dto.response.CustomerProductResponse;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.entity.StockStatus;
import com.alqaseh.ecommerce.features.product.mapper.ProductMapper;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.security.UserPrincipal;
import com.alqaseh.ecommerce.infrastructure.user.entity.Role;
import com.alqaseh.ecommerce.shared.audit.entity.AuditAction;
import com.alqaseh.ecommerce.shared.audit.service.AuditService;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import com.alqaseh.ecommerce.shared.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("01923450-0000-7000-8000-000000000001");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuditService auditService;

    @Spy
    private ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);

    @InjectMocks
    private ProductServiceImpl productService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static ProductRequest request(String name) {
        return ProductRequest.builder()
                .name(name)
                .category(ProductCategory.FURNITURE)
                .price(BigDecimal.valueOf(250))
                .cost(BigDecimal.valueOf(140))
                .availableQuantity(15)
                .build();
    }

    private static Product existingProduct() {
        return Product.builder()
                .id(PRODUCT_ID)
                .name("Old Chair")
                .category(ProductCategory.FURNITURE)
                .price(BigDecimal.valueOf(200))
                .cost(BigDecimal.valueOf(120))
                .availableQuantity(10)
                .build();
    }

    private static void authenticateAs(Role role) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), role.name().toLowerCase(), "hash", role,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("Create: a free name creates the product and writes an audit entry")
    void createSucceeds() {
        when(productRepository.existsByName("Office Chair")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(PRODUCT_ID);
            return p;
        });

        Result<AdminProductResponse> result = productService.createProduct(request("Office Chair"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().getId()).isEqualTo(PRODUCT_ID);
        assertThat(result.getValue().getName()).isEqualTo("Office Chair");
        verify(auditService).recordAudit(eq(AuditAction.PRODUCT_CREATED), eq(PRODUCT_ID), any());
    }

    @Test
    @DisplayName("Create: a duplicate name is a business failure and nothing is written")
    void createRejectsDuplicateName() {
        when(productRepository.existsByName("Office Chair")).thenReturn(true);

        Result<AdminProductResponse> result = productService.createProduct(request("Office Chair"));

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NAME_ALREADY_EXISTS);
        verify(productRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Update: changes all fields of the managed entity and audits it")
    void updateSucceeds() {
        Product existing = existingProduct();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));
        when(productRepository.existsByNameAndIdNot("Updated Chair", PRODUCT_ID)).thenReturn(false);

        Result<AdminProductResponse> result = productService.updateProduct(PRODUCT_ID, request("Updated Chair"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(existing.getName()).isEqualTo("Updated Chair");
        assertThat(existing.getPrice()).isEqualByComparingTo("250");
        assertThat(existing.getAvailableQuantity()).isEqualTo(15);
        verify(auditService).recordAudit(eq(AuditAction.PRODUCT_UPDATED), eq(PRODUCT_ID), any());
    }

    @Test
    @DisplayName("Update: unknown product returns PRODUCT_NOT_FOUND")
    void updateUnknownProduct() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        Result<AdminProductResponse> result = productService.updateProduct(PRODUCT_ID, request("Updated Chair"));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Update: renaming to another product's name is rejected and leaves the entity untouched")
    void updateRejectsNameOfAnotherProduct() {
        Product existing = existingProduct();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));
        when(productRepository.existsByNameAndIdNot("Taken", PRODUCT_ID)).thenReturn(true);

        Result<AdminProductResponse> result = productService.updateProduct(PRODUCT_ID, request("Taken"));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NAME_ALREADY_EXISTS);
        assertThat(existing.getName()).isEqualTo("Old Chair");
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("List: admins see cost and exact quantity")
    void listForAdminExposesCostAndQuantity() {
        authenticateAs(Role.ADMIN);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingProduct())));

        PageResponse<?> response = productService.listProducts(new ProductFilterRequest());

        AdminProductResponse row = (AdminProductResponse) response.content().get(0);
        assertThat(row.getCost()).isEqualByComparingTo("120");
        assertThat(row.getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("List: customers get only the stock status, never cost or exact quantity")
    void listForCustomerMasksCostAndQuantity() {
        authenticateAs(Role.CUSTOMER);
        Product lowStock = existingProduct();
        lowStock.setAvailableQuantity(3);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lowStock)));

        PageResponse<?> response = productService.listProducts(new ProductFilterRequest());

        CustomerProductResponse row = (CustomerProductResponse) response.content().get(0);
        assertThat(row.getStockStatus()).isEqualTo(StockStatus.LOW);
        assertThat(row.getName()).isEqualTo("Old Chair");
    }
}
