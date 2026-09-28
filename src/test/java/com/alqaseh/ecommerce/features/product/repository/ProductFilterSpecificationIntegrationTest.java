package com.alqaseh.ecommerce.features.product.repository;

import com.alqaseh.ecommerce.features.product.dto.request.ProductFilterRequest;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductFilterSpecificationIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        // 1. Electronic Keyboard (Active)
        productRepository.save(Product.builder()
                .name("Mechanical Keyboard RGB")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(120.00))
                .cost(BigDecimal.valueOf(70.00))
                .availableQuantity(15)
                .build());

        // 2. Electronic Mouse (Active)
        productRepository.save(Product.builder()
                .name("Wireless Gaming Mouse")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(60.00))
                .cost(BigDecimal.valueOf(30.00))
                .availableQuantity(25)
                .build());

        // 3. Furniture Desk (Active)
        productRepository.save(Product.builder()
                .name("Standing Desk Pro")
                .category(ProductCategory.FURNITURE)
                .price(BigDecimal.valueOf(350.00))
                .cost(BigDecimal.valueOf(200.00))
                .availableQuantity(5)
                .build());
    }

    @Test
    @DisplayName("Filter by partial name (case-insensitive) matches only products containing it")
    void shouldFilterByPartialName() {
        ProductFilterRequest filter = ProductFilterRequest.builder()
                .name("Keyboard")
                .page(0)
                .size(10)
                .build();

        Specification<Product> spec = ProductSpecification.filterBy(filter);
        Page<Product> result = productRepository.findAll(spec, filter.toPageable(Sort.by("name")));

        // Only "Mechanical Keyboard RGB" contains "Keyboard"
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Mechanical Keyboard RGB");
    }

    @Test
    @DisplayName("Filter by category returns only products in that category")
    void shouldFilterByCategory() {
        ProductFilterRequest filter = ProductFilterRequest.builder()
                .category(ProductCategory.ELECTRONICS)
                .page(0)
                .size(10)
                .build();

        Specification<Product> spec = ProductSpecification.filterBy(filter);
        Page<Product> result = productRepository.findAll(spec, filter.toPageable(Sort.by("name")));

        // Electronics: Keyboard and Mouse (2 items)
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Mechanical Keyboard RGB", "Wireless Gaming Mouse");
    }

    @Test
    @DisplayName("Filter by combined name and category")
    void shouldFilterByCombinedNameAndCategory() {
        ProductFilterRequest filter = ProductFilterRequest.builder()
                .name("Desk")
                .category(ProductCategory.FURNITURE)
                .page(0)
                .size(10)
                .build();

        Specification<Product> spec = ProductSpecification.filterBy(filter);
        Page<Product> result = productRepository.findAll(spec, filter.toPageable(Sort.by("name")));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Standing Desk Pro");
    }

    @Test
    @DisplayName("Pagination works correctly with filter (page size = 1)")
    void shouldPaginateFilteredResults() {
        ProductFilterRequest page0Filter = ProductFilterRequest.builder()
                .category(ProductCategory.ELECTRONICS)
                .page(0)
                .size(1)
                .build();

        Page<Product> page0 = productRepository.findAll(
                ProductSpecification.filterBy(page0Filter), page0Filter.toPageable(Sort.by("name")));

        assertThat(page0.getTotalElements()).isEqualTo(2);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page0.getContent()).hasSize(1);
        assertThat(page0.getContent().get(0).getName()).isEqualTo("Mechanical Keyboard RGB");

        ProductFilterRequest page1Filter = ProductFilterRequest.builder()
                .category(ProductCategory.ELECTRONICS)
                .page(1)
                .size(1)
                .build();

        Page<Product> page1 = productRepository.findAll(
                ProductSpecification.filterBy(page1Filter), page1Filter.toPageable(Sort.by("name")));

        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getName()).isEqualTo("Wireless Gaming Mouse");
    }
}
