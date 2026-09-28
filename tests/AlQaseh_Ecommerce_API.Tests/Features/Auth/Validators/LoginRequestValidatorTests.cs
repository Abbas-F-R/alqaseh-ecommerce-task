using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Features.Auth.Validators;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Auth.Validators;

public class LoginRequestValidatorTests
{
    private readonly LoginRequestValidator _validator = new();

    [Fact]
    public void ValidRequest_Passes() =>
        _validator.Validate(new LoginRequest { UserName = "admin", Password = "Admin123!" }).IsValid.Should().BeTrue();

    [Theory]
    [InlineData("", "password")]
    [InlineData("   ", "password")]
    [InlineData("admin", "")]
    public void MissingUserNameOrPassword_Fails(string userName, string password) =>
        _validator.Validate(new LoginRequest { UserName = userName, Password = password }).IsValid.Should().BeFalse();

    [Fact]
    public void ShortPassword_IsNotRejectedHere_SoTheClientCannotProbePasswordRules() =>
        _validator.Validate(new LoginRequest { UserName = "admin", Password = "x" }).IsValid.Should().BeTrue();

    [Fact]
    public void OverlongUserName_Fails() =>
        _validator.Validate(new LoginRequest { UserName = new string('a', 51), Password = "x" }).IsValid.Should().BeFalse();
}
