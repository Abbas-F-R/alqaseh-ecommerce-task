package com.alqaseh.ecommerce.features.product.dto;

import com.alqaseh.ecommerce.features.order.dto.request.CreateOrderRequest;
import com.alqaseh.ecommerce.features.order.dto.request.OrderFilterRequest;
import com.alqaseh.ecommerce.features.order.dto.request.OrderItemRequest;
import com.alqaseh.ecommerce.features.payment.dto.request.PaymentRequest;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.features.product.dto.request.AdminProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.CustomerProductFilterRequest;
import com.alqaseh.ecommerce.features.product.dto.request.ProductRequest;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A control character (a NUL above all: PostgreSQL cannot store it) never reaches the database; a null order line never reaches the service. */
class ControlCharacterValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static ProductRequest product(String name) {
        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setCategory(ProductCategory.FURNITURE);
        request.setPrice(new BigDecimal("10"));
        request.setCost(new BigDecimal("5"));
        request.setAvailableQuantity(1);
        return request;
    }

    @Test
    @DisplayName("Product name: NUL, tab inside, DEL and C1 controls are refused; ordinary text, Arabic and emoji are fine")
    void productName() {
        for (String bad : new String[]{"nul\u0000char", "tab\tinside", "del\u007fchar", "c1\u0085char"}) {
            assertThat(validator.validate(product(bad))).as(bad).isNotEmpty();
        }
        for (String good : new String[]{"Standing Desk", "مكتب خشبي", "Chair 😀", "O'Brien & \"Sons\" 100%"}) {
            assertThat(validator.validate(product(good))).as(good).isEmpty();
        }
    }

    @Test
    @DisplayName("Name and customer filters refuse control characters")
    void filters() {
        AdminProductFilterRequest admin = new AdminProductFilterRequest();
        admin.setName("a\u0000b");
        assertThat(validator.validate(admin)).isNotEmpty();
        CustomerProductFilterRequest customer = new CustomerProductFilterRequest();
        customer.setName("a\u0000b");
        assertThat(validator.validate(customer)).isNotEmpty();
        OrderFilterRequest orders = new OrderFilterRequest();
        orders.setCustomer("a\u0000b");
        assertThat(validator.validate(orders)).isNotEmpty();

        admin.setName("desk 50%");
        assertThat(validator.validate(admin)).isEmpty();
    }

    @Test
    @DisplayName("Order: a null line and a control character in the discount code are refused")
    void order() {
        PaymentRequest payment = PaymentRequest.builder().method(PaymentMethod.CREDIT_CARD).cardNumber("4111111111111111").build();
        List<OrderItemRequest> withNull = new ArrayList<>();
        withNull.add(null);
        assertThat(validator.validate(CreateOrderRequest.builder().items(withNull).payment(payment).build())).isNotEmpty();

        List<OrderItemRequest> ok = List.of(new OrderItemRequest(UUID.randomUUID(), 1));
        assertThat(validator.validate(CreateOrderRequest.builder().items(ok).discountCode("AB\u0000C").payment(payment).build())).isNotEmpty();
        assertThat(validator.validate(CreateOrderRequest.builder().items(ok).discountCode("ABC123").payment(payment).build())).isEmpty();
    }

    @Test
    @DisplayName("Price and cost always carry two decimals, and a third decimal is still refused")
    void moneyScale() {
        ProductRequest request = product("Desk");
        request.setPrice(new BigDecimal("1E+3"));
        request.setCost(new BigDecimal("10.5"));
        assertThat(request.getPrice()).hasToString("1000.00");
        assertThat(request.getCost()).hasToString("10.50");
        assertThat(validator.validate(request)).isEmpty();

        request.setPrice(new BigDecimal("10.005"));
        assertThat(request.getPrice()).hasToString("10.005");
        assertThat(validator.validate(request)).isNotEmpty();
    }
}
