package com.alqaseh.ecommerce.features.order.service;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.features.discount.repository.DiscountCodeRepository;
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
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import com.alqaseh.ecommerce.shared.audit.entity.AuditAction;
import com.alqaseh.ecommerce.shared.audit.repository.AuditLogRepository;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order creation against a real database and a real transaction manager (deliberately NOT {@code @Transactional}
 * so that commits and rollbacks really happen). A {@code Result.failure} is an ordinary return value that Spring
 * would commit, so these tests prove that a failed order leaves no trace: no stock deducted, no discount burned.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderTransactionIntegrationTest {

    private static final String DECLINED_CARD = "0000000000000000";
    private static final String GOOD_CARD = "4111222233334444";

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private DiscountCodeRepository discountCodeRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserRepository userRepository;

    private User customer;
    private Product laptop;
    private Product mouse;
    private DiscountCode discount;

    @BeforeEach
    void setUp() {
        cleanUp();
        customer = userRepository.findByUsername("customer1").orElseThrow();
        laptop = saveProduct("Laptop", 1000, 700, 10);
        mouse = saveProduct("Mouse", 20, 10, 10);
        discount = discountCodeRepository.save(DiscountCode.builder()
                .code("SAVE50").amount(BigDecimal.valueOf(50)).minimumOrderTotal(BigDecimal.valueOf(100))
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build());
        authenticate(customer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanUp();
    }

    private void cleanUp() {
        orderRepository.deleteAll();
        discountCodeRepository.deleteAll();
        productRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    private Product saveProduct(String name, int price, int cost, int stock) {
        return productRepository.save(Product.builder().name(name).category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(price)).cost(BigDecimal.valueOf(cost)).availableQuantity(stock).build());
    }

    private static void authenticate(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static CreateOrderRequest order(String card, String discountCode, OrderItemRequest... items) {
        return CreateOrderRequest.builder()
                .items(List.of(items))
                .discountCode(discountCode)
                .payment(PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber(card).build())
                .build();
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getAvailableQuantity();
    }

    private boolean discountUsed() {
        return discountCodeRepository.findById(discount.getId()).orElseThrow().isUsed();
    }

    @Test
    @DisplayName("Successful order: stock deducted, discount consumed, order and audit rows persisted")
    void successfulOrderIsPersistedAtomically() {
        Result<CustomerOrderResponse> result = orderService.createOrder(
                order(GOOD_CARD, "SAVE50", new OrderItemRequest(laptop.getId(), 2), new OrderItemRequest(mouse.getId(), 1)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().getTotalPrice()).isEqualByComparingTo("1970"); // 2020 - 50
        assertThat(stockOf(laptop)).isEqualTo(8);
        assertThat(stockOf(mouse)).isEqualTo(9);
        assertThat(discountUsed()).isTrue();
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findAll()).extracting("action")
                .containsExactlyInAnyOrder(AuditAction.ORDER_CREATED, AuditAction.DISCOUNT_APPLIED);
    }

    @Test
    @DisplayName("Declined payment leaves NO trace: stock restored, discount still usable, no order, no audit")
    void declinedPaymentRollsEverythingBack() {
        Result<CustomerOrderResponse> result = orderService.createOrder(
                order(DECLINED_CARD, "SAVE50", new OrderItemRequest(laptop.getId(), 2), new OrderItemRequest(mouse.getId(), 1)));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED);
        assertThat(stockOf(laptop)).as("laptop stock").isEqualTo(10);
        assertThat(stockOf(mouse)).as("mouse stock").isEqualTo(10);
        assertThat(discountUsed()).as("discount must not be burned by a failed payment").isFalse();
        assertThat(orderRepository.count()).isZero();
        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    @DisplayName("A declined payment does not prevent the very same order from succeeding afterwards")
    void customerCanRetryAfterDecline() {
        orderService.createOrder(order(DECLINED_CARD, "SAVE50", new OrderItemRequest(laptop.getId(), 1)));

        Result<CustomerOrderResponse> retry = orderService.createOrder(
                order(GOOD_CARD, "SAVE50", new OrderItemRequest(laptop.getId(), 1)));

        assertThat(retry.isSuccess()).isTrue();
        assertThat(stockOf(laptop)).isEqualTo(9);
    }

    @Test
    @DisplayName("Insufficient stock on the second line does not deduct the first line")
    void noPartialStockDeduction() {
        Result<CustomerOrderResponse> result = orderService.createOrder(
                order(GOOD_CARD, null, new OrderItemRequest(laptop.getId(), 2), new OrderItemRequest(mouse.getId(), 11)));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(stockOf(laptop)).isEqualTo(10);
        assertThat(stockOf(mouse)).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("Invalid discount after valid lines does not deduct any stock")
    void invalidDiscountDoesNotTouchStock() {
        Result<CustomerOrderResponse> result = orderService.createOrder(
                order(GOOD_CARD, "NO-SUCH-CODE", new OrderItemRequest(laptop.getId(), 2)));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.DISCOUNT_NOT_FOUND);
        assertThat(stockOf(laptop)).isEqualTo(10);
    }

    @Test
    @DisplayName("Discount below its minimum order total is rejected and stays usable")
    void discountBelowMinimumStaysUsable() {
        Result<CustomerOrderResponse> result = orderService.createOrder(
                order(GOOD_CARD, "SAVE50", new OrderItemRequest(mouse.getId(), 2))); // 40 < 100 minimum

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.MINIMUM_ORDER_TOTAL_NOT_MET);
        assertThat(discountUsed()).isFalse();
    }

    @Test
    @DisplayName("A used discount code cannot be used by a second order")
    void discountIsSingleUse() {
        assertThat(orderService.createOrder(order(GOOD_CARD, "SAVE50", new OrderItemRequest(laptop.getId(), 1))).isSuccess()).isTrue();

        Result<CustomerOrderResponse> second = orderService.createOrder(
                order(GOOD_CARD, "SAVE50", new OrderItemRequest(laptop.getId(), 1)));

        assertThat(second.getErrorCode()).isEqualTo(ErrorCode.DISCOUNT_ALREADY_USED);
        assertThat(stockOf(laptop)).isEqualTo(9);
    }

    @Test
    @DisplayName("Concurrent orders with the same discount code: exactly one succeeds")
    void concurrentRedemptionOfOneCode() throws InterruptedException {
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    authenticate(customer);
                    Result<CustomerOrderResponse> result = orderService.createOrder(
                            order(GOOD_CARD, "SAVE50", new OrderItemRequest(laptop.getId(), 1)));
                    if (result.isSuccess()) {
                        succeeded.incrementAndGet();
                    }
                } catch (Exception expectedConflict) {
                    // optimistic-lock conflict on the discount (or product) row: the order was rolled back
                } finally {
                    SecurityContextHolder.clearContext();
                    done.countDown();
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(discountUsed()).isTrue();
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(laptop)).isEqualTo(9);
    }
}
