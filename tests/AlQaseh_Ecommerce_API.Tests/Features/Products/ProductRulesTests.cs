using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Orders.Validators;
using AlQaseh_Ecommerce_API.Features.Products;
using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Features.Products.Utils;
using AlQaseh_Ecommerce_API.Features.Products.Validators;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Validation;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Products;

public class ProductRulesTests
{
    [Theory]
    [InlineData(0, "low")]
    [InlineData(4, "low")]
    [InlineData(5, "limited")]
    [InlineData(9, "limited")]
    [InlineData(10, "available")]
    [InlineData(1_000_000, "available")]
    public void StockStatus_FollowsTheQuantityBands(int quantity, string expected) =>
        StockStatuses.FromQuantity(quantity).Should().Be(expected);

    [Fact]
    public void ThereAreExactlyFourCategories() =>
        ProductCategories.All.Should().Equal("furniture", "electronics", "beauty", "garden");

    [Theory]
    [InlineData("furniture", "furniture")]
    [InlineData("ELECTRONICS", "electronics")]
    [InlineData(" Beauty ", "beauty")]
    [InlineData("gArDeN", "garden")]
    [InlineData("toys", null)]
    [InlineData("", null)]
    [InlineData(null, null)]
    public void Categories_AreNormalizedToLowercase(string? input, string? expected) =>
        ProductCategories.Normalize(input).Should().Be(expected);

    // ---- product form

    private readonly ProductFormValidator _form = new();

    private static ProductForm ValidForm() => new() { Name = "Chair", Category = "furniture", Price = 100, Cost = 60, AvailableQuantity = 5 };

    [Fact]
    public void ValidForm_Passes() => _form.Validate(ValidForm()).IsValid.Should().BeTrue();

    [Fact]
    public void ZeroCostAndZeroQuantity_AreAllowed()
    {
        var form = ValidForm();
        form.Cost = 0;
        form.AvailableQuantity = 0;

        _form.Validate(form).IsValid.Should().BeTrue();
    }

    [Theory]
    [InlineData("", "furniture", 100, 60, 5)]
    [InlineData("   ", "furniture", 100, 60, 5)]
    [InlineData("Chair", "toys", 100, 60, 5)]
    [InlineData("Chair", "", 100, 60, 5)]
    [InlineData("Chair", "furniture", 0, 60, 5)]
    [InlineData("Chair", "furniture", -1, 60, 5)]
    [InlineData("Chair", "furniture", 100, -1, 5)]
    [InlineData("Chair", "furniture", 100, 60, -1)]
    public void InvalidForm_Fails(string name, string category, decimal price, decimal cost, int quantity) =>
        _form.Validate(new ProductForm { Name = name, Category = category, Price = price, Cost = cost, AvailableQuantity = quantity })
            .IsValid.Should().BeFalse();

    [Fact]
    public void OverlongName_Fails()
    {
        var form = ValidForm();
        form.Name = new string('x', 151);

        _form.Validate(form).IsValid.Should().BeFalse();
    }

    // ---- filters

    private readonly CustomerProductFilterValidator _customer = new();
    private readonly AdminProductFilterValidator _admin = new();

    [Fact]
    public void DefaultCustomerFilter_Passes() => _customer.Validate(new CustomerProductFilter()).IsValid.Should().BeTrue();

    [Theory]
    [InlineData(1)]
    [InlineData(10)]
    [InlineData(50)]
    public void CustomerLimitInRange_Passes(int limit) =>
        _customer.Validate(new CustomerProductFilter { Limit = limit }).IsValid.Should().BeTrue();

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    [InlineData(51)]
    [InlineData(999)]
    public void CustomerLimitOutOfRange_Fails(int limit) =>
        _customer.Validate(new CustomerProductFilter { Limit = limit }).IsValid.Should().BeFalse();

    [Fact]
    public void ValidCursor_Passes() =>
        _customer.Validate(new CustomerProductFilter { Cursor = ProductCursor.Encode(42) }).IsValid.Should().BeTrue();

    [Theory]
    [InlineData("not-base64")]
    [InlineData("YWJj")]
    [InlineData("eyJJZCI6LTF9")]
    public void InvalidCursor_Fails(string cursor) =>
        _customer.Validate(new CustomerProductFilter { Cursor = cursor }).IsValid.Should().BeFalse();

    [Theory]
    [InlineData(1)]
    [InlineData(42)]
    [InlineData(1054)]
    [InlineData(long.MaxValue)]
    public void CursorRoundTrips_AndIsUrlSafe(long id)
    {
        var cursor = ProductCursor.Encode(id);

        cursor.Should().NotContainAny("+", "/", "=");
        ProductCursor.TryDecode(cursor, out var decoded).Should().BeTrue();
        decoded.Should().Be(id);
    }

    [Theory]
    [InlineData("garden", true)]
    [InlineData("GARDEN", true)]
    [InlineData(null, true)]
    [InlineData("toys", false)]
    public void CategoryFilter_MustBeOneOfTheFour(string? category, bool valid)
    {
        _customer.Validate(new CustomerProductFilter { Category = category }).IsValid.Should().Be(valid);
        _admin.Validate(new AdminProductFilter { Category = category }).IsValid.Should().Be(valid);
    }

    [Theory]
    [InlineData(0, 1, true)]
    [InlineData(0, 50, true)]
    [InlineData(999, 10, true)]
    [InlineData(-1, 10, false)]
    [InlineData(0, 0, false)]
    [InlineData(0, 51, false)]
    public void AdminPaging_ZeroBasedPageAndSizeUpTo50(int page, int size, bool valid) =>
        _admin.Validate(new AdminProductFilter { PageNumber = page, PageSize = size }).IsValid.Should().Be(valid);

    [Theory]
    [InlineData(-1, 10, false)]
    [InlineData(0, 10, true)]
    [InlineData(1, 51, false)]
    [InlineData(1, 50, true)]
    public void CustomerOrderPaging_HasTheSameLimits(int page, int size, bool valid) =>
        new BaseFilterValidator().Validate(new BaseFilter { PageNumber = page, PageSize = size }).IsValid.Should().Be(valid);

    [Theory]
    [InlineData("CreditCard", true)]
    [InlineData("xyzwallet", true)]
    [InlineData(null, true)]
    [InlineData("Bitcoin", false)]
    public void OrderFilter_PaymentMethodMustBeSupported(string? method, bool valid) =>
        new OrderFilterValidator().Validate(new OrderFilter { PaymentMethod = method }).IsValid.Should().Be(valid);

    [Fact]
    public void OrderFilter_CustomerIdMustBePositive() =>
        new OrderFilterValidator().Validate(new OrderFilter { CustomerId = 0 }).IsValid.Should().BeFalse();
}
