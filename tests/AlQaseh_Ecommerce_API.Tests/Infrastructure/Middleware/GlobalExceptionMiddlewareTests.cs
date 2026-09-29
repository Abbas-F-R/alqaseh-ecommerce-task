using System.Text.Json;
using AlQaseh_Ecommerce_API.Infrastructure.Middleware;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging.Abstractions;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Infrastructure.Middleware;

public class GlobalExceptionMiddlewareTests
{
    private static async Task<(DefaultHttpContext Context, JsonElement Body)> RunAsync(string environment, Exception failure)
    {
        var context = new DefaultHttpContext();
        context.Response.Body = new MemoryStream();

        var env = new Mock<IHostEnvironment>();
        env.Setup(e => e.EnvironmentName).Returns(environment);

        var middleware = new GlobalExceptionMiddleware(_ => throw failure, NullLogger<GlobalExceptionMiddleware>.Instance, env.Object);
        await middleware.InvokeAsync(context);

        context.Response.Body.Seek(0, SeekOrigin.Begin);
        using var reader = new StreamReader(context.Response.Body);
        return (context, JsonDocument.Parse(await reader.ReadToEndAsync()).RootElement.Clone());
    }

    [Fact]
    public async Task InDevelopment_ReturnsDiagnosticProblemDetails()
    {
        var (context, root) = await RunAsync(Environments.Development, new InvalidOperationException("Detailed dev error!"));

        context.Response.StatusCode.Should().Be(500);
        context.Response.ContentType.Should().StartWith("application/problem+json");
        root.GetProperty("status").GetInt32().Should().Be(500);
        root.GetProperty("code").GetString().Should().Be("InternalServerError");
        root.GetProperty("detail").GetString().Should().Be("Detailed dev error!");
        root.GetProperty("exceptionType").GetString().Should().Be("System.InvalidOperationException");
        root.GetProperty("errorType").GetString().Should().Be("Unhandled");
        root.TryGetProperty("stackTrace", out _).Should().BeTrue();
    }

    [Fact]
    public async Task OutsideDevelopment_ReturnsGenericProblemDetailsWithoutInternals()
    {
        var (context, root) = await RunAsync(Environments.Production, new InvalidOperationException("Server=db;Password=secret"));

        context.Response.StatusCode.Should().Be(500);
        context.Response.ContentType.Should().StartWith("application/problem+json");
        root.GetProperty("code").GetString().Should().Be("InternalServerError");
        root.GetProperty("detail").GetString().Should().Contain("unexpected error");
        root.GetProperty("traceId").GetString().Should().NotBeNullOrEmpty();
        root.GetRawText().Should().NotContain("secret").And.NotContain("InvalidOperationException");
        root.TryGetProperty("stackTrace", out _).Should().BeFalse();
    }

    [Fact]
    public async Task ARequestKestrelRefused_KeepsItsStatus_ItIsNotA500()
    {
        var (context, root) = await RunAsync(Environments.Production,
            new BadHttpRequestException("Request body too large. The max request body size is 30000000 bytes.", StatusCodes.Status413PayloadTooLarge));

        context.Response.StatusCode.Should().Be(413);
        root.GetProperty("status").GetInt32().Should().Be(413);
        root.GetRawText().Should().NotContain("30000000");
    }

    [Fact]
    public async Task ABodyThatIsNotValidInItsDeclaredCharset_Is400_NotA500()
    {
        var (context, root) = await RunAsync(Environments.Production, new System.Text.DecoderFallbackException("Unable to translate bytes [7D]"));

        context.Response.StatusCode.Should().Be(400);
        root.GetProperty("status").GetInt32().Should().Be(400);
        root.GetRawText().Should().NotContain("7D");
    }

    [Fact]
    public async Task WhenNothingFails_PassesThrough()
    {
        var context = new DefaultHttpContext();
        var env = new Mock<IHostEnvironment>();
        var called = false;

        await new GlobalExceptionMiddleware(_ => { called = true; return Task.CompletedTask; }, NullLogger<GlobalExceptionMiddleware>.Instance, env.Object)
            .InvokeAsync(context);

        called.Should().BeTrue();
        context.Response.StatusCode.Should().Be(200);
    }
}
