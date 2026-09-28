package com.alqaseh.ecommerce.features.order.service;

import com.alqaseh.ecommerce.features.order.dto.request.CreateOrderRequest;
import com.alqaseh.ecommerce.features.order.dto.request.OrderFilterRequest;
import com.alqaseh.ecommerce.features.order.dto.response.AdminOrderResponse;
import com.alqaseh.ecommerce.features.order.dto.response.CustomerOrderResponse;
import com.alqaseh.ecommerce.shared.dto.PaginationRequest;
import com.alqaseh.ecommerce.shared.response.PageResponse;
import com.alqaseh.ecommerce.shared.result.Result;

public interface OrderService {

    /** Places an order for the authenticated customer. */
    Result<CustomerOrderResponse> createOrder(CreateOrderRequest request);

    /** The authenticated customer's own orders, newest first. */
    PageResponse<CustomerOrderResponse> listMyOrders(PaginationRequest paging);

    /** All orders (admin), newest first. */
    PageResponse<AdminOrderResponse> listAllOrders(OrderFilterRequest filter);
}
