using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Shared.Validation;
using FluentValidation;

namespace AlQaseh_Ecommerce_API.Features.Products.Validators;

public class ProductFilterValidator : PagedFilterValidator<ProductFilter>
{
    public ProductFilterValidator()
    {
        RuleFor(x => x.Name)
            .MaximumLength(150).WithMessage("Name filter must not exceed 150 characters.");

        RuleFor(x => x.Category)
            .Must(category => ProductCategories.Normalize(category) is not null)
            .When(x => !string.IsNullOrWhiteSpace(x.Category))
            .WithMessage($"Category must be one of: {string.Join(", ", ProductCategories.All)}.");
    }
}
