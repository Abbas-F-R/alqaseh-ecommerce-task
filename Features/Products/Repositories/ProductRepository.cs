using System.Data;
using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Features.Products.Utils;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using Dapper;

namespace AlQaseh_Ecommerce_API.Features.Products.Repositories;

[Scoped]
public class ProductRepository(DapperContext context) : IProductRepository
{
    public async Task<AdminProductResponse?> Get(long id)
    {
        await using var connection = context.CreateConnection();
        return await connection.QueryFirstOrDefaultAsync<AdminProductResponse>(
            "ProductsGetById", new { Id = id }, commandType: CommandType.StoredProcedure);
    }

    public async Task<(List<AdminProductResponse> Data, int TotalCount)> GetPage(AdminProductFilter filter)
    {
        await using var connection = context.CreateConnection();
        await using var multi = await connection.QueryMultipleAsync(
            "ProductsGetAll",
            new
            {
                filter.PageNumber,
                filter.PageSize,
                Name = string.IsNullOrWhiteSpace(filter.Name) ? null : SqlLike.Escape(filter.Name.Trim()),
                Category = string.IsNullOrWhiteSpace(filter.Category) ? null : filter.Category
            },
            commandType: CommandType.StoredProcedure);

        var totalCount = await multi.ReadFirstAsync<int>();
        var data = (await multi.ReadAsync<AdminProductResponse>()).ToList();
        return (data, totalCount);
    }

    public async Task<CursorResponse<AdminProductResponse>> GetCursorPage(CustomerProductFilter filter)
    {
        ProductCursor.TryDecode(filter.Cursor, out var afterId);

        await using var connection = context.CreateConnection();
        var rows = (await connection.QueryAsync<AdminProductResponse>(
            "ProductsGetCursor",
            new
            {
                Limit = filter.Limit,
                AfterId = afterId > 0 ? (long?)afterId : null,
                Name = string.IsNullOrWhiteSpace(filter.Name) ? null : SqlLike.Escape(filter.Name.Trim()),
                Category = string.IsNullOrWhiteSpace(filter.Category) ? null : filter.Category
            },
            commandType: CommandType.StoredProcedure)).ToList();

        var hasMore = rows.Count > filter.Limit;
        var data = hasMore ? rows.Take(filter.Limit).ToList() : rows;
        var nextCursor = hasMore && data.Count > 0 ? ProductCursor.Encode(data.Last().Id) : null;

        return new CursorResponse<AdminProductResponse>(data, nextCursor, hasMore);
    }

    public async Task<bool> NameExists(string name, long? excludeId = null)
    {
        await using var connection = context.CreateConnection();
        return await connection.ExecuteScalarAsync<bool>(
            "ProductsNameExists", new { Name = name, ExcludeId = excludeId }, commandType: CommandType.StoredProcedure);
    }

    public async Task<AdminProductResponse> Add(ProductForm form, long userId)
    {
        await using var connection = context.CreateConnection();
        return await connection.QuerySingleAsync<AdminProductResponse>(
            "ProductsInsert",
            new { form.Name, form.Category, form.Price, form.Cost, form.AvailableQuantity, CreatedBy = userId },
            commandType: CommandType.StoredProcedure);
    }

    public async Task<AdminProductResponse?> Update(long id, ProductForm form, long userId)
    {
        await using var connection = context.CreateConnection();
        return await connection.QueryFirstOrDefaultAsync<AdminProductResponse>(
            "ProductsUpdate",
            new { Id = id, form.Name, form.Category, form.Price, form.Cost, form.AvailableQuantity, UpdatedBy = userId },
            commandType: CommandType.StoredProcedure);
    }

    public Task<ReservedProduct?> ReserveStock(long productId, int quantity, IDbTransaction transaction) =>
        transaction.Connection!.QueryFirstOrDefaultAsync<ReservedProduct>(
            "ProductsReserveStock",
            new { ProductId = productId, Quantity = quantity },
            transaction,
            commandType: CommandType.StoredProcedure);

    public async Task<bool> Exists(long productId, IDbTransaction transaction) =>
        await transaction.Connection!.ExecuteScalarAsync<bool>(
            "ProductsCheckExists",
            new { ProductId = productId },
            transaction,
            commandType: CommandType.StoredProcedure);
}
