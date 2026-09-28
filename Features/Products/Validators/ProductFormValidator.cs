using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using FluentValidation;

namespace AlQaseh_Ecommerce_API.Features.Products.Validators;

public class ProductFormValidator : AbstractValidator<ProductForm>
{
    public ProductFormValidator()
    {
        RuleFor(x => x.Name)
            .NotEmpty().WithMessage("Product name is required.")
            .MaximumLength(150).WithMessage("Product name must not exceed 150 characters.");

        RuleFor(x => x.Category)
            .Must(category => ProductCategories.Normalize(category) is not null)
            .WithMessage($"Category must be one of: {string.Join(", ", ProductCategories.All)}.");

        RuleFor(x => x.Price)
            .GreaterThan(0).WithMessage("Price must be greater than 0.");

        RuleFor(x => x.Cost)
            .GreaterThanOrEqualTo(0).WithMessage("Cost must be greater than or equal to 0.");

        RuleFor(x => x.AvailableQuantity)
            .GreaterThanOrEqualTo(0).WithMessage("Available quantity must be greater than or equal to 0.");
    }
}
