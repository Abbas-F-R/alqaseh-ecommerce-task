package com.alqaseh.ecommerce.features.product.service;

import com.alqaseh.ecommerce.features.product.dto.request.ProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.dto.response.AdminProductResponse;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.mapper.ProductMapper;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.features.product.repository.ProductSpecification;
import com.alqaseh.ecommerce.infrastructure.security.SecurityUtils;
import com.alqaseh.ecommerce.shared.audit.entity.AuditAction;
import com.alqaseh.ecommerce.shared.audit.service.AuditService;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import com.alqaseh.ecommerce.shared.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Input shape (blank name, negative price, ...) is validated by Bean Validation before we get here.
 * What remains are rules that need database state: existence and name uniqueness. The unique index
 * on the products table is the final guard against races; see {@code GlobalExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    // UUID v7 ids are time-ordered, so this is a stable "oldest first" order backed by the primary key.
    private static final Sort LIST_ORDER = Sort.by("id");

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final AuditService auditService;

    @Override
    @Transactional
    public Result<AdminProductResponse> createProduct(ProductRequest request) {
        if (productRepository.existsByName(request.getName())) {
            return Result.failure(ErrorCode.PRODUCT_NAME_ALREADY_EXISTS, request.getName());
        }

        Product product = productRepository.saveAndFlush(productMapper.toEntity(request));

        auditService.recordAudit(AuditAction.PRODUCT_CREATED, product.getId(), describe(product));
        return Result.success(productMapper.toAdminResponse(product));
    }

    @Override
    @Transactional
    public Result<AdminProductResponse> updateProduct(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            return Result.failure(ErrorCode.PRODUCT_NOT_FOUND, id);
        }
        if (productRepository.existsByNameAndIdNot(request.getName(), id)) {
            return Result.failure(ErrorCode.PRODUCT_NAME_ALREADY_EXISTS, request.getName());
        }

        product.updateDetails(request.getName(), request.getCategory(), request.getPrice(), request.getCost(),
                request.getAvailableQuantity());
        // Managed entity: flush now so auditing (updatedAt/updatedBy) and the version check are applied before the response is built.
        productRepository.flush();

        auditService.recordAudit(AuditAction.PRODUCT_UPDATED, id, describe(product));
        return Result.success(productMapper.toAdminResponse(product));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<?> listProducts(ProductFilterRequest filter) {
        Page<Product> page = productRepository.findAll(ProductSpecification.filterBy(filter), filter.toPageable(LIST_ORDER));

        if (SecurityUtils.isCurrentUserAdmin()) {
            return PageResponse.of(page.map(productMapper::toAdminResponse));
        }
        return PageResponse.of(page.map(productMapper::toCustomerResponse));
    }

    private static String describe(Product p) {
        return "name='%s', category=%s, price=%s, cost=%s, quantity=%d"
                .formatted(p.getName(), p.getCategory(), p.getPrice(), p.getCost(), p.getAvailableQuantity());
    }
}
