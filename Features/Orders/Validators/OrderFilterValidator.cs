using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Payments;
using AlQaseh_Ecommerce_API.Shared.Validation;
using FluentValidation;

namespace AlQaseh_Ecommerce_API.Features.Orders.Validators;

public class OrderFilterValidator : PagedFilterValidator<OrderFilter>
{
    public OrderFilterValidator()
    {
        RuleFor(x => x.Customer)
            .MaximumLength(50).WithMessage("Customer filter must not exceed 50 characters.");

        RuleFor(x => x.CustomerId)
            .GreaterThan(0).When(x => x.CustomerId.HasValue).WithMessage("Customer id is invalid.");

        RuleFor(x => x.PaymentMethod)
            .Must(method => PaymentMethods.Normalize(method) is not null)
            .When(x => !string.IsNullOrWhiteSpace(x.PaymentMethod))
            .WithMessage($"Payment method must be one of: {string.Join(", ", PaymentMethods.All)}.");
    }
}
