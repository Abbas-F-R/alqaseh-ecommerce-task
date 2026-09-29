package com.alqaseh.ecommerce.features.order;

import com.alqaseh.ecommerce.features.order.dto.request.OrderFilterRequest;
import com.alqaseh.ecommerce.features.order.dto.response.AdminOrderResponse;
import com.alqaseh.ecommerce.features.order.dto.response.CustomerOrderResponse;
import com.alqaseh.ecommerce.features.order.repository.OrderRepository;
import com.alqaseh.ecommerce.features.order.service.OrderService;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.features.product.dto.request.AdminProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.CustomerProductFilterRequest;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.features.product.service.ProductService;
import com.alqaseh.ecommerce.infrastructure.security.UserPrincipal;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import com.alqaseh.ecommerce.shared.dto.PaginationRequest;
import com.alqaseh.ecommerce.shared.response.CursorResponse;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance guard: the number of SQL statements of the read endpoints must not depend on the number of rows
 * (no N+1) and pagination must run in the database (LIMIT), not in memory.
 */
@SpringBootTest
@ActiveProfiles("test")
class QueryCountIntegrationTest {

    private static final int LINES_PER_ORDER = 3;

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductService productService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private User customer;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        customer = userRepository.findByUsername("customer1").orElseThrow();
        User other = userRepository.findByUsername("customer2").orElseThrow();

        Product product = OrderTestData.saveProduct(productRepository, "Phone", 500, 300, 100);
        for (int i = 0; i < 5; i++) {
            OrderTestData.saveOrder(orderRepository, productRepository, customer, product, LINES_PER_ORDER, PaymentMethod.CREDIT_CARD);
        }
        for (int i = 0; i < 2; i++) {
            OrderTestData.saveOrder(orderRepository, productRepository, other, product, LINES_PER_ORDER, PaymentMethod.XYZ_WALLET);
        }
        for (int i = 0; i < 12; i++) {
            OrderTestData.saveProduct(productRepository, "Extra " + i, 10, 5, 5);
        }

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    private static void authenticate(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("GET /api/orders/my: 3 statements for a page (page + count + items of the whole page), no product loaded")
    void myOrdersQueryCount() {
        authenticate(customer);
        statistics.clear();

        PageResponse<CustomerOrderResponse> page = orderService.listMyOrders(PaginationRequest.builder().size(3).build());

        assertThat(page.data()).hasSize(3);
        assertThat(page.totalCount()).isEqualTo(5);
        assertThat(page.data()).allSatisfy(order -> assertThat(order.getItems()).hasSize(LINES_PER_ORDER));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(statistics.getEntityStatistics(Product.class.getName()).getLoadCount())
                .as("order lines must not hydrate their products").isZero();
    }

    @Test
    @DisplayName("GET /api/orders: 3 statements for a page (customer joined into the page query)")
    void adminOrdersQueryCount() {
        authenticate(userRepository.findByUsername("admin").orElseThrow());
        statistics.clear(); // do not count the login lookup above

        PageResponse<AdminOrderResponse> page = orderService.listAllOrders(OrderFilterRequest.builder().size(4).build());

        assertThat(page.data()).hasSize(4);
        assertThat(page.totalCount()).isEqualTo(7);
        assertThat(page.data()).allSatisfy(order -> {
            assertThat(order.getCustomerUsername()).isNotBlank();
            assertThat(order.getItems()).hasSize(LINES_PER_ORDER);
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(statistics.getEntityStatistics(Product.class.getName()).getLoadCount()).isZero();
    }

    @Test
    @DisplayName("GET /api/admin/products: page/offset pagination costs 2 statements (count + page)")
    void adminProductListingQueryCount() {
        authenticate(userRepository.findByUsername("admin").orElseThrow());
        statistics.clear(); // do not count the login lookup above

        var page = productService.listProductsForAdmin(AdminProductFilterRequest.builder().size(5).build());

        assertThat(page.data()).hasSize(5);
        assertThat(page.totalCount()).isGreaterThan(5);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("GET /api/customer/products: 1 statement (no count query) and limit + 1 rows are loaded to detect hasMore")
    void customerProductListingQueryCount() {
        authenticate(userRepository.findByUsername("customer1").orElseThrow());
        statistics.clear(); // do not count the login lookup above

        CursorResponse<?> page = productService.listProductsForCustomer(CustomerProductFilterRequest.builder().limit(5).build());

        assertThat(page.data()).hasSize(5);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityStatistics(Product.class.getName()).getLoadCount()).isEqualTo(6);
    }
}
