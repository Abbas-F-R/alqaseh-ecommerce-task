package com.alqaseh.ecommerce.features.product.repository;

import com.alqaseh.ecommerce.features.product.dto.request.ProductCriteria;
import com.alqaseh.ecommerce.features.product.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    public List<Product> findProducts(ProductCriteria filter, UUID afterId, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> root = cq.from(Product.class);

        var spec = ProductSpecification.filterBy(filter, afterId);
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        cq.orderBy(cb.asc(root.get("id")));

        return entityManager.createQuery(cq)
                .setMaxResults(limit)
                .getResultList();
    }
}
