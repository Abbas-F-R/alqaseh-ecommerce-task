using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Orders.Validators;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Orders.Validators;

public class CreateOrderRequestValidatorTests
{
    private readonly CreateOrderRequestValidator _validator = new();

    private static CreateOrderRequest Valid() => new()
    {
        Items = [new OrderItemRequest { ProductId = 1, Quantity = 2 }],
        Payment = new PaymentRequest { Method = "CreditCard", CardNumber = "4111111111111111" }
    };

    [Fact]
    public void ValidCreditCardOrder_Passes() => _validator.Validate(Valid()).IsValid.Should().BeTrue();

    [Fact]
    public void ValidWalletOrder_Passes()
    {
        var request = Valid();
        request.Payment = new PaymentRequest { Method = "xyzwallet", PhoneNumber = "+9647701234567", WalletPassword = "secret" };
        request.DiscountCode = "ABC123";

        _validator.Validate(request).IsValid.Should().BeTrue();
    }

    [Fact]
    public void NoItems_Fails()
    {
        var request = Valid();
        request.Items = [];

        _validator.Validate(request).IsValid.Should().BeFalse();
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    [InlineData(10_001)]
    public void QuantityOutOfRange_Fails(int quantity)
    {
        var request = Valid();
        request.Items[0].Quantity = quantity;

        _validator.Validate(request).IsValid.Should().BeFalse();
    }

    [Fact]
    public void ProductIdMissing_Fails()
    {
        var request = Valid();
        request.Items[0].ProductId = 0;

        _validator.Validate(request).IsValid.Should().BeFalse();
    }

    [Fact]
    public void TooManyItems_Fails()
    {
        var request = Valid();
        request.Items = Enumerable.Range(1, 101).Select(i => new OrderItemRequest { ProductId = i, Quantity = 1 }).ToList();

        _validator.Validate(request).IsValid.Should().BeFalse();
    }

    [Theory]
    [InlineData("")]
    [InlineData("Bitcoin")]
    [InlineData("CREDIT_CARD")]
    public void UnsupportedPaymentMethod_Fails(string method)
    {
        var request = Valid();
        request.Payment = new PaymentRequest { Method = method };

        _validator.Validate(request).IsValid.Should().BeFalse();
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("abcd")]
    [InlineData("12345")]
    public void CreditCard_NeedsACardNumberOf12To19Digits(string? number)
    {
        var request = Valid();
        request.Payment.CardNumber = number;

        _validator.Validate(request).IsValid.Should().BeFalse();
    }

    [Theory]
    [InlineData(null, "secret")]
    [InlineData("", "secret")]
    [InlineData("abc", "secret")]
    [InlineData("07701234567", null)]
    [InlineData("07701234567", "")]
    public void XyzWallet_NeedsAPhoneNumberAndAWalletPassword(string? phone, string? password)
    {
        var request = Valid();
        request.Payment = new PaymentRequest { Method = "XyzWallet", PhoneNumber = phone, WalletPassword = password };

        _validator.Validate(request).IsValid.Should().BeFalse();
    }

    [Fact]
    public void NullPayment_Fails()
    {
        var request = Valid();
        request.Payment = null!;

        _validator.Validate(request).IsValid.Should().BeFalse();
    }
}
