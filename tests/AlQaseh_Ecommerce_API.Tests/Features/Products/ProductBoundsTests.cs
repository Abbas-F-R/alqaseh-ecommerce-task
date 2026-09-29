using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Features.Products.Validators;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Products;

/// <summary>Boundaries of every product field: null, blank, too long, negative, zero where invalid, the maximum and one above it.</summary>
public class ProductBoundsTests
{
    private readonly ProductFormValidator _validator = new();

    private bool Valid(string? name = "Chair", string? category = "furniture", decimal price = 10, decimal cost = 5, int quantity = 1) =>
        _validator.Validate(new ProductForm { Name = name!, Category = category!, Price = price, Cost = cost, AvailableQuantity = quantity }).IsValid;

    [Fact]
    public void Name_Is1To150Characters_NotBlank()
    {
        Valid(name: new string('a', 150)).Should().BeTrue();
        Valid(name: new string('a', 151)).Should().BeFalse();
        Valid(name: null).Should().BeFalse();
        Valid(name: "").Should().BeFalse();
        Valid(name: "   ").Should().BeFalse();
    }

    [Fact]
    public void Quantity_Is0To1000000()
    {
        Valid(quantity: 0).Should().BeTrue();
        Valid(quantity: ProductFormValidator.MaxQuantity).Should().BeTrue();
        Valid(quantity: ProductFormValidator.MaxQuantity + 1).Should().BeFalse();
        Valid(quantity: -1).Should().BeFalse();
        Valid(quantity: int.MaxValue).Should().BeFalse();
    }

    [Fact]
    public void Price_IsAbove0_WithAtMost10DigitsBeforeAnd2AfterTheDecimalPoint()
    {
        Valid(price: 0.01m, cost: 0).Should().BeTrue();
        Valid(price: 9_999_999_999.99m).Should().BeTrue();
        Valid(price: 0m).Should().BeFalse();
        Valid(price: -1m).Should().BeFalse();
        Valid(price: 0.001m).Should().BeFalse();
        Valid(price: 10.005m).Should().BeFalse();
        Valid(price: 10_000_000_000m).Should().BeFalse();
    }

    [Fact]
    public void Cost_Is0OrMore_WithTheSamePrecision_AndNeverAboveThePrice()
    {
        Valid(price: 10, cost: 0).Should().BeTrue();
        Valid(price: 9_999_999_999.99m, cost: 9_999_999_999.99m).Should().BeTrue();
        Valid(price: 10, cost: 10).Should().BeTrue();   // break-even is allowed
        Valid(price: 10, cost: 10.01m).Should().BeFalse();
        Valid(price: 50, cost: 140).Should().BeFalse();
        Valid(price: 0.01m, cost: 0.02m).Should().BeFalse();
        Valid(cost: -0.01m).Should().BeFalse();
        Valid(cost: 5.555m).Should().BeFalse();
        Valid(price: 9_999_999_999.99m, cost: 10_000_000_000m).Should().BeFalse();
    }

    [Fact]
    public void CostAbovePrice_IsOneClearError_OnTheCostField()
    {
        var result = _validator.Validate(new ProductForm { Name = "Chair", Category = "furniture", Price = 10, Cost = 11, AvailableQuantity = 1 });

        result.Errors.Should().ContainSingle().Which.Should().Match<FluentValidation.Results.ValidationFailure>(e =>
            e.PropertyName == "Cost" && e.ErrorMessage == "Cost must not exceed the price.");
    }

    [Theory]
    [InlineData("furniture", true)]
    [InlineData("ELECTRONICS", true)]
    [InlineData("toys", false)]
    [InlineData("", false)]
    [InlineData(null, false)]
    public void Category_MustBeOneOfTheFour(string? category, bool valid) => Valid(category: category).Should().Be(valid);
}
