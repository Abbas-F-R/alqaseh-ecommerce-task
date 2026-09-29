package com.alqaseh.ecommerce.features.product.repository;

import com.alqaseh.ecommerce.features.product.dto.request.ProductCriteria;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.shared.util.LikePattern;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

/** Builds the WHERE clause for product listings; all filtering happens in PostgreSQL. */
public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> filterBy(ProductCriteria filter) {
        return filterBy(filter, null);
    }

    public static Specification<Product> filterBy(ProductCriteria filter, UUID afterId) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (StringUtils.hasText(filter.getName())) {
                String pattern = LikePattern.contains(filter.getName());
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("name")), pattern, LikePattern.ESCAPE));
            }
            if (filter.getCategory() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("category"), filter.getCategory()));
            }
            if (afterId != null) {
                predicate = cb.and(predicate, cb.greaterThan(root.get("id"), afterId));
            }
            return predicate;
        };
    }
}
