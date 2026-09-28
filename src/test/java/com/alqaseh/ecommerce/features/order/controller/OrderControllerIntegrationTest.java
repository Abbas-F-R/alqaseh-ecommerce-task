package com.alqaseh.ecommerce.features.order.controller;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.features.discount.repository.DiscountCodeRepository;
import com.alqaseh.ecommerce.features.order.dto.request.CreateOrderRequest;
import com.alqaseh.ecommerce.features.order.dto.request.OrderItemRequest;
import com.alqaseh.ecommerce.features.order.repository.OrderRepository;
import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.user.entity.Role;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DiscountCodeRepository discountCodeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Product testProduct;
    private DiscountCode testDiscount;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        discountCodeRepository.deleteAll();

        if (!userRepository.existsByUsername("customer1")) {
            userRepository.save(User.builder()
                    .username("customer1")
                    .password("$2a$10$hash")
                    .role(Role.CUSTOMER)
                    .build());
        }

        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(User.builder()
                    .username("admin")
                    .password("$2a$10$hash")
                    .role(Role.ADMIN)
                    .build());
        }

        testProduct = productRepository.save(Product.builder()
                .name("Coffee Maker Pro")
                .category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(100.00))
                .cost(BigDecimal.valueOf(60.00))
                .availableQuantity(20)
                .build());

        testDiscount = discountCodeRepository.save(DiscountCode.builder()
                .code("SPECIAL15")
                .amount(BigDecimal.valueOf(15.00))
                .minimumOrderTotal(BigDecimal.valueOf(50.00))
                .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .used(false)
                .build());
    }

    @Test
    @WithUserDetails("customer1")
    @DisplayName("Customer can successfully place an order with discount and snapshot values")
    void customerCanPlaceOrder() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(new OrderItemRequest(testProduct.getId(), 2))) // 2 * 100 = 200, discount = 15, final = 185
                .discountCode("SPECIAL15")
                .payment(PaymentRequest.builder()
                        .method(PaymentMethod.CREDIT_CARD)
                        .cardNumber("4111222233334444")
                        .build())
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.totalPrice", is(185.0)))
                .andExpect(jsonPath("$.data.discountAmount", is(15.0)))
                .andExpect(jsonPath("$.data.paymentMethod", is("CreditCard")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].productName", is("Coffee Maker Pro")))
                .andExpect(jsonPath("$.data.items[0].unitPrice", is(100.0)));
    }

    @Test
    @WithUserDetails("customer1")
    @DisplayName("Customer cannot place order with insufficient stock")
    void customerCannotOrderMoreThanStock() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(new OrderItemRequest(testProduct.getId(), 50))) // Available is only 20
                .payment(PaymentRequest.builder()
                        .method(PaymentMethod.CREDIT_CARD)
                        .cardNumber("4111222233334444")
                        .build())
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INSUFFICIENT_STOCK")));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Admin cannot place an order (Customer only)")
    void adminCannotPlaceOrder() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(new OrderItemRequest(testProduct.getId(), 1)))
                .payment(PaymentRequest.builder()
                        .method(PaymentMethod.CREDIT_CARD)
                        .cardNumber("4111222233334444")
                        .build())
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Admin can view all orders and net profit calculation")
    void adminCanListAllOrdersWithProfit() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()));
    }

    @Test
    @WithUserDetails("customer1")
    @DisplayName("Customer cannot access admin order listing (403 Forbidden)")
    void customerCannotListAllOrders() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }
}
