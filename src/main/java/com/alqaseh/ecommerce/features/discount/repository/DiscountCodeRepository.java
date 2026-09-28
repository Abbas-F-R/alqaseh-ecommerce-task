package com.alqaseh.ecommerce.features.discount.repository;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, UUID> {

    Optional<DiscountCode> findByCode(String code);
}
