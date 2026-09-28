package com.alqaseh.ecommerce.features.order.service;

import com.alqaseh.ecommerce.features.discount.entity.DiscountCode;
import com.alqaseh.ecommerce.features.discount.service.DiscountService;
import com.alqaseh.ecommerce.features.order.dto.request.CreateOrderRequest;
import com.alqaseh.ecommerce.features.order.dto.request.OrderFilterRequest;
import com.alqaseh.ecommerce.features.order.dto.response.AdminOrderResponse;
import com.alqaseh.ecommerce.features.order.dto.response.CustomerOrderResponse;
import com.alqaseh.ecommerce.features.order.entity.Order;
import com.alqaseh.ecommerce.features.order.entity.OrderItem;
import com.alqaseh.ecommerce.features.order.entity.OrderStatus;
import com.alqaseh.ecommerce.features.order.mapper.OrderMapper;
import com.alqaseh.ecommerce.features.order.repository.OrderRepository;
import com.alqaseh.ecommerce.features.order.repository.OrderSpecification;
import com.alqaseh.ecommerce.features.payment.dto.response.PaymentResult;
import com.alqaseh.ecommerce.features.payment.processor.PaymentProcessor;
import com.alqaseh.ecommerce.features.payment.processor.PaymentProcessorFactory;
import com.alqaseh.ecommerce.features.product.entity.Product;
import com.alqaseh.ecommerce.features.product.repository.ProductRepository;
import com.alqaseh.ecommerce.infrastructure.security.SecurityUtils;
import com.alqaseh.ecommerce.infrastructure.user.repository.UserRepository;
import com.alqaseh.ecommerce.shared.audit.entity.AuditAction;
import com.alqaseh.ecommerce.shared.audit.service.AuditService;
import com.alqaseh.ecommerce.shared.dto.PaginationRequest;
import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import com.alqaseh.ecommerce.shared.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final DiscountService discountService;
    private final PaymentProcessorFactory paymentProcessorFactory;
    private final AuditService auditService;
    private final OrderMapper orderMapper;

    /**
     * Order creation is one atomic unit of work. Request shape (non-empty items, quantity >= 1, payment fields)
     * is already validated by Bean Validation; everything here needs database or domain state.
     *
     * <p>A {@code Result.failure} is a normal return value, which Spring would <em>commit</em>. So no failure may be
     * returned after state was changed unless the transaction is explicitly marked rollback-only.
     * That is why the steps are ordered like this:
     * <ol>
     *   <li>load products (1 query) and validate existence + stock: nothing modified yet;</li>
     *   <li>calculate subtotal, validate the discount, calculate the final total: nothing modified yet;</li>
     *   <li>reserve stock and redeem the discount, then flush: a concurrent order on the same product/code
     *       fails here (409) <em>before</em> the customer is charged;</li>
     *   <li>process payment: a decline rolls the reservation back;</li>
     *   <li>create order + items and the audit rows; commit.</li>
     * </ol>
     */
    @Override
    @Transactional
    public Result<CustomerOrderResponse> createOrder(CreateOrderRequest request) {
        UUID customerId = SecurityUtils.requireCurrentUserId();

        PaymentProcessor paymentProcessor = paymentProcessorFactory.getProcessor(request.getPayment().getMethod()).orElse(null);
        if (paymentProcessor == null) {
            return Result.failure(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        }

        // 1. Load all products in one query (no query per line); repeated lines of a product are merged.
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        request.getItems().forEach(line -> quantities.merge(line.getProductId(), line.getQuantity(), Integer::sum));
        Map<UUID, Product> products = productRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 2. Validate stock and build the order lines with their price/cost snapshots.
        List<OrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Map.Entry<UUID, Integer> line : quantities.entrySet()) {
            Product product = products.get(line.getKey());
            if (product == null) {
                return Result.failure(ErrorCode.PRODUCT_NOT_FOUND, line.getKey());
            }
            if (!product.hasSufficientStock(line.getValue())) {
                return Result.failure(ErrorCode.INSUFFICIENT_STOCK, product.getName());
            }
            OrderItem item = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .unitPrice(product.getPrice())
                    .unitCost(product.getCost())
                    .quantity(line.getValue())
                    .build();
            items.add(item);
            subtotal = subtotal.add(item.getSubtotal());
            totalCost = totalCost.add(item.getTotalCost());
        }

        // 3. Discount (optional) and final total. The discount is never larger than the subtotal (checked by redeem).
        DiscountCode discount = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getDiscountCode() != null && !request.getDiscountCode().isBlank()) {
            Result<DiscountCode> redemption = discountService.redeem(request.getDiscountCode(), subtotal);
            if (redemption.isFailure()) {
                return Result.failure(redemption.getErrorCode(), redemption.getArgs());
            }
            discount = redemption.getValue();
            discountAmount = discount.getAmount();
        }
        BigDecimal total = subtotal.subtract(discountAmount);

        // 4. Reserve stock and flush so version conflicts surface now, before any money moves.
        items.forEach(item -> item.getProduct().deductStock(item.getQuantity()));
        productRepository.flush();

        // 5. Charge. A decline is a business outcome: undo the reservation and report it.
        PaymentResult payment = paymentProcessor.process(total, request.getPayment());
        if (!payment.successful()) {
            log.warn("Payment declined: customerId={}, method={}, reason={}",
                    customerId, request.getPayment().getMethod(), payment.message());
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.failure(ErrorCode.PAYMENT_FAILED, payment.message());
        }

        // 6. Persist the order with its lines, then the audit trail. createdAt/createdBy come from JPA auditing.
        Order order = Order.builder()
                .customer(userRepository.getReferenceById(customerId))
                .subtotalAmount(subtotal)
                .discountAmount(discountAmount)
                .totalAmount(total)
                .totalCost(totalCost)
                .discountCode(discount)
                .paymentMethod(request.getPayment().getMethod())
                .status(OrderStatus.COMPLETED)
                .build();
        items.forEach(order::addItem);
        orderRepository.save(order);

        auditService.recordAudit(AuditAction.ORDER_CREATED, order.getId(),
                "total=%s, subtotal=%s, discount=%s, items=%d, paymentMethod=%s, transactionId=%s"
                        .formatted(total, subtotal, discountAmount, items.size(), order.getPaymentMethod(), payment.transactionId()));
        if (discount != null) {
            auditService.recordAudit(AuditAction.DISCOUNT_APPLIED, discount.getId(),
                    "code=%s, orderId=%s, amount=%s".formatted(discount.getCode(), order.getId(), discountAmount));
        }

        log.info("Order created: orderId={}, customerId={}, total={}", order.getId(), customerId, total);
        return Result.success(orderMapper.toCustomerResponse(order));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerOrderResponse> listMyOrders(PaginationRequest paging) {
        var page = orderRepository.findByCustomerId(SecurityUtils.requireCurrentUserId(), paging.toPageable(NEWEST_FIRST));
        return PageResponse.of(page.map(orderMapper::toCustomerResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> listAllOrders(OrderFilterRequest filter) {
        var page = orderRepository.findAll(OrderSpecification.filterBy(filter), filter.toPageable(NEWEST_FIRST));
        return PageResponse.of(page.map(orderMapper::toAdminResponse));
    }
}
