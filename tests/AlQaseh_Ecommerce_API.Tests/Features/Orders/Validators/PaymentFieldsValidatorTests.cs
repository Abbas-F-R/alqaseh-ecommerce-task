using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Orders.Validators;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Orders.Validators;

/// <summary>Length and format of the payment fields, and "only the fields of the chosen method": card data and wallet data never mix.</summary>
public class PaymentFieldsValidatorTests
{
    private readonly CreateOrderRequestValidator _validator = new();

    private bool Valid(PaymentRequest payment) =>
        _validator.Validate(new CreateOrderRequest { Items = [new OrderItemRequest { ProductId = 1, Quantity = 1 }], Payment = payment }).IsValid;

    private bool Card(string? number) => Valid(new PaymentRequest { Method = "CreditCard", CardNumber = number });

    private bool Wallet(string? phone, string? password) => Valid(new PaymentRequest { Method = "XyzWallet", PhoneNumber = phone, WalletPassword = password });

    [Theory]
    [InlineData("411111111111")]
    [InlineData("4111111111111111")]
    [InlineData("4111111111111111111")]
    public void CardNumberOf12To19Digits_IsValid(string number) => Card(number).Should().BeTrue();

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("41111111111")]
    [InlineData("41111111111111111111")]
    [InlineData("4111 1111 1111 1111")]
    [InlineData("abcd1111abcd1111")]
    [InlineData("4111-1111-1111-1111")]
    [InlineData("4111111111111111\n")]
    public void CardNumberMissingTooShortTooLongOrNotDigits_IsRejected(string? number) => Card(number).Should().BeFalse();

    [Fact]
    public void GiganticCardNumber_IsRejectedNotProcessed() => Card(new string('4', 1_000_000)).Should().BeFalse();

    [Fact]
    public void Wallet_NeedsAPhoneOf8To15Digits_AndAPasswordOf1To128Characters()
    {
        Wallet("+9647800000000", "secret").Should().BeTrue();
        Wallet("07800000", "s").Should().BeTrue();
        Wallet("+964780000000000", new string('p', 128)).Should().BeTrue();

        Wallet("1234567", "secret").Should().BeFalse();
        Wallet("+9647800000000000", "secret").Should().BeFalse();
        Wallet("+96478abc0000", "secret").Should().BeFalse();
        Wallet("+964 780 000 0000", "secret").Should().BeFalse();
        Wallet("+9647800000000\n", "secret").Should().BeFalse(); // a trailing newline is not a digit
        Wallet("+9647800000000", new string('p', 129)).Should().BeFalse();
        Wallet("+9647800000000", "").Should().BeFalse();
        Wallet(null, "secret").Should().BeFalse();
    }

    [Fact]
    public void FieldsOfTheOtherMethod_AreRejected_ButAnEmptyUnusedFieldIsTolerated()
    {
        Valid(new PaymentRequest { Method = "CreditCard", CardNumber = "4111111111111111", PhoneNumber = "+9647800000000" }).Should().BeFalse();
        Valid(new PaymentRequest { Method = "CreditCard", CardNumber = "4111111111111111", WalletPassword = "secret" }).Should().BeFalse();
        Valid(new PaymentRequest { Method = "XyzWallet", PhoneNumber = "+9647800000000", WalletPassword = "secret", CardNumber = "4111111111111111" }).Should().BeFalse();

        Valid(new PaymentRequest { Method = "CreditCard", CardNumber = "4111111111111111", PhoneNumber = "", WalletPassword = " " }).Should().BeTrue();
    }

    [Theory]
    [InlineData("CreditCard", true)]
    [InlineData("creditcard", true)]
    [InlineData("XYZWALLET", false)] // valid method, but the card fields do not fit a wallet
    [InlineData("Cash", false)]
    [InlineData("", false)]
    public void MethodMustBeOneOfTheTwo(string method, bool valid) =>
        Valid(new PaymentRequest { Method = method, CardNumber = "4111111111111111" }).Should().Be(valid);

    [Fact]
    public void OverlongMethod_IsRejected() => Valid(new PaymentRequest { Method = new string('x', 21), CardNumber = "4111111111111111" }).Should().BeFalse();
}
