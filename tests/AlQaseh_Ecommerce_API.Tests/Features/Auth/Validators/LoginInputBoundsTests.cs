using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Features.Auth.Validators;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Auth.Validators;

/// <summary>Login input is bounded and made of harmless characters before anything looks the user up or logs the name.</summary>
public class LoginInputBoundsTests
{
    private readonly LoginRequestValidator _validator = new();

    private bool Valid(string? userName, string? password) =>
        _validator.Validate(new LoginRequest { UserName = userName!, Password = password! }).IsValid;

    [Fact]
    public void NormalLogin_AndTheBoundaries_AreValid()
    {
        Valid("admin", "Admin123!").Should().BeTrue();
        Valid("customer.one@shop-1_x", "p").Should().BeTrue();
        Valid(new string('a', 50), new string('p', 128)).Should().BeTrue();
    }

    [Fact]
    public void NullEmptyAndBlank_AreRejected()
    {
        Valid(null, "x").Should().BeFalse();
        Valid("", "x").Should().BeFalse();
        Valid("   ", "x").Should().BeFalse();
        Valid("admin", null).Should().BeFalse();
        Valid("admin", "").Should().BeFalse();
    }

    [Fact]
    public void OverTheMaximum_IsRejected()
    {
        Valid(new string('a', 51), "x").Should().BeFalse();
        Valid("admin", new string('p', 129)).Should().BeFalse();
        Valid("admin", new string('p', 1_000_000)).Should().BeFalse();
    }

    [Theory]
    [InlineData("ad min")]
    [InlineData("admin\n")]
    [InlineData("admin\r\nINFO forged log line")]
    [InlineData("admin\t")]
    [InlineData("adm/in")]
    [InlineData("adm'in")]
    [InlineData("adm;in")]
    [InlineData("adm\0in")]
    [InlineData("أدمن")]
    public void WhitespaceControlCharactersAndSymbols_AreNotAllowedInAUserName(string userName) =>
        Valid(userName, "x").Should().BeFalse();
}
