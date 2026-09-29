using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Payments;
using FluentValidation;

namespace AlQaseh_Ecommerce_API.Features.Orders.Validators;

public class CreateOrderRequestValidator : AbstractValidator<CreateOrderRequest>
{
    public const int MaxItems = 100;
    public const int MaxQuantity = 10_000;

    public CreateOrderRequestValidator()
    {
        RuleFor(x => x.Items)
            .NotEmpty().WithMessage("An order must contain at least one item.")
            .Must(items => items is null || items.Count <= MaxItems).WithMessage($"An order cannot contain more than {MaxItems} items.");

        RuleForEach(x => x.Items).ChildRules(item =>
        {
            item.RuleFor(i => i.ProductId).GreaterThan(0).WithMessage("Product id is required.");
            item.RuleFor(i => i.Quantity).InclusiveBetween(1, MaxQuantity).WithMessage($"Quantity must be between 1 and {MaxQuantity}.");
        });

        RuleFor(x => x.DiscountCode)
            .MaximumLength(50).WithMessage("Discount code must not exceed 50 characters.");

        RuleFor(x => x.Payment).NotNull().WithMessage("Payment details are required.");

        When(x => x.Payment is not null, () =>
        {
            RuleFor(x => x.Payment.Method)
                .MaximumLength(20).WithMessage("Payment method must not exceed 20 characters.")
                .Must(method => PaymentMethods.Normalize(method) is not null)
                .WithMessage($"Payment method must be one of: {string.Join(", ", PaymentMethods.All)}.");

            When(x => PaymentMethods.Normalize(x.Payment.Method) == PaymentMethods.CreditCard, () =>
            {
                RuleFor(x => x.Payment.CardNumber)
                    .NotEmpty().WithMessage("Card number is required for CreditCard.")
                    .Matches(@"^\d{12,19}\z").WithMessage("Card number must be 12 to 19 digits."); // \z, not $: "$" would accept a trailing newline

                // Only the fields of the chosen method are accepted: wallet data with a card payment is a client bug.
                RuleFor(x => x.Payment.PhoneNumber)
                    .Must(string.IsNullOrWhiteSpace).WithMessage("Phone number is only for XyzWallet payments.");
                RuleFor(x => x.Payment.WalletPassword)
                    .Must(string.IsNullOrWhiteSpace).WithMessage("Wallet password is only for XyzWallet payments.");
            });

            When(x => PaymentMethods.Normalize(x.Payment.Method) == PaymentMethods.XyzWallet, () =>
            {
                RuleFor(x => x.Payment.PhoneNumber)
                    .NotEmpty().WithMessage("Phone number is required for XyzWallet.")
                    .Matches(@"^\+?\d{8,15}\z").WithMessage("Phone number must be 8 to 15 digits with an optional leading +.");

                RuleFor(x => x.Payment.WalletPassword)
                    .NotEmpty().WithMessage("Wallet password is required for XyzWallet.")
                    .MaximumLength(128).WithMessage("Wallet password must not exceed 128 characters.");

                RuleFor(x => x.Payment.CardNumber)
                    .Must(string.IsNullOrWhiteSpace).WithMessage("Card number is only for CreditCard payments.");
            });
        });
    }
}
