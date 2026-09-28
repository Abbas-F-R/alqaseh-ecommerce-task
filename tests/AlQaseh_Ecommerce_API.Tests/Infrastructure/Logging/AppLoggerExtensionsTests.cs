using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using Microsoft.Extensions.Logging;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Infrastructure.Logging;

public class AppLoggerExtensionsTests
{
    [Fact]
    public void LogDatabaseError_InvokesLoggerWithErrorMessage()
    {
        var loggerMock = new Mock<ILogger>();
        var ex = new Exception("Database failure");

        loggerMock.Object.LogDatabaseError(ex, "Failed to connect to database");

        loggerMock.Verify(
            x => x.Log(
                LogLevel.Error,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("[Database]") && v.ToString()!.Contains("Failed to connect")),
                ex,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);
    }

    [Fact]
    public void LogSecurityError_InvokesLoggerWithSecurityTag()
    {
        var loggerMock = new Mock<ILogger>();
        var ex = new UnauthorizedAccessException("Forbidden access");

        loggerMock.Object.LogSecurityError(ex, "Token expired for user {UserId}", 42);

        loggerMock.Verify(
            x => x.Log(
                LogLevel.Error,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("[Security]") && v.ToString()!.Contains("Token expired")),
                ex,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);
    }

    [Fact]
    public void LogValidationError_InvokesLoggerWithValidationTag()
    {
        var loggerMock = new Mock<ILogger>();
        var ex = new ArgumentException("Invalid field value");

        loggerMock.Object.LogValidationError(ex, "Validation failure detected: {Field}", "Email");

        loggerMock.Verify(
            x => x.Log(
                LogLevel.Error,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("[Validation]")),
                ex,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);
    }

    [Fact]
    public void LogUnhandledError_InvokesLoggerWithUnhandledTag()
    {
        var loggerMock = new Mock<ILogger>();
        var ex = new NullReferenceException("Unexpected crash");

        loggerMock.Object.LogUnhandledError(ex, "System crashed unexpectedly");

        loggerMock.Verify(
            x => x.Log(
                LogLevel.Error,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("[Unhandled]")),
                ex,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);
    }
}
