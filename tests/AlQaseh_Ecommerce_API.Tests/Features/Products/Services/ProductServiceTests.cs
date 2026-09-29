using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Features.Products.Repositories;
using AlQaseh_Ecommerce_API.Features.Products.Services;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using FluentAssertions;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Products.Services;

public class ProductServiceTests
{
    private readonly Mock<IProductRepository> _repo = new();
    private readonly ProductService _service;

    public ProductServiceTests() => _service = new ProductService(_repo.Object);

    private static AdminProductResponse Product(long id, string name, int quantity, string category = "furniture") =>
        new() { Id = id, Name = name, Category = category, Price = 100, Cost = 60, AvailableQuantity = quantity, CreatedBy = 1 };

    private static ServiceRequest<ProductForm> Form(string name = "Office Chair", string category = "FURNITURE", long userId = 1) =>
        new(new ProductForm { Name = name, Category = category, Price = 100, Cost = 60, AvailableQuantity = 5 }, userId);

    // ---- create

    [Fact]
    public async Task Add_StoresTrimmedNameAndLowercaseCategoryAndRecordsTheCreator()
    {
        ProductForm? stored = null;
        _repo.Setup(r => r.Add(It.IsAny<ProductForm>(), 9)).Callback<ProductForm, long>((f, _) => stored = f)
            .ReturnsAsync(Product(1, "Office Chair", 5));

        var result = await _service.Add(Form("  Office Chair  ", "FuRnItUrE", userId: 9));

        result.IsSuccess.Should().BeTrue();
        stored!.Name.Should().Be("Office Chair");
        stored.Category.Should().Be("furniture");
        _repo.Verify(r => r.NameExists("Office Chair", null), Times.Once);
    }

    [Fact]
    public async Task Add_WithADuplicateName_ReturnsConflictAndStoresNothing()
    {
        _repo.Setup(r => r.NameExists("Office Chair", null)).ReturnsAsync(true);

        var result = await _service.Add(Form());

        result.Error.Should().Be(Messages.ProductNameAlreadyExists);
        _repo.Verify(r => r.Add(It.IsAny<ProductForm>(), It.IsAny<long>()), Times.Never);
    }

    // ---- update

    [Fact]
    public async Task Update_ReplacesTheProductAndRecordsTheUpdater()
    {
        _repo.Setup(r => r.Get(5)).ReturnsAsync(Product(5, "Old", 1));
        _repo.Setup(r => r.Update(5, It.Is<ProductForm>(f => f.Name == "New" && f.Category == "garden"), 9))
            .ReturnsAsync(Product(5, "New", 5, "garden"));

        var result = await _service.Update(5, Form(" New ", "GARDEN", userId: 9));

        result.IsSuccess.Should().BeTrue();
        result.Data!.Name.Should().Be("New");
        _repo.Verify(r => r.NameExists("New", 5), Times.Once);
    }

    [Fact]
    public async Task Update_OfAMissingProduct_ReturnsNotFound()
    {
        _repo.Setup(r => r.Get(99)).ReturnsAsync((AdminProductResponse?)null);

        var result = await _service.Update(99, Form());

        result.Error.Should().Be(Messages.ProductNotFound);
        _repo.Verify(r => r.Update(It.IsAny<long>(), It.IsAny<ProductForm>(), It.IsAny<long>()), Times.Never);
    }

    [Fact]
    public async Task Update_ToAnotherProductsName_ReturnsConflict()
    {
        _repo.Setup(r => r.Get(5)).ReturnsAsync(Product(5, "Old", 1));
        _repo.Setup(r => r.NameExists("Office Chair", 5)).ReturnsAsync(true);

        var result = await _service.Update(5, Form());

        result.Error.Should().Be(Messages.ProductNameAlreadyExists);
        _repo.Verify(r => r.Update(It.IsAny<long>(), It.IsAny<ProductForm>(), It.IsAny<long>()), Times.Never);
    }

    [Fact]
    public async Task Update_WhenTheProductDisappearsInBetween_ReturnsNotFound()
    {
        _repo.Setup(r => r.Get(5)).ReturnsAsync(Product(5, "Old", 1));
        _repo.Setup(r => r.Update(5, It.IsAny<ProductForm>(), It.IsAny<long>())).ReturnsAsync((AdminProductResponse?)null);

        (await _service.Update(5, Form())).Error.Should().Be(Messages.ProductNotFound);
    }

    // ---- list

    [Fact]
    public async Task GetAll_ForAdmin_KeepsCostAndExactQuantity_ReturnsTheTotal_AndNormalizesTheFilter()
    {
        AdminProductFilter? used = null;
        _repo.Setup(r => r.GetPage(It.IsAny<AdminProductFilter>())).Callback<AdminProductFilter>(f => used = f)
            .ReturnsAsync((new List<AdminProductResponse> { Product(1, "Chair", 3), Product(2, "Desk", 12) }, 42));

        var result = await _service.GetAll(new ServiceRequest<AdminProductFilter>(
            new AdminProductFilter { Name = "  ch ", Category = "FURNITURE", PageNumber = 2, PageSize = 5 }, 1));

        result.Data!.Select(p => p.AvailableQuantity).Should().Equal(3, 12);
        result.Data!.All(p => p.Cost == 60).Should().BeTrue();
        result.TotalCount.Should().Be(42);
        used.Should().BeEquivalentTo(new AdminProductFilter { Name = "ch", Category = "furniture", PageNumber = 2, PageSize = 5 });
    }

    [Fact]
    public async Task GetAllForCustomer_ShowsStockStatusInsteadOfQuantity_AndNormalizesTheFilter()
    {
        CustomerProductFilter? used = null;
        _repo.Setup(r => r.GetCursorPage(It.IsAny<CustomerProductFilter>())).Callback<CustomerProductFilter>(f => used = f)
            .ReturnsAsync(new CursorResponse<AdminProductResponse>(new List<AdminProductResponse>
            {
                Product(1, "A", 0), Product(2, "B", 4), Product(3, "C", 5), Product(4, "D", 9), Product(5, "E", 10), Product(6, "F", 500)
            }, "next", true));

        var result = await _service.GetAllForCustomer(new ServiceRequest<CustomerProductFilter>(
            new CustomerProductFilter { Name = " a ", Category = "GARDEN", Limit = 6, Cursor = " c0 " }, 1));

        result.Data!.Data.Select(p => p.StockStatus).Should().Equal("low", "low", "limited", "limited", "available", "available");
        result.Data!.NextCursor.Should().Be("next");
        result.Data!.HasMore.Should().BeTrue();
        used.Should().BeEquivalentTo(new CustomerProductFilter { Name = "a", Category = "garden", Limit = 6, Cursor = "c0" });
    }

    [Fact]
    public void CustomerResponse_HasNoCostOrQuantityMember()
    {
        var members = typeof(CustomerProductResponse).GetProperties().Select(p => p.Name);

        members.Should().NotContain(["Cost", "AvailableQuantity", "CreatedBy", "UpdatedBy"]);
    }
}
