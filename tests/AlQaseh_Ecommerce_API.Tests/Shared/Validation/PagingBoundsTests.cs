using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Orders.Validators;
using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Features.Products.Utils;
using AlQaseh_Ecommerce_API.Features.Products.Validators;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Shared.Validation;

/// <summary>List filters and pagination are bounded, so a request cannot make the database work on an absurd value (or overflow its int arithmetic).</summary>
public class PagingBoundsTests
{
    private readonly AdminProductFilterValidator _admin = new();
    private readonly CustomerProductFilterValidator _customer = new();
    private readonly OrderFilterValidator _orders = new();

    [Theory]
    [InlineData(0, 1, true)]
    [InlineData(BaseFilter.MaxPageNumber, 50, true)]
    [InlineData(BaseFilter.MaxPageNumber + 1, 10, false)]
    [InlineData(int.MaxValue, 10, false)] // used to overflow the offset of the stored procedure
    [InlineData(-1, 10, false)]
    [InlineData(0, 0, false)]
    [InlineData(0, 51, false)]
    public void PageAndSize(int page, int size, bool valid) =>
        _admin.Validate(new AdminProductFilter { PageNumber = page, PageSize = size }).IsValid.Should().Be(valid);

    [Fact]
    public void ProductNameFilter_Is150CharactersAtMost()
    {
        _admin.Validate(new AdminProductFilter { Name = new string('a', 150) }).IsValid.Should().BeTrue();
        _admin.Validate(new AdminProductFilter { Name = new string('a', 151) }).IsValid.Should().BeFalse();
        _customer.Validate(new CustomerProductFilter { Name = new string('a', 151) }).IsValid.Should().BeFalse();
    }

    [Fact]
    public void CustomerFilterOfTheOrderList_Is50CharactersAtMost()
    {
        _orders.Validate(new OrderFilter { Customer = new string('a', 50) }).IsValid.Should().BeTrue();
        _orders.Validate(new OrderFilter { Customer = new string('a', 51) }).IsValid.Should().BeFalse();
        _orders.Validate(new OrderFilter { PageNumber = BaseFilter.MaxPageNumber + 1 }).IsValid.Should().BeFalse();
    }

    [Fact]
    public void Cursor_ARealOneIsValid_GarbageAnOverlongOneAndANegativeIdAreNot()
    {
        _customer.Validate(new CustomerProductFilter { Cursor = ProductCursor.Encode(long.MaxValue) }).IsValid.Should().BeTrue();
        _customer.Validate(new CustomerProductFilter { Cursor = "not-a-cursor" }).IsValid.Should().BeFalse();
        _customer.Validate(new CustomerProductFilter { Cursor = new string('A', 101) }).IsValid.Should().BeFalse();
        _customer.Validate(new CustomerProductFilter { Cursor = new string('A', 100_000) }).IsValid.Should().BeFalse();
    }
}
