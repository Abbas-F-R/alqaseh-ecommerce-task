package com.alqaseh.ecommerce.features.order.repository;

import com.alqaseh.ecommerce.features.order.dto.request.OrderFilterRequest;
import com.alqaseh.ecommerce.features.order.entity.Order;
import com.alqaseh.ecommerce.shared.util.LikePattern;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Builds the WHERE clause for the admin order listing; all filtering happens in PostgreSQL. */
public final class OrderSpecification {

    private OrderSpecification() {
    }

    public static Specification<Order> filterBy(OrderFilterRequest filter) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (StringUtils.hasText(filter.getCustomer())) {
                String pattern = LikePattern.contains(filter.getCustomer());
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("customer").get("username")), pattern, LikePattern.ESCAPE));
            }
            if (filter.getCustomerId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("customer").get("id"), filter.getCustomerId()));
            }
            if (filter.getPaymentMethod() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("paymentMethod"), filter.getPaymentMethod()));
            }
            return predicate;
        };
    }
}
