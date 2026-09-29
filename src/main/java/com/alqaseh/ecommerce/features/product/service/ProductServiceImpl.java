package com.alqaseh.ecommerce.features.product.service;

import com.alqaseh.ecommerce.features.product.dto.request.AdminProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.CustomerProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.dto.response.AdminProductResponse;
import com.alqaseh.ecommerce.features.product.dto.response.CustomerProductResponse;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.mapper.ProductMapper;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.features.product.repository.ProductSpecification;
import com.alqaseh.ecommerce.features.product.util.ProductCursor;
import com.alqaseh.ecommerce.shared.audit.entity.AuditAction;
import com.alqaseh.ecommerce.shared.audit.service.AuditService;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.response.CursorResponse;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import com.alqaseh.ecommerce.shared.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Input shape (blank name, negative price, ...) is validated by Bean Validation before we get here.
 * What remains are rules that need database state: existence and name uniqueness. The unique index
 * on the products table is the final guard against races; see {@code GlobalExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

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
    public PageResponse<AdminProductResponse> listProductsForAdmin(AdminProductFilterRequest filter) {
        var page = productRepository.findAll(ProductSpecification.filterBy(filter), filter.toPageable(Sort.by("id")));
        return PageResponse.of(page.map(productMapper::toAdminResponse));
    }

    /** One keyset page: limit + 1 rows tell whether there is a next page, so no count query and no OFFSET are needed. */
    @Override
    @Transactional(readOnly = true)
    public CursorResponse<CustomerProductResponse> listProductsForCustomer(CustomerProductFilterRequest filter) {
        int limit = filter.getLimit();
        List<Product> products = productRepository.findProducts(filter, ProductCursor.decode(filter.getCursor()), limit + 1);

        boolean hasMore = products.size() > limit;
        List<Product> pageItems = hasMore ? products.subList(0, limit) : products;
        String nextCursor = hasMore ? ProductCursor.encode(pageItems.get(pageItems.size() - 1).getId()) : null;

        return new CursorResponse<>(pageItems.stream().map(productMapper::toCustomerResponse).toList(), nextCursor, hasMore);
    }

    private static String describe(Product p) {
        return "name='%s', category=%s, price=%s, cost=%s, quantity=%d"
                .formatted(p.getName(), p.getCategory(), p.getPrice(), p.getCost(), p.getAvailableQuantity());
    }
}
