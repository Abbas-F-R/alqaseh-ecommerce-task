package com.alqaseh.ecommerce.features.order.controller;

import com.alqaseh.ecommerce.features.order.dto.request.CreateOrderRequest;
import com.alqaseh.ecommerce.features.order.dto.request.OrderFilterRequest;
import com.alqaseh.ecommerce.features.order.dto.response.AdminOrderResponse;
import com.alqaseh.ecommerce.features.order.dto.response.CustomerOrderResponse;
import com.alqaseh.ecommerce.features.order.service.OrderService;
import com.alqaseh.ecommerce.shared.controller.BaseController;
import com.alqaseh.ecommerce.shared.dto.PaginationRequest;
import com.alqaseh.ecommerce.shared.response.ApiResponse;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order placement and history")
@SecurityRequirement(name = "Bearer Authentication")
public class OrderController extends BaseController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Place an order (customer only)",
            description = "All or nothing. CREDIT_CARD needs cardNumber; XYZ_WALLET needs phoneNumber and walletPassword. "
                    + "Card 0000000000000000 and wallet password wrongPassword are declined.")
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderRequest request, HttpServletRequest http) {
        return toResponseEntity(orderService.createOrder(request), HttpStatus.CREATED, http);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List my orders (customer only)",
            description = "Your own orders, newest first.")
    public ResponseEntity<PageResponse<CustomerOrderResponse>> listMyOrders(
            @Valid @ParameterObject PaginationRequest paging) {
        return ResponseEntity.ok(orderService.listMyOrders(paging));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all orders (admin only)",
            description = "Newest first, with cost and profit. Optional filters: customer, customerId, paymentMethod.")
    public ResponseEntity<PageResponse<AdminOrderResponse>> listAllOrders(
            @Valid @ParameterObject OrderFilterRequest filter) {
        return ResponseEntity.ok(orderService.listAllOrders(filter));
    }
}
