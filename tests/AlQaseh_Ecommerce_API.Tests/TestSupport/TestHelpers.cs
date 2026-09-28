using AlQaseh_Ecommerce_API.Shared.Base;
using Microsoft.AspNetCore.Mvc;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.TestSupport;

/// <summary>A clock that always returns the same instant.</summary>
public sealed class FixedTimeProvider(DateTimeOffset now) : TimeProvider
{
    public override DateTimeOffset GetUtcNow() => now;
}

public static class TestHelpers
{
    public static readonly DateTimeOffset Now = new(2026, 6, 15, 12, 0, 0, TimeSpan.Zero);

    /// <summary>Gives a controller a signed-in caller without an HTTP context.</summary>
    public static T WithUser<T>(this T controller, long id, string userName, string role, string lang = "en") where T : BaseController
    {
        var user = new Mock<ICurrentUser>();
        user.Setup(u => u.UserId).Returns(id);
        user.Setup(u => u.UserName).Returns(userName);
        user.Setup(u => u.Role).Returns(role);
        user.Setup(u => u.Lang).Returns(lang);
        controller.SetCurrentUser(user.Object);
        return controller;
    }

    /// <summary>The problem details carried by an error response, with its stable error code.</summary>
    public static (int Status, string? Code, string? Detail) Problem(this IActionResult result)
    {
        var objectResult = Assert.IsAssignableFrom<ObjectResult>(result);
        var problem = Assert.IsType<ProblemDetails>(objectResult.Value);
        return (objectResult.StatusCode ?? 0, problem.Extensions.TryGetValue("code", out var code) ? code?.ToString() : null, problem.Detail);
    }
}
