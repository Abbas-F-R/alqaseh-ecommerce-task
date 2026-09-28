using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Payments;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Payments;

public class PaymentProcessorTests
{
    private static readonly PaymentProcessorFactory Factory =
        new([new CreditCardPaymentProcessor(), new XyzWalletPaymentProcessor()]);

    [Fact]
    public void CreditCard_AcceptsAnyCard()
    {
        var result = new CreditCardPaymentProcessor().Charge(100, new PaymentRequest { Method = "CreditCard", CardNumber = "4111111111111111" });

        result.Success.Should().BeTrue();
        result.TransactionId.Should().StartWith("CC-");
    }

    [Fact]
    public void CreditCard_DeclinesTheTestDeclineCard()
    {
        var result = new CreditCardPaymentProcessor().Charge(100, new PaymentRequest { CardNumber = CreditCardPaymentProcessor.DeclinedCardNumber });

        result.Success.Should().BeFalse();
        result.TransactionId.Should().BeNull();
        result.Error.Should().NotBeNullOrEmpty();
    }

    [Fact]
    public void XyzWallet_AcceptsAnyPhoneAndPassword()
    {
        var result = new XyzWalletPaymentProcessor().Charge(100, new PaymentRequest { PhoneNumber = "07701234567", WalletPassword = "secret" });

        result.Success.Should().BeTrue();
        result.TransactionId.Should().StartWith("XYZ-");
    }

    [Fact]
    public void XyzWallet_RejectsTheTestWrongPassword()
    {
        var result = new XyzWalletPaymentProcessor().Charge(100, new PaymentRequest { PhoneNumber = "07701234567", WalletPassword = XyzWalletPaymentProcessor.WrongPassword });

        result.Success.Should().BeFalse();
    }

    [Theory]
    [InlineData("CreditCard", "CreditCard")]
    [InlineData("creditcard", "CreditCard")]
    [InlineData("  XYZWALLET ", "XyzWallet")]
    public void Factory_FindsTheProcessorInAnyLetterCase(string input, string expectedMethod) =>
        Factory.GetProcessor(input)!.Method.Should().Be(expectedMethod);

    [Theory]
    [InlineData("Bitcoin")]
    [InlineData("")]
    [InlineData("CREDIT_CARD")]
    public void Factory_ReturnsNullForUnsupportedMethods(string input) => Factory.GetProcessor(input).Should().BeNull();

    [Fact]
    public void ThereAreExactlyTwoPaymentMethods() => PaymentMethods.All.Should().Equal("CreditCard", "XyzWallet");
}
