using AlQaseh_Ecommerce_API.Features.Auth.Controllers;
using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Features.Auth.Services;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using AlQaseh_Ecommerce_API.Tests.TestSupport;
using FluentAssertions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Auth.Controllers;

public class AuthControllerTests
{
    private readonly Mock<IAuthService> _authService = new();
    private readonly AuthController _controller;

    public AuthControllerTests()
    {
        _controller = new AuthController(_authService.Object).WithUser(0, "", "");
    }

    [Fact]
    public void Login_IsAnonymous()
    {
        typeof(AuthController).GetMethod(nameof(AuthController.Login))!
            .GetCustomAttributes(typeof(AllowAnonymousAttribute), false).Should().NotBeEmpty();
    }

    [Fact]
    public async Task Login_WithValidCredentials_Returns200WithTheToken()
    {
        var request = new LoginRequest { UserName = "admin", Password = "Admin123!" };
        var response = new LoginResponse { UserId = 1, UserName = "admin", Role = "Admin", Token = "jwt", ExpiresAt = DateTime.UtcNow.AddHours(1) };
        _authService.Setup(s => s.Login(request)).ReturnsAsync(ServiceResult<LoginResponse>.Ok(response));

        var result = await _controller.Login(request);

        var ok = result.Result.Should().BeOfType<OkObjectResult>().Subject;
        ok.Value.Should().BeEquivalentTo(response);
    }

    [Fact]
    public async Task Login_WithWrongCredentials_Returns401WithTheErrorCode()
    {
        _authService.Setup(s => s.Login(It.IsAny<LoginRequest>()))
            .ReturnsAsync(ServiceResult<LoginResponse>.Failure(Messages.InvalidCredentials));

        var result = await _controller.Login(new LoginRequest { UserName = "admin", Password = "nope" });

        var (status, code, detail) = result.Result!.Problem();
        status.Should().Be(401);
        code.Should().Be("InvalidCredentials");
        detail.Should().Be("Invalid username or password.");
    }
}
