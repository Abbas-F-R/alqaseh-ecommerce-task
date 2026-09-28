using System.Data;
using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Shared.Base.dto;

namespace AlQaseh_Ecommerce_API.Features.Orders.Repositories;

/// <summary>An order about to be stored. Amounts are already calculated: Total = Subtotal - Discount.</summary>
public record NewOrder(long CustomerId, decimal Subtotal, decimal Discount, decimal Total, decimal TotalCost, long? DiscountCodeId, string PaymentMethod);

/// <summary>An order line with the product name, price and cost as they were when the order was placed.</summary>
public record NewOrderItem(long ProductId, string ProductName, decimal UnitPrice, decimal UnitCost, int Quantity);

public class InsertedOrder
{
    public long Id { get; set; }
    public DateTime CreatedAt { get; set; }
}

public interface IOrderRepository
{
    Task<InsertedOrder> InsertOrder(NewOrder order, IDbTransaction transaction);

    Task InsertItems(long orderId, IEnumerable<NewOrderItem> items, IDbTransaction transaction);

    /// <summary>One page of the customer's own orders (newest first) with their items, and the total number of orders.</summary>
    Task<(List<CustomerOrderResponse> Data, int TotalCount)> GetMyOrders(long customerId, BaseFilter paging);

    /// <summary>One page of all orders (newest first) with profit and items, filtered by customer and payment method, and the total number of matches.</summary>
    Task<(List<AdminOrderResponse> Data, int TotalCount)> GetAllOrders(OrderFilter filter);
}
