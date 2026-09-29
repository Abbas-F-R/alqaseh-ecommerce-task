using System.Data;
using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Shared.Base.dto;

namespace AlQaseh_Ecommerce_API.Features.Products.Repositories;

/// <summary>A product whose stock was just reduced by an order, with the price and cost that were in effect at that moment.</summary>
public record ReservedProduct(long Id, string Name, decimal Price, decimal Cost);

public interface IProductRepository
{
    Task<AdminProductResponse?> Get(long id);

    /// <summary>Admin list: one page (offset pagination, ordered by id) and the total number of matches.</summary>
    Task<(List<AdminProductResponse> Data, int TotalCount)> GetPage(AdminProductFilter filter);

    /// <summary>Customer list: one keyset page (ordered by id; no OFFSET, no count).</summary>
    Task<CursorResponse<AdminProductResponse>> GetCursorPage(CustomerProductFilter filter);

    /// <summary>Whether another product already has this name (case-insensitive), optionally ignoring one product.</summary>
    Task<bool> NameExists(string name, long? excludeId = null);

    Task<AdminProductResponse> Add(ProductForm form, long userId);

    /// <summary>Replaces the product's fields and stamps UpdatedBy / UpdatedAt; null when the product does not exist.</summary>
    Task<AdminProductResponse?> Update(long id, ProductForm form, long userId);

    /// <summary>
    /// Atomically takes <paramref name="quantity"/> units out of stock and returns the product's price and cost;
    /// null when the product does not exist or has less than that in stock. Does not touch UpdatedBy / UpdatedAt.
    /// </summary>
    Task<ReservedProduct?> ReserveStock(long productId, int quantity, IDbTransaction transaction);

    Task<bool> Exists(long productId, IDbTransaction transaction);
}
