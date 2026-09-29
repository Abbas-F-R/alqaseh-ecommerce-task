package com.alqaseh.ecommerce.features.product.repository;

import com.alqaseh.ecommerce.features.product.dto.request.CustomerProductFilterRequest;
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
import java.util.List;

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
        CustomerProductFilterRequest filter = CustomerProductFilterRequest.builder()
                .name("Keyboard")
                .limit(10)
                .build();

        List<Product> result = productRepository.findProducts(filter, null, 10);

        // Only "Mechanical Keyboard RGB" contains "Keyboard"
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Mechanical Keyboard RGB");
    }

    @Test
    @DisplayName("Filter by category returns only products in that category")
    void shouldFilterByCategory() {
        CustomerProductFilterRequest filter = CustomerProductFilterRequest.builder()
                .category(ProductCategory.ELECTRONICS)
                .limit(10)
                .build();

        List<Product> result = productRepository.findProducts(filter, null, 10);

        // Electronics: Keyboard and Mouse (2 items)
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Mechanical Keyboard RGB", "Wireless Gaming Mouse");
    }

    @Test
    @DisplayName("Filter by combined name and category")
    void shouldFilterByCombinedNameAndCategory() {
        CustomerProductFilterRequest filter = CustomerProductFilterRequest.builder()
                .name("Desk")
                .category(ProductCategory.FURNITURE)
                .limit(10)
                .build();

        List<Product> result = productRepository.findProducts(filter, null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Standing Desk Pro");
    }

    @Test
    @DisplayName("LIKE wildcards typed in the name filter are matched literally (\"%\" and \"_\" are not wildcards)")
    void shouldMatchWildcardsLiterally() {
        productRepository.save(Product.builder()
                .name("Sale 50% Lamp")
                .category(ProductCategory.FURNITURE)
                .price(BigDecimal.valueOf(40.00))
                .cost(BigDecimal.valueOf(20.00))
                .availableQuantity(3)
                .build());

        assertThat(namesMatching("50%")).containsExactly("Sale 50% Lamp");
        assertThat(namesMatching("%")).containsExactly("Sale 50% Lamp");
        assertThat(namesMatching("_")).isEmpty();
        assertThat(namesMatching("Standing_Desk")).isEmpty();
    }

    private List<String> namesMatching(String name) {
        return productRepository.findProducts(CustomerProductFilterRequest.builder().name(name).build(), null, 10)
                .stream().map(Product::getName).toList();
    }

    @Test
    @DisplayName("Keyset pagination works correctly with filter (limit = 1)")
    void shouldPaginateFilteredResults() {
        CustomerProductFilterRequest filter = CustomerProductFilterRequest.builder()
                .category(ProductCategory.ELECTRONICS)
                .limit(1)
                .build();

        List<Product> page0 = productRepository.findProducts(filter, null, 1);
        assertThat(page0).hasSize(1);
        assertThat(page0.get(0).getName()).isEqualTo("Mechanical Keyboard RGB");

        List<Product> page1 = productRepository.findProducts(filter, page0.get(0).getId(), 1);
        assertThat(page1).hasSize(1);
        assertThat(page1.get(0).getName()).isEqualTo("Wireless Gaming Mouse");
    }
}
