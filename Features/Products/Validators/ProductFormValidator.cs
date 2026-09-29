using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using FluentValidation;

namespace AlQaseh_Ecommerce_API.Features.Products.Validators;

public class ProductFormValidator : AbstractValidator<ProductForm>
{
    private const int MoneyPrecision = 12;
    private const int MoneyScale = 2;

    /// <summary>Upper bound of the stock of one product: far above any real catalogue, far below the int range.</summary>
    public const int MaxQuantity = 1_000_000;

    public ProductFormValidator()
    {
        RuleFor(x => x.Name)
            .NotEmpty().WithMessage("Product name is required.")
            .MaximumLength(150).WithMessage("Product name must not exceed 150 characters.");

        RuleFor(x => x.Category)
            .Must(category => ProductCategories.Normalize(category) is not null)
            .WithMessage($"Category must be one of: {string.Join(", ", ProductCategories.All)}.");

        // At most 10 digits before and 2 after the decimal point: an order of up to 1,000,000 units of one product still fits decimal(18,2),
        // and a value the column cannot hold is a 400 here instead of a rounded value or an SQL overflow (500).
        RuleFor(x => x.Price)
            .GreaterThan(0).WithMessage("Price must be greater than 0.")
            .PrecisionScale(MoneyPrecision, MoneyScale, true).WithMessage("Price must have at most 10 digits before and 2 after the decimal point.");

        RuleFor(x => x.Cost)
            .GreaterThanOrEqualTo(0).WithMessage("Cost must be greater than or equal to 0.")
            .PrecisionScale(MoneyPrecision, MoneyScale, true).WithMessage("Cost must have at most 10 digits before and 2 after the decimal point.")
            .LessThanOrEqualTo(x => x.Price).WithMessage("Cost must not exceed the price."); // a product is never sold below what it costs

        RuleFor(x => x.AvailableQuantity)
            .InclusiveBetween(0, MaxQuantity).WithMessage($"Available quantity must be between 0 and {MaxQuantity}.");
    }
}
