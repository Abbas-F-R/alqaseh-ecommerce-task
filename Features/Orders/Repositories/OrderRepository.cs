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
            @"DECLARE @inserted TABLE (Id BIGINT, CreatedAt DATETIME2(3));

              INSERT INTO Orders (CustomerId, SubtotalAmount, DiscountAmount, TotalAmount, TotalCost, DiscountCodeId, PaymentMethod)
              OUTPUT inserted.Id, inserted.CreatedAt INTO @inserted
              VALUES (@CustomerId, @Subtotal, @Discount, @Total, @TotalCost, @DiscountCodeId, @PaymentMethod);

              SELECT Id, CreatedAt FROM @inserted;",
            order,
            transaction);

    public Task InsertItems(long orderId, IEnumerable<NewOrderItem> items, IDbTransaction transaction) =>
        transaction.Connection!.ExecuteAsync(
            @"INSERT INTO OrderItems (OrderId, ProductId, ProductName, UnitPrice, UnitCost, Quantity)
              VALUES (@OrderId, @ProductId, @ProductName, @UnitPrice, @UnitCost, @Quantity)",
            items.Select(i => new { OrderId = orderId, i.ProductId, i.ProductName, i.UnitPrice, i.UnitCost, i.Quantity }),
            transaction);

    public async Task<(List<CustomerOrderResponse> Data, int TotalCount)> GetMyOrders(long customerId, BaseFilter paging)
    {
        await using var connection = context.CreateConnection();

        await using var multi = await connection.QueryMultipleAsync(
            @"SELECT COUNT(*) FROM Orders WHERE CustomerId = @CustomerId;

              SELECT Id, TotalAmount AS TotalPrice, PaymentMethod, CreatedAt AS PurchaseDate, DiscountAmount
              FROM Orders
              WHERE CustomerId = @CustomerId
              ORDER BY CreatedAt DESC, Id DESC
              OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;",
            new { CustomerId = customerId, Offset = (paging.PageNumber - 1) * paging.PageSize, paging.PageSize });

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

        var conditions = new List<string>();
        var parameters = new DynamicParameters();

        if (!string.IsNullOrWhiteSpace(filter.Customer))
        {
            conditions.Add(@"u.UserName LIKE @Customer ESCAPE '\'");
            parameters.Add("Customer", "%" + EscapeLike(filter.Customer.Trim()) + "%");
        }

        if (filter.CustomerId.HasValue)
        {
            conditions.Add("o.CustomerId = @CustomerId");
            parameters.Add("CustomerId", filter.CustomerId.Value);
        }

        if (PaymentMethods.Normalize(filter.PaymentMethod) is { } paymentMethod)
        {
            conditions.Add("o.PaymentMethod = @PaymentMethod");
            parameters.Add("PaymentMethod", paymentMethod);
        }

        parameters.Add("Offset", (filter.PageNumber - 1) * filter.PageSize);
        parameters.Add("PageSize", filter.PageSize);

        var where = conditions.Count == 0 ? string.Empty : "WHERE " + string.Join(" AND ", conditions);

        await using var multi = await connection.QueryMultipleAsync(
            $@"SELECT COUNT(*)
               FROM Orders o
               INNER JOIN Users u ON u.Id = o.CustomerId
               {where};

               SELECT o.Id, o.CustomerId, u.UserName AS CustomerUsername,
                      o.SubtotalAmount, o.DiscountAmount, o.TotalAmount, o.TotalCost,
                      o.TotalAmount - o.TotalCost AS Profit,
                      o.PaymentMethod, o.CreatedAt AS PurchaseDate
               FROM Orders o
               INNER JOIN Users u ON u.Id = o.CustomerId
               {where}
               ORDER BY o.CreatedAt DESC, o.Id DESC
               OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;",
            parameters);

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
            @"SELECT OrderId, ProductId, ProductName, UnitPrice, Quantity, UnitPrice * Quantity AS Subtotal
              FROM OrderItems
              WHERE OrderId IN @OrderIds
              ORDER BY Id",
            new { OrderIds = ids });

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

    private static string EscapeLike(string value) =>
        value.Replace("\\", "\\\\").Replace("%", "\\%").Replace("_", "\\_").Replace("[", "\\[");

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
