package com.alqaseh.ecommerce.shared.entity;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.features.discount.repository.DiscountCodeRepository;
import com.alqaseh.ecommerce.features.order.entity.Order;
import com.alqaseh.ecommerce.features.order.entity.OrderItem;
import com.alqaseh.ecommerce.features.order.entity.OrderStatus;
import com.alqaseh.ecommerce.features.order.repository.OrderRepository;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.user.entity.Role;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatabaseConstraintsIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DiscountCodeRepository discountCodeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testUser = userRepository.saveAndFlush(User.builder()
                .username("constraint_tester")
                .password("hash")
                .role(Role.CUSTOMER)
                .build());

        testProduct = productRepository.saveAndFlush(Product.builder()
                .name("Valid Product")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(100.00))
                .cost(BigDecimal.valueOf(50.00))
                .availableQuantity(10)
                .build());
    }

    @Test
    @DisplayName("Product with negative price violates database CHECK constraint")
    void shouldRejectNegativeProductPrice() {
        Product invalid = Product.builder()
                .name("Negative Price Product")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(-10.00))
                .cost(BigDecimal.valueOf(50.00))
                .availableQuantity(5)
                .build();

        assertThatThrownBy(() -> {
            productRepository.saveAndFlush(invalid);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Product with negative cost violates database CHECK constraint")
    void shouldRejectNegativeProductCost() {
        Product invalid = Product.builder()
                .name("Negative Cost Product")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(100.00))
                .cost(BigDecimal.valueOf(-5.00))
                .availableQuantity(5)
                .build();

        assertThatThrownBy(() -> {
            productRepository.saveAndFlush(invalid);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Product with negative availableQuantity violates database CHECK constraint")
    void shouldRejectNegativeAvailableQuantity() {
        Product invalid = Product.builder()
                .name("Negative Qty Product")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(100.00))
                .cost(BigDecimal.valueOf(50.00))
                .availableQuantity(-1)
                .build();

        assertThatThrownBy(() -> {
            productRepository.saveAndFlush(invalid);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("OrderItem with zero or negative quantity violates database CHECK constraint")
    void shouldRejectZeroOrderItemQuantity() {
        Order order = Order.builder()
                .customer(testUser)
                .subtotalAmount(BigDecimal.valueOf(100.00))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(100.00))
                .totalCost(BigDecimal.valueOf(50.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(OrderStatus.COMPLETED)
                .build();

        OrderItem item = OrderItem.builder()
                .product(testProduct)
                .productName(testProduct.getName())
                .unitPrice(BigDecimal.valueOf(100.00))
                .unitCost(BigDecimal.valueOf(50.00))
                .quantity(0) // Invalid: quantity must be > 0
                .build();

        order.addItem(item);

        assertThatThrownBy(() -> {
            orderRepository.saveAndFlush(order);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DiscountCode with negative amount violates database CHECK constraint")
    void shouldRejectNegativeDiscountAmount() {
        DiscountCode invalid = DiscountCode.builder()
                .code("NEG_DISCOUNT")
                .amount(BigDecimal.valueOf(-15.00))
                .minimumOrderTotal(BigDecimal.valueOf(50.00))
                .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .used(false)
                .build();

        assertThatThrownBy(() -> {
            discountCodeRepository.saveAndFlush(invalid);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DiscountCode with negative minimumOrderTotal violates database CHECK constraint")
    void shouldRejectNegativeMinimumOrderTotal() {
        DiscountCode invalid = DiscountCode.builder()
                .code("NEG_MIN")
                .amount(BigDecimal.valueOf(10.00))
                .minimumOrderTotal(BigDecimal.valueOf(-10.00))
                .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .used(false)
                .build();

        assertThatThrownBy(() -> {
            discountCodeRepository.saveAndFlush(invalid);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Order with negative total_amount violates database CHECK constraint")
    void shouldRejectNegativeOrderTotalAmount() {
        Order invalid = Order.builder()
                .customer(testUser)
                .subtotalAmount(BigDecimal.valueOf(100.00))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(-50.00))
                .totalCost(BigDecimal.valueOf(40.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(OrderStatus.COMPLETED)
                .build();

        assertThatThrownBy(() -> {
            orderRepository.saveAndFlush(invalid);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
