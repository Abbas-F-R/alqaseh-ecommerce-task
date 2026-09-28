package com.alqaseh.ecommerce.features.order.repository;

import com.alqaseh.ecommerce.features.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * Both listings fetch a page of orders in one query; the items of the whole page are then loaded together by
 * Hibernate's batch fetching ({@code default_batch_fetch_size}). The items are deliberately NOT join-fetched:
 * a collection fetch join combined with paging forces Hibernate to paginate in memory.
 */
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    /** The customer's own view does not expose the customer, so nothing extra is fetched. */
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    /** Admin view shows the customer's username: join-fetch it (a to-one join, safe with paging). */
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);
}
