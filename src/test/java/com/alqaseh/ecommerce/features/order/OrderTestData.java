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

    /**
     * One order for {@code customer} with {@code lines} lines paid with {@code method}: the first line is {@code product},
     * every further line is a product of its own (an order has one line per product, a database rule).
     */
    public static Order saveOrder(OrderRepository repository, ProductRepository products, User customer, Product product,
                                  int lines, PaymentMethod method) {
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
            Product line = i == 0 ? product
                    : saveProduct(products, product.getName() + " line " + i + " " + System.nanoTime(),
                            product.getPrice().intValue(), product.getCost().intValue(), 50);
            order.addItem(OrderItem.builder().product(line).productName(line.getName())
                    .unitPrice(line.getPrice()).unitCost(line.getCost()).quantity(1).build());
        }
        return repository.saveAndFlush(order);
    }
}
