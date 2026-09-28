package com.alqaseh.ecommerce.features.order.mapper;

import com.alqaseh.ecommerce.features.order.dto.response.AdminOrderResponse;
import com.alqaseh.ecommerce.features.order.dto.response.CustomerOrderResponse;
import com.alqaseh.ecommerce.features.order.dto.response.OrderItemResponse;
import com.alqaseh.ecommerce.features.order.entity.Order;
import com.alqaseh.ecommerce.features.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Pure field mapping; profit and line subtotals are computed by the entities. */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    // product is a lazy reference; reading only its id does not load the product row.
    @Mapping(target = "productId", source = "product.id")
    OrderItemResponse toItemResponse(OrderItem item);

    @Mapping(target = "totalPrice", source = "totalAmount")
    @Mapping(target = "purchaseDate", source = "createdAt")
    CustomerOrderResponse toCustomerResponse(Order order);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerUsername", source = "customer.username")
    @Mapping(target = "purchaseDate", source = "createdAt")
    AdminOrderResponse toAdminResponse(Order order);
}
