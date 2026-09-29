using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Shared.Base.dto;

namespace AlQaseh_Ecommerce_API.Features.Products.Services;

public interface IProductService
{
    /// <summary>Admin view of a page of products (offset pagination with totals): cost and exact available quantity.</summary>
    Task<ServiceResult<List<AdminProductResponse>>> GetAll(ServiceRequest<AdminProductFilter> request);

    /// <summary>Customer view of a cursor page of products: stock status instead of the exact quantity, no cost.</summary>
    Task<ServiceResult<CursorResponse<CustomerProductResponse>>> GetAllForCustomer(ServiceRequest<CustomerProductFilter> request);

    Task<ServiceResult<AdminProductResponse>> Add(ServiceRequest<ProductForm> request);

    Task<ServiceResult<AdminProductResponse>> Update(long id, ServiceRequest<ProductForm> request);
}
