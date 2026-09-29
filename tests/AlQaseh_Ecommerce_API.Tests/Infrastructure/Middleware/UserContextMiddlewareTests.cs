using System.Security.Claims;
using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Features.Auth.Repositories;
using AlQaseh_Ecommerce_API.Infrastructure.Middleware;
using AlQaseh_Ecommerce_API.Shared.Constants;
using FluentAssertions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging.Abstractions;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Infrastructure.Middleware;

public class UserContextMiddlewareTests
{
    private static async Task<(bool NextCalled, DefaultHttpContext Context)> RunAsync(ClaimsPrincipal user, IUserRepository users, Endpoint? endpoint = null)
    {
        var context = new DefaultHttpContext { User = user };
        context.Response.Body = new MemoryStream();
        if (endpoint is not null)
            context.SetEndpoint(endpoint);
        var nextCalled = false;
        await new UserContextMiddleware(_ => { nextCalled = true; return Task.CompletedTask; }, NullLogger<UserContextMiddleware>.Instance)
            .InvokeAsync(context, users);
        return (nextCalled, context);
    }

    private static ClaimsPrincipal Authenticated(params Claim[] claims) => new(new ClaimsIdentity(claims, "TestAuth"));

    private static Claim[] Token(string id, string role) => [new Claim(ClaimNames.UserId, id), new Claim(ClaimNames.Role, role)];

    private static IUserRepository Users(UserDto? user)
    {
        var repository = new Mock<IUserRepository>();
        repository.Setup(r => r.GetById(It.IsAny<long>())).ReturnsAsync(user);
        return repository.Object;
    }

    [Fact]
    public async Task TokenOfAnExistingUserInTheSameRole_Proceeds()
    {
        var (nextCalled, context) = await RunAsync(Authenticated(Token("42", "Customer")), Users(new UserDto { Id = 42, Role = "Customer" }));

        nextCalled.Should().BeTrue();
        context.Response.StatusCode.Should().Be(200);
    }

    [Fact]
    public async Task TokenOfAUserWhoNoLongerExists_Returns401AndStops()
    {
        var (nextCalled, context) = await RunAsync(Authenticated(Token("42", "Customer")), Users(null));

        nextCalled.Should().BeFalse();
        context.Response.StatusCode.Should().Be(401);
        context.Response.ContentType.Should().StartWith("application/problem+json");
    }

    [Theory]
    [InlineData("Customer", "Admin")]   // promoted after the token was issued
    [InlineData("Admin", "Customer")]   // demoted after the token was issued
    [InlineData("Admin", "admin")]      // the role is compared exactly
    public async Task TokenWhoseRoleNoLongerMatchesTheAccount_Returns401AndStops(string tokenRole, string accountRole)
    {
        var (nextCalled, context) = await RunAsync(Authenticated(Token("42", tokenRole)), Users(new UserDto { Id = 42, Role = accountRole }));

        nextCalled.Should().BeFalse();
        context.Response.StatusCode.Should().Be(401);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("not-a-number")]
    public async Task TokenWithoutValidUserId_Returns401AndStops_WithoutTouchingTheDatabase(string? userId)
    {
        var claims = userId is null ? [new Claim(ClaimNames.UserName, "x")] : new[] { new Claim(ClaimNames.UserId, userId) };
        var repository = new Mock<IUserRepository>(MockBehavior.Strict);

        var (nextCalled, context) = await RunAsync(Authenticated(claims), repository.Object);

        nextCalled.Should().BeFalse();
        context.Response.StatusCode.Should().Be(401);
    }

    [Fact]
    public async Task UnauthenticatedRequest_IsLeftToTheAuthorizationLayer_WithoutTouchingTheDatabase()
    {
        var repository = new Mock<IUserRepository>(MockBehavior.Strict);

        var (nextCalled, context) = await RunAsync(new ClaimsPrincipal(new ClaimsIdentity()), repository.Object);

        nextCalled.Should().BeTrue();
        context.Response.StatusCode.Should().Be(200);
    }

    [Fact]
    public async Task AnonymousEndpoint_IsNotChecked()
    {
        var repository = new Mock<IUserRepository>(MockBehavior.Strict);
        var endpoint = new Endpoint(_ => Task.CompletedTask, new EndpointMetadataCollection(new AllowAnonymousAttribute()), "login");

        var (nextCalled, _) = await RunAsync(Authenticated(Token("42", "Customer")), repository.Object, endpoint);

        nextCalled.Should().BeTrue();
    }
}
