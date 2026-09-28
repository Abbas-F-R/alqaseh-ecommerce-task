using System.Security.Claims;
using AlQaseh_Ecommerce_API.Shared.Base;
using AlQaseh_Ecommerce_API.Shared.Constants;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Shared.Base;

public class CurrentUserTests
{
    private static CurrentUser For(HttpContext? context)
    {
        var accessor = new Mock<IHttpContextAccessor>();
        accessor.Setup(a => a.HttpContext).Returns(context);
        return new CurrentUser(accessor.Object);
    }

    private static DefaultHttpContext Context(string? acceptLanguage = null, params Claim[] claims)
    {
        var context = new DefaultHttpContext { User = new ClaimsPrincipal(new ClaimsIdentity(claims, "Test")) };
        if (acceptLanguage is not null)
            context.Request.Headers.AcceptLanguage = acceptLanguage;
        return context;
    }

    [Fact]
    public void ReadsIdNameAndRoleFromTheTokenClaims()
    {
        var user = For(Context(null,
            new Claim(ClaimNames.UserId, "42"), new Claim(ClaimNames.UserName, "customer1"), new Claim(ClaimNames.Role, "Customer")));

        (user.UserId, user.UserName, user.Role).Should().Be((42, "customer1", "Customer"));
    }

    [Fact]
    public void WithoutClaims_ReturnsEmptyValues()
    {
        var user = For(Context());

        (user.UserId, user.UserName, user.Role).Should().Be((0, "", ""));
    }

    [Fact]
    public void WithoutAnHttpContext_ReturnsEmptyValues()
    {
        var user = For(null);

        (user.UserId, user.Role, user.Lang).Should().Be((0, "", "en"));
    }

    [Fact]
    public void NonNumericId_IsZero() =>
        For(Context(null, new Claim(ClaimNames.UserId, "abc"))).UserId.Should().Be(0);

    [Theory]
    [InlineData("ar", "ar")]
    [InlineData("ar-IQ,ar;q=0.9,en;q=0.8", "ar")]
    [InlineData("AR", "ar")]
    [InlineData("en-US", "en")]
    [InlineData("fr", "en")]
    [InlineData("", "en")]
    public void Language_ComesFromAcceptLanguage_DefaultingToEnglish(string header, string expected) =>
        For(Context(header)).Lang.Should().Be(expected);

    [Fact]
    public void NoAcceptLanguageHeader_MeansEnglish() => For(Context()).Lang.Should().Be("en");
}
