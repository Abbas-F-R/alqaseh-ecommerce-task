package com.alqaseh.ecommerce.features.product.repository;

import com.alqaseh.ecommerce.features.product.dto.request.ProductFilterRequest;
import com.alqaseh.ecommerce.features.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Builds the WHERE clause for product listings; all filtering happens in PostgreSQL. */
public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> filterBy(ProductFilterRequest filter) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (StringUtils.hasText(filter.getName())) {
                String pattern = "%" + filter.getName().trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("name")), pattern));
            }
            if (filter.getCategory() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("category"), filter.getCategory()));
            }
            return predicate;
        };
    }
}
