using System.Data;
using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Payments;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using Dapper;

namespace AlQaseh_Ecommerce_API.Features.Orders.Repositories;

[Scoped]
public class OrderRepository(DapperContext context) : IOrderRepository
{
    public Task<InsertedOrder> InsertOrder(NewOrder order, IDbTransaction transaction) =>
        transaction.Connection!.QuerySingleAsync<InsertedOrder>(
            "OrdersInsert",
            order,
            transaction,
            commandType: CommandType.StoredProcedure);

    public Task InsertItems(long orderId, IEnumerable<NewOrderItem> items, IDbTransaction transaction) =>
        transaction.Connection!.ExecuteAsync(
            "OrderItemsInsert",
            items.Select(i => new { OrderId = orderId, i.ProductId, i.ProductName, i.UnitPrice, i.UnitCost, i.Quantity }),
            transaction,
            commandType: CommandType.StoredProcedure);

    public async Task<(List<CustomerOrderResponse> Data, int TotalCount)> GetMyOrders(long customerId, BaseFilter paging)
    {
        await using var connection = context.CreateConnection();

        await using var multi = await connection.QueryMultipleAsync(
            "OrdersGetByCustomer",
            new
            {
                CustomerId = customerId,
                paging.PageNumber,
                paging.PageSize
            },
            commandType: CommandType.StoredProcedure);

        var totalCount = await multi.ReadFirstAsync<int>();
        var orders = (await multi.ReadAsync<CustomerOrderResponse>()).ToList();

        var items = await LoadItems(connection, orders.Select(o => o.Id));
        foreach (var order in orders)
            order.Items = items.GetValueOrDefault(order.Id) ?? [];

        return (orders, totalCount);
    }

    public async Task<(List<AdminOrderResponse> Data, int TotalCount)> GetAllOrders(OrderFilter filter)
    {
        await using var connection = context.CreateConnection();

        var paymentMethod = PaymentMethods.Normalize(filter.PaymentMethod);

        await using var multi = await connection.QueryMultipleAsync(
            "OrdersGetAll",
            new
            {
                Customer = string.IsNullOrWhiteSpace(filter.Customer) ? null : SqlLike.Escape(filter.Customer.Trim()),
                filter.CustomerId,
                PaymentMethod = paymentMethod,
                filter.PageNumber,
                filter.PageSize
            },
            commandType: CommandType.StoredProcedure);

        var totalCount = await multi.ReadFirstAsync<int>();
        var orders = (await multi.ReadAsync<AdminOrderResponse>()).ToList();

        var items = await LoadItems(connection, orders.Select(o => o.Id));
        foreach (var order in orders)
            order.Items = items.GetValueOrDefault(order.Id) ?? [];

        return (orders, totalCount);
    }

    /// <summary>Loads the items of a whole page of orders with one query.</summary>
    private static async Task<Dictionary<long, List<OrderItemResponse>>> LoadItems(IDbConnection connection, IEnumerable<long> orderIds)
    {
        var ids = orderIds.ToList();
        if (ids.Count == 0)
            return [];

        var rows = await connection.QueryAsync<OrderItemRow>(
            "OrderItemsGetByOrderIds",
            new { OrderIdsCsv = string.Join(",", ids) },
            commandType: CommandType.StoredProcedure);

        return rows.GroupBy(r => r.OrderId).ToDictionary(
            g => g.Key,
            g => g.Select(r => new OrderItemResponse
            {
                ProductId = r.ProductId,
                ProductName = r.ProductName,
                UnitPrice = r.UnitPrice,
                Quantity = r.Quantity,
                Subtotal = r.Subtotal
            }).ToList());
    }

    private sealed class OrderItemRow
    {
        public long OrderId { get; set; }
        public long ProductId { get; set; }
        public string ProductName { get; set; } = string.Empty;
        public decimal UnitPrice { get; set; }
        public int Quantity { get; set; }
        public decimal Subtotal { get; set; }
    }
}
