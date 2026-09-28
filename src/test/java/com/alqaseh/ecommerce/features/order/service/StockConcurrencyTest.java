package com.alqaseh.ecommerce.features.order.service;

import com.alqaseh.ecommerce.features.order.dto.request.CreateOrderRequest;
import com.alqaseh.ecommerce.features.order.dto.request.OrderItemRequest;
import com.alqaseh.ecommerce.features.order.dto.response.CustomerOrderResponse;
import com.alqaseh.ecommerce.features.order.repository.OrderRepository;
import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.security.UserPrincipal;
import com.alqaseh.ecommerce.infrastructure.user.entity.Role;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import com.alqaseh.ecommerce.shared.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();

        testCustomer = userRepository.findByUsername("concurrency_customer").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("concurrency_customer")
                        .password("$2a$10$hash")
                        .role(Role.CUSTOMER)
                        .build())
        );

        // Product with exactly 10 units in stock
        testProduct = productRepository.save(Product.builder()
                .name("High Demand Laptop")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(1200.00))
                .cost(BigDecimal.valueOf(800.00))
                .availableQuantity(10)
                .build());
    }

    @Test
    @DisplayName("Concurrent order placement must never result in negative stock (overselling prevention)")
    void concurrentOrdersMustPreventOverselling() throws InterruptedException {
        int numberOfThreads = 10;
        int quantityPerOrder = 2;
        // Total requested = 10 * 2 = 20 units, but only 10 units are in stock!
        // At most 5 orders can succeed. Stock must NEVER be negative.

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger failedOrders = new AtomicInteger(0);
        AtomicInteger exceptionOrders = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    // Wait for all threads to be ready to maximize race condition
                    startLatch.await();

                    // Each thread sets up authentication context
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    UserPrincipal principal = UserPrincipal.create(testCustomer);
                    context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                    SecurityContextHolder.setContext(context);

                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .items(List.of(new OrderItemRequest(testProduct.getId(), quantityPerOrder)))
                            .payment(PaymentRequest.builder()
                                    .method(PaymentMethod.CREDIT_CARD)
                                    .cardNumber("4111222233334444")
                                    .build())
                            .build();

                    Result<CustomerOrderResponse> result = orderService.createOrder(request);
                    if (result.isSuccess()) {
                        successfulOrders.incrementAndGet();
                    } else {
                        failedOrders.incrementAndGet();
                    }
                } catch (Exception e) {
                    // OptimisticLockingFailureException or concurrency collision caught at transaction commit
                    exceptionOrders.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release all threads at once
        boolean finishedInTime = doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(finishedInTime).isTrue();

        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();

        // 1. Stock must never be negative (No overselling!)
        assertThat(updatedProduct.getAvailableQuantity())
                .as("Stock should never drop below zero")
                .isGreaterThanOrEqualTo(0);

        // 2. Successful orders multiplied by quantityPerOrder + remaining stock must equal initial stock
        int unitsSold = successfulOrders.get() * quantityPerOrder;
        assertThat(unitsSold + updatedProduct.getAvailableQuantity())
                .as("Units sold + remaining stock must exactly equal initial stock (10)")
                .isEqualTo(10);

        // 3. At most 5 orders could have succeeded since 5 * 2 = 10
        assertThat(successfulOrders.get())
                .as("Successful orders must be <= 5")
                .isLessThanOrEqualTo(5);

        // 4. Total attempts handled (success + business failure + concurrency conflict) == numberOfThreads
        assertThat(successfulOrders.get() + failedOrders.get() + exceptionOrders.get())
                .isEqualTo(numberOfThreads);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }
}
