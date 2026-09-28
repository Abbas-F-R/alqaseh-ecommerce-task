package com.alqaseh.ecommerce.features.order.service;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.features.discount.service.DiscountService;
import com.alqaseh.ecommerce.features.order.dto.request.CreateOrderRequest;
import com.alqaseh.ecommerce.features.order.dto.request.OrderItemRequest;
import com.alqaseh.ecommerce.features.order.dto.response.CustomerOrderResponse;
import com.alqaseh.ecommerce.features.order.entity.Order;
import com.alqaseh.ecommerce.features.order.mapper.OrderMapper;
import com.alqaseh.ecommerce.features.order.repository.OrderRepository;
import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import com.alqaseh.ecommerce.features.payment.dto.response.PaymentResult;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.features.payment.processor.PaymentProcessor;
import com.alqaseh.ecommerce.features.payment.processor.PaymentProcessorFactory;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.security.UserPrincipal;
import com.alqaseh.ecommerce.infrastructure.user.entity.Role;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import com.alqaseh.ecommerce.shared.audit.entity.AuditAction;
import com.alqaseh.ecommerce.shared.audit.service.AuditService;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Business flow of order creation with all collaborators mocked. The transactional guarantees
 * (rollback on declined payment, no partial stock deduction) are verified against a real
 * database in {@code OrderTransactionIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("01923450-0000-7000-8000-000000000010");
    private static final UUID PRODUCT_ID_1 = UUID.fromString("01923450-0000-7000-8000-000000000001");
    private static final UUID PRODUCT_ID_2 = UUID.fromString("01923450-0000-7000-8000-000000000002");
    private static final UUID DISCOUNT_ID = UUID.fromString("01923450-0000-7000-8000-000000000011");
    private static final UUID ORDER_ID = UUID.fromString("01923450-0000-7000-8000-000000000100");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DiscountService discountService;
    @Mock
    private PaymentProcessorFactory paymentProcessorFactory;
    @Mock
    private PaymentProcessor paymentProcessor;
    @Mock
    private AuditService auditService;
    @Spy
    private OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product keyboard;
    private Product mouse;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = new UserPrincipal(CUSTOMER_ID, "customer1", "hash", Role.CUSTOMER, List.of(() -> "ROLE_CUSTOMER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        keyboard = product(PRODUCT_ID_1, "Keyboard", 50, 30, 10);
        mouse = product(PRODUCT_ID_2, "Mouse", 25, 15, 10);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Product product(UUID id, String name, int price, int cost, int stock) {
        return Product.builder()
                .id(id)
                .name(name)
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(price))
                .cost(BigDecimal.valueOf(cost))
                .availableQuantity(stock)
                .build();
    }

    private static CreateOrderRequest order(String discountCode, OrderItemRequest... items) {
        return CreateOrderRequest.builder()
                .items(List.of(items))
                .discountCode(discountCode)
                .payment(PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber("4111222233334444").build())
                .build();
    }

    private void paymentSucceeds() {
        when(paymentProcessorFactory.getProcessor(PaymentMethod.CREDIT_CARD)).thenReturn(Optional.of(paymentProcessor));
        when(paymentProcessor.process(any(), any())).thenReturn(PaymentResult.success("CC-1"));
    }

    private void orderIsSavedWithId() {
        when(userRepository.getReferenceById(CUSTOMER_ID)).thenReturn(User.builder().id(CUSTOMER_ID).username("customer1").build());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            saved.setId(ORDER_ID);
            return saved;
        });
    }

    @Test
    @DisplayName("Happy path: totals, discount, profit inputs, stock deduction, audit entries")
    void createsOrderWithDiscount() {
        DiscountCode discount = DiscountCode.builder().id(DISCOUNT_ID).code("DISC10").amount(BigDecimal.valueOf(10))
                .minimumOrderTotal(BigDecimal.valueOf(50)).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(productRepository.findAllById(any())).thenReturn(List.of(keyboard, mouse));
        when(discountService.redeem("DISC10", BigDecimal.valueOf(125))).thenReturn(Result.success(discount));
        paymentSucceeds();
        orderIsSavedWithId();

        Result<CustomerOrderResponse> result = orderService.createOrder(order("DISC10",
                new OrderItemRequest(PRODUCT_ID_1, 2),   // 2 x 50 = 100 (cost 60)
                new OrderItemRequest(PRODUCT_ID_2, 1))); // 1 x 25 = 25  (cost 15)

        assertThat(result.isSuccess()).isTrue();
        CustomerOrderResponse response = result.getValue();
        assertThat(response.getId()).isEqualTo(ORDER_ID);
        assertThat(response.getTotalPrice()).isEqualByComparingTo("115");   // 125 - 10
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("10");
        assertThat(response.getItems()).hasSize(2);

        assertThat(keyboard.getAvailableQuantity()).isEqualTo(8);
        assertThat(mouse.getAvailableQuantity()).isEqualTo(9);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getSubtotalAmount()).isEqualByComparingTo("125");
        assertThat(saved.getValue().getTotalCost()).isEqualByComparingTo("75");
        assertThat(saved.getValue().getProfit()).isEqualByComparingTo("40");   // 115 - 75
        verify(paymentProcessor).process(eq(BigDecimal.valueOf(115)), any(PaymentRequest.class));
        verify(auditService).recordAudit(eq(AuditAction.ORDER_CREATED), eq(ORDER_ID), anyString());
        verify(auditService).recordAudit(eq(AuditAction.DISCOUNT_APPLIED), eq(DISCOUNT_ID), anyString());
    }

    @Test
    @DisplayName("Products are loaded with a single batch query, and repeated lines of one product are merged")
    void loadsProductsInOneQueryAndMergesRepeatedLines() {
        when(productRepository.findAllById(any())).thenReturn(List.of(keyboard));
        paymentSucceeds();
        orderIsSavedWithId();

        Result<CustomerOrderResponse> result = orderService.createOrder(order(null,
                new OrderItemRequest(PRODUCT_ID_1, 2),
                new OrderItemRequest(PRODUCT_ID_1, 3)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().getItems()).hasSize(1);
        assertThat(result.getValue().getItems().get(0).getQuantity()).isEqualTo(5);
        assertThat(keyboard.getAvailableQuantity()).isEqualTo(5);
        verify(productRepository, times(1)).findAllById(Set.of(PRODUCT_ID_1));
    }

    @Test
    @DisplayName("Repeated lines are validated against stock as one quantity")
    void mergedQuantityIsCheckedAgainstStock() {
        keyboard.setAvailableQuantity(4);
        when(productRepository.findAllById(any())).thenReturn(List.of(keyboard));
        when(paymentProcessorFactory.getProcessor(PaymentMethod.CREDIT_CARD)).thenReturn(Optional.of(paymentProcessor));

        Result<CustomerOrderResponse> result = orderService.createOrder(order(null,
                new OrderItemRequest(PRODUCT_ID_1, 2),
                new OrderItemRequest(PRODUCT_ID_1, 3))); // 5 requested, 4 in stock

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(keyboard.getAvailableQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("Unknown product: PRODUCT_NOT_FOUND, no payment")
    void rejectsUnknownProduct() {
        when(productRepository.findAllById(any())).thenReturn(List.of(keyboard));
        when(paymentProcessorFactory.getProcessor(PaymentMethod.CREDIT_CARD)).thenReturn(Optional.of(paymentProcessor));

        Result<CustomerOrderResponse> result = orderService.createOrder(order(null,
                new OrderItemRequest(PRODUCT_ID_1, 1),
                new OrderItemRequest(PRODUCT_ID_2, 1)));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verifyNoInteractions(paymentProcessor, orderRepository, auditService);
    }

    @Test
    @DisplayName("Insufficient stock on any line: INSUFFICIENT_STOCK, nothing deducted, no payment, no order")
    void rejectsInsufficientStockWithoutSideEffects() {
        mouse.setAvailableQuantity(1);
        when(productRepository.findAllById(any())).thenReturn(List.of(keyboard, mouse));
        when(paymentProcessorFactory.getProcessor(PaymentMethod.CREDIT_CARD)).thenReturn(Optional.of(paymentProcessor));

        Result<CustomerOrderResponse> result = orderService.createOrder(order(null,
                new OrderItemRequest(PRODUCT_ID_1, 2),
                new OrderItemRequest(PRODUCT_ID_2, 5)));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(keyboard.getAvailableQuantity()).as("first line must not be deducted").isEqualTo(10);
        verifyNoInteractions(paymentProcessor, orderRepository, auditService);
    }

    @Test
    @DisplayName("Invalid discount: its failure is returned as is, nothing deducted, no payment")
    void propagatesDiscountFailure() {
        when(productRepository.findAllById(any())).thenReturn(List.of(keyboard));
        when(paymentProcessorFactory.getProcessor(PaymentMethod.CREDIT_CARD)).thenReturn(Optional.of(paymentProcessor));
        when(discountService.redeem(anyString(), any())).thenReturn(Result.failure(ErrorCode.DISCOUNT_EXPIRED, "OLD"));

        Result<CustomerOrderResponse> result = orderService.createOrder(order("OLD", new OrderItemRequest(PRODUCT_ID_1, 1)));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.DISCOUNT_EXPIRED);
        assertThat(keyboard.getAvailableQuantity()).isEqualTo(10);
        verifyNoInteractions(paymentProcessor, orderRepository, auditService);
    }

    @Test
    @DisplayName("Unsupported payment method is rejected before anything is loaded")
    void rejectsUnsupportedPaymentMethod() {
        when(paymentProcessorFactory.getProcessor(PaymentMethod.CREDIT_CARD)).thenReturn(Optional.empty());

        Result<CustomerOrderResponse> result = orderService.createOrder(order(null, new OrderItemRequest(PRODUCT_ID_1, 1)));

        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        verify(productRepository, never()).findAllById(any());
    }
}
