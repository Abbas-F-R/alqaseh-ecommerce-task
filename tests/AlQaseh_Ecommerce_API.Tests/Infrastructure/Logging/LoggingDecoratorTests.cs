using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using FluentAssertions;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Infrastructure.Logging;

public interface IDecoratorTestService
{
    string SyncOperation(string input);
    Task<string> AsyncVoidOperation(string input);
    Task<ServiceResult<string>> AsyncResultOperation(bool shouldSucceed);
    Task FailingOperation();
}

public class LoggingDecoratorTests
{
    private readonly Mock<IDecoratorTestService> _targetMock = new();
    private readonly Mock<ILogger<IDecoratorTestService>> _loggerMock = new();

    [Fact]
    public void SyncOperation_LogsInvocationAndCompletion()
    {
        _targetMock.Setup(s => s.SyncOperation("hello")).Returns("world");

        var proxy = LoggingDecorator<IDecoratorTestService>.Create(_targetMock.Object, _loggerMock.Object);

        var result = proxy.SyncOperation("hello");

        result.Should().Be("world");

        _loggerMock.Verify(
            x => x.Log(
                LogLevel.Information,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("Invoking SyncOperation")),
                null,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);

        _loggerMock.Verify(
            x => x.Log(
                LogLevel.Information,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("Completed SyncOperation")),
                null,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);
    }

    [Fact]
    public async Task AsyncResultOperation_OnSuccess_LogsCompletionAndReturnsResult()
    {
        _targetMock.Setup(s => s.AsyncResultOperation(true))
            .ReturnsAsync(ServiceResult<string>.Ok("SuccessData"));

        var proxy = LoggingDecorator<IDecoratorTestService>.Create(_targetMock.Object, _loggerMock.Object);

        var result = await proxy.AsyncResultOperation(true);

        result.IsSuccess.Should().BeTrue();
        result.Data.Should().Be("SuccessData");

        _loggerMock.Verify(
            x => x.Log(
                LogLevel.Information,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("Invoking AsyncResultOperation")),
                null,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);

        _loggerMock.Verify(
            x => x.Log(
                LogLevel.Information,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("Completed AsyncResultOperation")),
                null,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);
    }

    [Fact]
    public async Task AsyncResultOperation_OnBusinessFailure_LogsWarning()
    {
        _targetMock.Setup(s => s.AsyncResultOperation(false))
            .ReturnsAsync(ServiceResult<string>.Failure(Messages.ProductNotFound));

        var proxy = LoggingDecorator<IDecoratorTestService>.Create(_targetMock.Object, _loggerMock.Object);

        var result = await proxy.AsyncResultOperation(false);

        result.IsSuccess.Should().BeFalse();

        _loggerMock.Verify(
            x => x.Log(
                LogLevel.Warning,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("returned business failure")),
                null,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);
    }

    [Fact]
    public async Task FailingOperation_LogsTypedErrorAndRethrows()
    {
        var exception = new ArgumentException("Invalid argument provided");
        _targetMock.Setup(s => s.FailingOperation()).ThrowsAsync(exception);

        var proxy = LoggingDecorator<IDecoratorTestService>.Create(_targetMock.Object, _loggerMock.Object);

        var act = async () => await proxy.FailingOperation();

        await act.Should().ThrowAsync<ArgumentException>().WithMessage("Invalid argument provided");

        _loggerMock.Verify(
            x => x.Log(
                LogLevel.Error,
                It.IsAny<EventId>(),
                It.Is<It.IsAnyType>((v, t) => v.ToString()!.Contains("[Validation]") && v.ToString()!.Contains("Exception in FailingOperation")),
                exception,
                It.IsAny<Func<It.IsAnyType, Exception?, string>>()),
            Times.Once);
    }

    [Fact]
    public async Task DecorateWithLogging_IntegratesWithDependencyInjection()
    {
        var services = new ServiceCollection();
        services.AddLogging();
        services.AddScoped<IDecoratorTestService>(_ => _targetMock.Object);
        services.DecorateWithLogging<IDecoratorTestService>();

        var provider = services.BuildServiceProvider();
        var resolved = provider.GetRequiredService<IDecoratorTestService>();

        resolved.Should().NotBeNull();
        _targetMock.Setup(s => s.SyncOperation("di_test")).Returns("di_response");

        var response = resolved.SyncOperation("di_test");
        response.Should().Be("di_response");
    }
}
