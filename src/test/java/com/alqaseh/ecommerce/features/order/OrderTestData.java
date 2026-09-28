package com.alqaseh.ecommerce.features.order;

import com.alqaseh.ecommerce.features.order.entity.Order;
import com.alqaseh.ecommerce.features.order.entity.OrderItem;
import com.alqaseh.ecommerce.features.order.entity.OrderStatus;
import com.alqaseh.ecommerce.features.order.repository.OrderRepository;
import com.alqaseh.ecommerce.features.payment.entity.PaymentMethod;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.entity.ProductCategory;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.user.entity.User;

import java.math.BigDecimal;

/** Persists already-completed orders directly, bypassing the service (test setup only). */
public final class OrderTestData {

    private OrderTestData() {
    }

    public static Product saveProduct(ProductRepository repository, String name, int price, int cost, int stock) {
        return repository.save(Product.builder().name(name).category(ProductCategory.ELECTRONICS)
                .price(BigDecimal.valueOf(price)).cost(BigDecimal.valueOf(cost)).availableQuantity(stock).build());
    }

    /** One order for {@code customer} with {@code lines} lines of {@code product}, paid with {@code method}. */
    public static Order saveOrder(OrderRepository repository, User customer, Product product, int lines, PaymentMethod method) {
        Order order = Order.builder()
                .customer(customer)
                .subtotalAmount(product.getPrice().multiply(BigDecimal.valueOf(lines)))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(product.getPrice().multiply(BigDecimal.valueOf(lines)))
                .totalCost(product.getCost().multiply(BigDecimal.valueOf(lines)))
                .paymentMethod(method)
                .status(OrderStatus.COMPLETED)
                .build();
        for (int i = 0; i < lines; i++) {
            order.addItem(OrderItem.builder().product(product).productName(product.getName())
                    .unitPrice(product.getPrice()).unitCost(product.getCost()).quantity(1).build());
        }
        return repository.saveAndFlush(order);
    }
}
