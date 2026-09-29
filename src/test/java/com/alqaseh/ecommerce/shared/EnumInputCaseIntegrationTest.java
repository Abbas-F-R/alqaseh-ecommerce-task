package com.alqaseh.ecommerce.shared;

import com.alqaseh.ecommerce.features.order.OrderTestData;
import com.alqaseh.ecommerce.features.order.repository.OrderRepository;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Enum values are accepted in any letter case; responses always use the canonical upper-case constant. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EnumInputCaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"furniture", "Furniture", "FURNITURE"})
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Request body: category is accepted in any case and returned as furniture")
    void categoryInBody(String category) throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Desk %s","category":"%s","price":100,"cost":60,"availableQuantity":5}"""
                        .formatted(category, category)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category", is("furniture")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"electronics", "Electronics", "ELECTRONICS"})
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Query parameter: category filter is accepted in any case")
    void categoryInQuery(String category) throws Exception {
        Product phone = OrderTestData.saveProduct(productRepository, "Phone", 500, 300, 3);
        productRepository.save(Product.builder().name("Sofa").category(
                com.alqaseh.ecommerce.features.product.entity.ProductCategory.FURNITURE)
                .price(phone.getPrice()).cost(phone.getCost()).availableQuantity(1).build());

        mockMvc.perform(get("/api/customer/products").param("category", category))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Phone")))
                .andExpect(jsonPath("$.data[0].category", is("electronics")))
                .andExpect(jsonPath("$.data[0].stockStatus", is("low")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"credit_card", "Credit_Card", "CREDIT_CARD"})
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Query parameter: paymentMethod filter of the admin order list is accepted in any case")
    void paymentMethodInQuery(String method) throws Exception {
        Product product = OrderTestData.saveProduct(productRepository, "Monitor", 200, 120, 50);
        var customer = userRepository.findByUsername("customer1").orElseThrow();
        OrderTestData.saveOrder(orderRepository, productRepository, customer, product, 1, PaymentMethod.CREDIT_CARD);
        OrderTestData.saveOrder(orderRepository, productRepository, customer, product, 1, PaymentMethod.XYZ_WALLET);

        mockMvc.perform(get("/api/orders").param("paymentMethod", method))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.data[0].paymentMethod", is("CreditCard")));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Request body: payment method is accepted in any case (validation reaches the payment fields)")
    void paymentMethodInBody() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items":[{"productId":"01923450-0000-7000-8000-000000000001","quantity":1}],
                         "payment":{"method":"xyz_wallet","phoneNumber":"+9647800000000"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.validationErrors['payment.walletCredentialsProvided']").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("A value that is not a constant at all is still rejected with 400")
    void unknownValueStillRejected() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"X","category":"toys","price":1,"cost":1,"availableQuantity":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
        mockMvc.perform(get("/api/admin/products").param("category", "toys"))
                .andExpect(status().isBadRequest());
    }
}
