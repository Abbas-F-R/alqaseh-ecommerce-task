using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Features.Auth.Repositories;
using AlQaseh_Ecommerce_API.Features.Auth.Services;
using AlQaseh_Ecommerce_API.Shared.Constants;
using AlQaseh_Ecommerce_API.Shared.Extensions;
using AlQaseh_Ecommerce_API.Shared.Utils;
using AlQaseh_Ecommerce_API.Tests.TestSupport;
using FluentAssertions;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Tokens;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Auth.Services;

public class AuthServiceTests
{
    private static readonly string Key = new('k', 48);
    private readonly Mock<IUserRepository> _users = new();
    private readonly JwtSettings _jwt = new(Key, TimeSpan.FromMinutes(30));
    private readonly AuthService _service;

    private readonly UserDto _customer = new()
    {
        Id = 7, UserName = "customer1", FullName = "Customer One", Role = Roles.Customer,
        PasswordHash = PasswordHasher.Hash("Customer123!")
    };

    public AuthServiceTests()
    {
        _users.Setup(r => r.GetByUserName("customer1")).ReturnsAsync(_customer);
        _service = new AuthService(_users.Object, _jwt, new FixedTimeProvider(TestHelpers.Now));
    }

    [Fact]
    public async Task Login_WithValidCredentials_ReturnsTokenForTheUser()
    {
        var result = await _service.Login(new LoginRequest { UserName = "customer1", Password = "Customer123!" });

        result.IsSuccess.Should().BeTrue();
        result.Data!.UserId.Should().Be(7);
        result.Data.Role.Should().Be("Customer");
        result.Data.Type.Should().Be("Bearer");
        result.Data.ExpiresAt.Should().Be(TestHelpers.Now.UtcDateTime.AddMinutes(30));
    }

    [Fact]
    public async Task Login_Token_IsSignedAndCarriesIdNameAndRole()
    {
        var result = await _service.Login(new LoginRequest { UserName = "customer1", Password = "Customer123!" });

        var validation = await new JsonWebTokenHandler().ValidateTokenAsync(result.Data!.Token, new TokenValidationParameters
        {
            ValidIssuer = JwtSettings.Issuer,
            ValidAudience = JwtSettings.Audience,
            IssuerSigningKey = _jwt.SigningKey,
            ValidateLifetime = false
        });

        validation.IsValid.Should().BeTrue();
        var token = (JsonWebToken)validation.SecurityToken;
        token.Subject.Should().Be("7");
        token.GetClaim(ClaimNames.UserName).Value.Should().Be("customer1");
        token.GetClaim(ClaimNames.Role).Value.Should().Be("Customer");
    }

    [Fact]
    public async Task Login_TrimsTheUserName()
    {
        var result = await _service.Login(new LoginRequest { UserName = "  customer1  ", Password = "Customer123!" });

        result.IsSuccess.Should().BeTrue();
    }

    [Fact]
    public async Task Login_WithWrongPassword_ReturnsInvalidCredentials()
    {
        var result = await _service.Login(new LoginRequest { UserName = "customer1", Password = "wrong" });

        result.IsSuccess.Should().BeFalse();
        result.Error.Should().Be(Messages.InvalidCredentials);
    }

    [Fact]
    public async Task Login_WithUnknownUser_ReturnsTheSameErrorAsAWrongPassword()
    {
        _users.Setup(r => r.GetByUserName("ghost")).ReturnsAsync((UserDto?)null);

        var result = await _service.Login(new LoginRequest { UserName = "ghost", Password = "whatever" });

        result.Error.Should().Be(Messages.InvalidCredentials);
    }
}
