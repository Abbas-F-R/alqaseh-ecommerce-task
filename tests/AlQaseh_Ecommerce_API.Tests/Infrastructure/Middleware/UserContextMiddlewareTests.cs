using System.Security.Claims;
using AlQaseh_Ecommerce_API.Infrastructure.Middleware;
using AlQaseh_Ecommerce_API.Shared.Constants;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Infrastructure.Middleware;

public class UserContextMiddlewareTests
{
    private static async Task<(bool NextCalled, DefaultHttpContext Context)> RunAsync(ClaimsPrincipal user)
    {
        var context = new DefaultHttpContext { User = user };
        context.Response.Body = new MemoryStream();
        var nextCalled = false;

        await new UserContextMiddleware(_ => { nextCalled = true; return Task.CompletedTask; }, NullLogger<UserContextMiddleware>.Instance)
            .InvokeAsync(context);

        return (nextCalled, context);
    }

    private static ClaimsPrincipal Authenticated(params Claim[] claims) => new(new ClaimsIdentity(claims, "TestAuth"));

    [Fact]
    public async Task TokenWithNumericUserId_Proceeds()
    {
        var (nextCalled, _) = await RunAsync(Authenticated(new Claim(ClaimNames.UserId, "42")));

        nextCalled.Should().BeTrue();
    }

    [Theory]
    [InlineData(null)]
    [InlineData("not-a-number")]
    public async Task TokenWithoutValidUserId_Returns401AndStops(string? userId)
    {
        var claims = userId is null ? [new Claim(ClaimNames.UserName, "x")] : new[] { new Claim(ClaimNames.UserId, userId) };

        var (nextCalled, context) = await RunAsync(Authenticated(claims));

        nextCalled.Should().BeFalse();
        context.Response.StatusCode.Should().Be(401);
        context.Response.ContentType.Should().StartWith("application/problem+json");
    }

    [Fact]
    public async Task UnauthenticatedRequest_IsLeftToTheAuthorizationLayer()
    {
        var (nextCalled, context) = await RunAsync(new ClaimsPrincipal(new ClaimsIdentity()));

        nextCalled.Should().BeTrue();
        context.Response.StatusCode.Should().Be(200);
    }
}
