using System.Reflection;
using AlQaseh_Ecommerce_API.Shared.Constants;
using AlQaseh_Ecommerce_API.Shared.Utils;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Shared.Utils;

public class ErrorMessagesUtilsTests
{
    private static string[] AllCodes() => typeof(Messages)
        .GetFields(BindingFlags.Public | BindingFlags.Static)
        .Where(f => f.IsLiteral && f.FieldType == typeof(string))
        .Select(f => (string)f.GetRawConstantValue()!)
        .ToArray();

    [Fact]
    public void EveryErrorCode_HasAnEnglishAndAnArabicMessage()
    {
        foreach (var code in AllCodes())
        {
            var en = code.GetMessage("en");
            var ar = code.GetMessage("ar");

            en.Should().NotBe(code, $"{code} needs an English message");
            ar.Should().NotBe(code, $"{code} needs an Arabic message");
            ar.Should().NotBe(en);
            ar.Should().MatchRegex("[؀-ۿ]", $"{code} must be written in Arabic");
        }
    }

    [Fact]
    public void EveryErrorCode_HasAnExplicitHttpStatus()
    {
        foreach (var code in AllCodes().Except([Messages.PaymentMethodNotSupported]))
            Messages.StatusCodeOf(code).Should().NotBe(400, $"{code} should map to a specific status, not the default");
    }

    [Theory]
    [InlineData(Messages.InvalidCredentials, 401)]
    [InlineData(Messages.ProductNotFound, 404)]
    [InlineData(Messages.DiscountNotFound, 404)]
    [InlineData(Messages.ProductNameAlreadyExists, 409)]
    [InlineData(Messages.InsufficientStock, 409)]
    [InlineData(Messages.DiscountAlreadyUsed, 409)]
    [InlineData(Messages.DiscountExpired, 422)]
    [InlineData(Messages.MinimumOrderTotalNotMet, 422)]
    [InlineData(Messages.DiscountExceedsTotal, 422)]
    [InlineData(Messages.PaymentFailed, 402)]
    [InlineData(Messages.PaymentMethodNotSupported, 400)]
    [InlineData("SomethingUnknown", 400)]
    public void StatusCodes_AreAsDocumented(string code, int status) => Messages.StatusCodeOf(code).Should().Be(status);

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("fr")]
    public void OtherLanguages_FallBackToEnglish(string? lang) =>
        Messages.ProductNotFound.GetMessage(lang).Should().Be("Product not found.");

    [Fact]
    public void UnknownKey_IsReturnedAsIs() => "NoSuchKey".GetMessage("ar").Should().Be("NoSuchKey");

    [Fact]
    public void LanguageIsCaseInsensitive() =>
        Messages.PaymentFailed.GetMessage("AR").Should().Be(Messages.PaymentFailed.GetMessage("ar"));
}
