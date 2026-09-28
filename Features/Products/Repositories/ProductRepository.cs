using System.Data;
using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using AlQaseh_Ecommerce_API.Shared.Attributes;
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

    public async Task<(List<AdminProductResponse> Data, int TotalCount)> GetAll(ProductFilter filter)
    {
        await using var connection = context.CreateConnection();
        await using var multi = await connection.QueryMultipleAsync(
            "ProductsGetAll",
            new
            {
                filter.PageNumber,
                filter.PageSize,
                Name = string.IsNullOrWhiteSpace(filter.Name) ? null : EscapeLike(filter.Name.Trim()),
                Category = string.IsNullOrWhiteSpace(filter.Category) ? null : filter.Category
            },
            commandType: CommandType.StoredProcedure);

        var totalCount = await multi.ReadFirstAsync<int>();
        var data = (await multi.ReadAsync<AdminProductResponse>()).ToList();
        return (data, totalCount);
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

    // OUTPUT ... INTO: a table with an enabled trigger (the audit trigger) cannot return OUTPUT rows directly.
    public Task<ReservedProduct?> ReserveStock(long productId, int quantity, IDbTransaction transaction) =>
        transaction.Connection!.QueryFirstOrDefaultAsync<ReservedProduct>(
            @"DECLARE @reserved TABLE (Id BIGINT, Name NVARCHAR(150), Price DECIMAL(18,2), Cost DECIMAL(18,2));

              UPDATE Products
              SET AvailableQuantity = AvailableQuantity - @Quantity
              OUTPUT inserted.Id, inserted.Name, inserted.Price, inserted.Cost INTO @reserved
              WHERE Id = @ProductId AND AvailableQuantity >= @Quantity;

              SELECT Id, Name, Price, Cost FROM @reserved;",
            new { ProductId = productId, Quantity = quantity },
            transaction);

    public async Task<bool> Exists(long productId, IDbTransaction transaction) =>
        await transaction.Connection!.ExecuteScalarAsync<bool>(
            "SELECT CASE WHEN EXISTS (SELECT 1 FROM Products WHERE Id = @ProductId) THEN 1 ELSE 0 END",
            new { ProductId = productId },
            transaction);

    /// <summary>Escapes LIKE wildcards so that a name filter is always a plain "contains" search (the procedure uses ESCAPE '\').</summary>
    private static string EscapeLike(string value) =>
        value.Replace("\\", "\\\\").Replace("%", "\\%").Replace("_", "\\_").Replace("[", "\\[");
}
