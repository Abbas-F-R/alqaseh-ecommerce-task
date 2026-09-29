package com.alqaseh.ecommerce.features.order;

import com.alqaseh.ecommerce.features.order.repository.OrderRepository;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who sees which orders and which fields (customer isolation, admin-only profit, filters, pagination). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderVisibilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        User customer1 = userRepository.findByUsername("customer1").orElseThrow();
        User customer2 = userRepository.findByUsername("customer2").orElseThrow();
        Product product = OrderTestData.saveProduct(productRepository, "Monitor", 200, 120, 50);

        OrderTestData.saveOrder(orderRepository, productRepository, customer1, product, 1, PaymentMethod.CREDIT_CARD);
        OrderTestData.saveOrder(orderRepository, productRepository, customer1, product, 2, PaymentMethod.XYZ_WALLET);
        OrderTestData.saveOrder(orderRepository, productRepository, customer2, product, 3, PaymentMethod.CREDIT_CARD);
        entityManager.flush();
        entityManager.clear(); // read back from the database, like a real request would
    }

    @Test
    @WithUserDetails("customer1")
    @DisplayName("A customer sees only their own orders, without cost, profit or customer details")
    void customerSeesOnlyOwnOrders() throws Exception {
        mockMvc.perform(get("/api/orders/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(2)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].profit", everyItem(nullValue())))
                .andExpect(jsonPath("$.data[*].totalCost", everyItem(nullValue())))
                .andExpect(jsonPath("$.data[*].customerId", everyItem(nullValue())))
                .andExpect(jsonPath("$.data[0].items[0].unitCost").doesNotExist());
    }

    @Test
    @WithUserDetails("customer2")
    @DisplayName("Another customer does not see them")
    void otherCustomerSeesOnlyTheirs() throws Exception {
        mockMvc.perform(get("/api/orders/my"))
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.data[0].totalPrice", is(600.0)));
    }

    @Test
    @WithUserDetails("customer1")
    @DisplayName("My orders are paginated in the database: page size and total are honoured, newest first")
    void myOrdersPagination() throws Exception {
        mockMvc.perform(get("/api/orders/my").param("size", "1").param("page", "0"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.totalCount", is(2)))
                .andExpect(jsonPath("$.pagesCount", is(2)))
                .andExpect(jsonPath("$.isLast", is(false)))
                .andExpect(jsonPath("$.data[0].paymentMethod", is("XyzWallet")));
        mockMvc.perform(get("/api/orders/my").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.data[0].paymentMethod", is("CreditCard")))
                .andExpect(jsonPath("$.isLast", is(true)));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Admin sees all orders including customer, cost and profit")
    void adminSeesEverything() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(3)))
                .andExpect(jsonPath("$.data[0].customerUsername", is("customer2")))
                .andExpect(jsonPath("$.data[0].profit", is(240.0)))   // 600 - 360
                .andExpect(jsonPath("$.data[0].totalCost", is(360.0)));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Admin filters by payment method and by customer, in the database")
    void adminFilters() throws Exception {
        mockMvc.perform(get("/api/orders").param("paymentMethod", "CREDIT_CARD"))
                .andExpect(jsonPath("$.totalCount", is(2)));
        mockMvc.perform(get("/api/orders").param("customer", "CUSTOMER2"))
                .andExpect(jsonPath("$.totalCount", is(1)));
        mockMvc.perform(get("/api/orders").param("customer", "customer1").param("paymentMethod", "XYZ_WALLET"))
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.data[0].customerUsername", is("customer1")));
    }

    @Test
    @WithUserDetails("customer1")
    @DisplayName("A customer cannot use the admin listing, an admin cannot use /my")
    void endpointsAreRoleRestricted() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("Admin cannot call the customer-only /my endpoint")
    void adminCannotUseMyOrders() throws Exception {
        mockMvc.perform(get("/api/orders/my")).andExpect(status().isForbidden());
    }
}
