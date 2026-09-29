using AlQaseh_Ecommerce_API.Features.Products.Controllers;
using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Features.Products.Services;
using AlQaseh_Ecommerce_API.Features.Products.Utils;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using AlQaseh_Ecommerce_API.Tests.TestSupport;
using FluentAssertions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Products.Controllers;

public class ProductControllerTests
{
    private readonly Mock<IProductService> _service = new();

    private ProductController Controller(string role) => new ProductController(_service.Object).WithUser(1, "user", role);

    private static string? RolesOf(string method) =>
        typeof(ProductController).GetMethod(method)!.GetCustomAttributes(typeof(AuthorizeAttribute), false)
            .Cast<AuthorizeAttribute>().SingleOrDefault()?.Roles;

    [Fact]
    public void TheWholeController_RequiresASignedInUser() =>
        typeof(ProductController).GetCustomAttributes(typeof(AuthorizeAttribute), true).Should().NotBeEmpty();

    [Fact]
    public void CreateAndUpdate_AreAdminOnly()
    {
        RolesOf(nameof(ProductController.CreateProduct)).Should().Be("Admin");
        RolesOf(nameof(ProductController.UpdateProduct)).Should().Be("Admin");
    }

    [Fact]
    public void EachRoleHasItsOwnListEndpoint()
    {
        RolesOf(nameof(ProductController.ListProductsForAdmin)).Should().Be("Admin");
        RolesOf(nameof(ProductController.ListProductsForCustomer)).Should().Be("Customer");
    }

    [Fact]
    public void ThereIsNoDeleteEndpoint() =>
        typeof(ProductController).GetMethods().Should().NotContain(m => m.Name.Contains("Delete"));

    [Fact]
    public async Task Create_Returns201WithTheProduct()
    {
        var product = new AdminProductResponse { Id = 3, Name = "Chair", Category = "furniture", CreatedBy = 1 };
        _service.Setup(s => s.Add(It.IsAny<ServiceRequest<ProductForm>>())).ReturnsAsync(ServiceResult<AdminProductResponse>.Ok(product));

        var result = await Controller("Admin").CreateProduct(new ProductForm { Name = "Chair" });

        var created = result.Result.Should().BeAssignableTo<ObjectResult>().Subject;
        created.StatusCode.Should().Be(201);
        created.Value.Should().BeSameAs(product);
    }

    [Fact]
    public async Task Create_WithADuplicateName_Returns409WithTheErrorCode()
    {
        _service.Setup(s => s.Add(It.IsAny<ServiceRequest<ProductForm>>()))
            .ReturnsAsync(ServiceResult<AdminProductResponse>.Failure(Messages.ProductNameAlreadyExists));

        var result = await Controller("Admin").CreateProduct(new ProductForm());

        var (status, code, _) = result.Result!.Problem();
        (status, code).Should().Be((409, "ProductNameAlreadyExists"));
    }

    [Fact]
    public async Task Update_OfAMissingProduct_Returns404_InArabicWhenAsked()
    {
        _service.Setup(s => s.Update(7, It.IsAny<ServiceRequest<ProductForm>>()))
            .ReturnsAsync(ServiceResult<AdminProductResponse>.Failure(Messages.ProductNotFound));

        var result = await new ProductController(_service.Object).WithUser(1, "admin", "Admin", "ar").UpdateProduct(7, new ProductForm());

        var (status, code, detail) = result.Result!.Problem();
        (status, code).Should().Be((404, "ProductNotFound"));
        detail.Should().Be("المنتج المطلوب غير موجود.");
    }

    [Fact]
    public async Task Update_PassesTheIdAndTheCallerToTheService()
    {
        _service.Setup(s => s.Update(7, It.IsAny<ServiceRequest<ProductForm>>()))
            .ReturnsAsync(ServiceResult<AdminProductResponse>.Ok(new AdminProductResponse { Id = 7 }));

        var result = await Controller("Admin").UpdateProduct(7, new ProductForm());

        result.Result.Should().BeOfType<OkObjectResult>();
        _service.Verify(s => s.Update(7, It.Is<ServiceRequest<ProductForm>>(r => r.UserId == 1 && r.Role == "Admin")), Times.Once);
    }

    [Fact]
    public async Task List_AsAdmin_ReturnsTheAdminViewInThePageEnvelope()
    {
        _service.Setup(s => s.GetAll(It.IsAny<ServiceRequest<AdminProductFilter>>()))
            .ReturnsAsync(ServiceResult<List<AdminProductResponse>>.PagedOk([new AdminProductResponse { Id = 1, AvailableQuantity = 3 }], 21));

        var result = await Controller("Admin").ListProductsForAdmin(new AdminProductFilter { PageNumber = 1, PageSize = 10 });

        var page = result.Result.Should().BeOfType<OkObjectResult>().Subject.Value.Should().BeOfType<Response<AdminProductResponse>>().Subject;
        (page.CurrentPage, page.TotalCount, page.PagesCount, page.IsLast).Should().Be((1, 21, 3, false));
        _service.Verify(s => s.GetAllForCustomer(It.IsAny<ServiceRequest<CustomerProductFilter>>()), Times.Never);
    }

    [Fact]
    public async Task List_AsCustomer_ReturnsTheCustomerView()
    {
        _service.Setup(s => s.GetAllForCustomer(It.IsAny<ServiceRequest<CustomerProductFilter>>()))
            .ReturnsAsync(ServiceResult<CursorResponse<CustomerProductResponse>>.Ok(
                new CursorResponse<CustomerProductResponse>([new CustomerProductResponse { Id = 1, StockStatus = "low" }], null, false)));

        var result = await Controller("Customer").ListProductsForCustomer(new CustomerProductFilter());

        var page = result.Result.Should().BeOfType<OkObjectResult>().Subject.Value.Should().BeOfType<CursorResponse<CustomerProductResponse>>().Subject;
        page.Data.Should().HaveCount(1);
        page.NextCursor.Should().BeNull();
        page.HasMore.Should().BeFalse();
        _service.Verify(s => s.GetAll(It.IsAny<ServiceRequest<AdminProductFilter>>()), Times.Never);
    }
}
