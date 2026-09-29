using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Features.Products.Utils;
using FluentValidation;

namespace AlQaseh_Ecommerce_API.Features.Products.Validators;

public class CustomerProductFilterValidator : AbstractValidator<CustomerProductFilter>
{
    public const int MaxLimit = 50;

    public CustomerProductFilterValidator()
    {
        RuleFor(x => x.Limit)
            .InclusiveBetween(1, MaxLimit).WithMessage($"Limit must be between 1 and {MaxLimit}.");

        RuleFor(x => x.Cursor)
            .Must(ProductCursor.IsValid)
            .When(x => !string.IsNullOrWhiteSpace(x.Cursor))
            .WithMessage("Invalid cursor.");

        RuleFor(x => x.Name)
            .MaximumLength(150).WithMessage("Name filter must not exceed 150 characters.");

        RuleFor(x => x.Category)
            .Must(category => ProductCategories.Normalize(category) is not null)
            .When(x => !string.IsNullOrWhiteSpace(x.Category))
            .WithMessage($"Category must be one of: {string.Join(", ", ProductCategories.All)}.");
    }
}
