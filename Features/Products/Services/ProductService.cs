using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Features.Products.Repositories;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;

namespace AlQaseh_Ecommerce_API.Features.Products.Services;

/// <summary>
/// Product catalog rules: unique names, the fixed categories (normalized to lowercase) and the role-dependent list views.
/// </summary>
[Scoped]
public class ProductService(IProductRepository productRepository) : IProductService
{
    public async Task<ServiceResult<List<AdminProductResponse>>> GetAll(ServiceRequest<AdminProductFilter> request)
    {
        var (data, totalCount) = await productRepository.GetPage(Normalize(request.Dto));
        return ServiceResult<List<AdminProductResponse>>.PagedOk(data, totalCount);
    }

    public async Task<ServiceResult<CursorResponse<CustomerProductResponse>>> GetAllForCustomer(ServiceRequest<CustomerProductFilter> request)
    {
        var cursorResult = await productRepository.GetCursorPage(Normalize(request.Dto));

        var customerData = cursorResult.Data.Select(p => new CustomerProductResponse
        {
            Id = p.Id,
            Name = p.Name,
            Category = p.Category,
            Price = p.Price,
            StockStatus = StockStatuses.FromQuantity(p.AvailableQuantity)
        }).ToList();

        var response = new CursorResponse<CustomerProductResponse>(customerData, cursorResult.NextCursor, cursorResult.HasMore);
        return ServiceResult<CursorResponse<CustomerProductResponse>>.Ok(response);
    }

    public async Task<ServiceResult<AdminProductResponse>> Add(ServiceRequest<ProductForm> request)
    {
        var form = Normalize(request.Dto);

        if (await productRepository.NameExists(form.Name))
            return ServiceResult<AdminProductResponse>.Failure(Messages.ProductNameAlreadyExists);

        try
        {
            return ServiceResult<AdminProductResponse>.Ok(await productRepository.Add(form, request.UserId));
        }
        catch (Exception ex) when (SqlErrors.IsUniqueViolation(ex))
        {
            // Two admins created the same name at the same moment: the unique index decided.
            return ServiceResult<AdminProductResponse>.Failure(Messages.ProductNameAlreadyExists);
        }
    }

    public async Task<ServiceResult<AdminProductResponse>> Update(long id, ServiceRequest<ProductForm> request)
    {
        var form = Normalize(request.Dto);

        if (await productRepository.Get(id) is null)
            return ServiceResult<AdminProductResponse>.Failure(Messages.ProductNotFound);

        if (await productRepository.NameExists(form.Name, excludeId: id))
            return ServiceResult<AdminProductResponse>.Failure(Messages.ProductNameAlreadyExists);

        try
        {
            var updated = await productRepository.Update(id, form, request.UserId);
            return updated is null
                ? ServiceResult<AdminProductResponse>.Failure(Messages.ProductNotFound)
                : ServiceResult<AdminProductResponse>.Ok(updated);
        }
        catch (Exception ex) when (SqlErrors.IsUniqueViolation(ex))
        {
            return ServiceResult<AdminProductResponse>.Failure(Messages.ProductNameAlreadyExists);
        }
    }

    /// <summary>Trims the name and lowercases the category (the validator already guaranteed it is one of the four).</summary>
    private static ProductForm Normalize(ProductForm form) => new()
    {
        Name = form.Name.Trim(),
        Category = ProductCategories.Normalize(form.Category)!,
        Price = form.Price,
        Cost = form.Cost,
        AvailableQuantity = form.AvailableQuantity
    };

    private static AdminProductFilter Normalize(AdminProductFilter filter) => new()
    {
        PageNumber = filter.PageNumber,
        PageSize = filter.PageSize,
        Name = string.IsNullOrWhiteSpace(filter.Name) ? null : filter.Name.Trim(),
        Category = ProductCategories.Normalize(filter.Category)
    };

    private static CustomerProductFilter Normalize(CustomerProductFilter filter) => new()
    {
        Limit = filter.Limit,
        Cursor = string.IsNullOrWhiteSpace(filter.Cursor) ? null : filter.Cursor.Trim(),
        Name = string.IsNullOrWhiteSpace(filter.Name) ? null : filter.Name.Trim(),
        Category = ProductCategories.Normalize(filter.Category)
    };
}
