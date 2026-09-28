using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Infrastructure.Logging;

public class LoggingOptionsTests
{
    [Fact]
    public void LoggingOptions_DefaultValues_AreSensibleEnterpriseDefaults()
    {
        var options = new LoggingOptions();

        options.LogsFolder.Should().Be("Logs");
        options.RetainedFileCountLimit.Should().Be(30);
        options.FileSizeLimitBytes.Should().Be(10 * 1024 * 1024);
        options.OutputTemplate.Should().Contain("{Timestamp:yyyy-MM-dd HH:mm:ss.fff zzz}");
        options.OutputTemplate.Should().Contain("{Level:u3}");
        options.OutputTemplate.Should().Contain("{Message:lj}");
    }

    [Fact]
    public void LoggingOptions_CanSetCustomValues()
    {
        var options = new LoggingOptions
        {
            LogsFolder = "CustomLogs",
            RetainedFileCountLimit = 14,
            FileSizeLimitBytes = 5 * 1024 * 1024,
            OutputTemplate = "[{Level}] {Message}"
        };

        options.LogsFolder.Should().Be("CustomLogs");
        options.RetainedFileCountLimit.Should().Be(14);
        options.FileSizeLimitBytes.Should().Be(5242880);
        options.OutputTemplate.Should().Be("[{Level}] {Message}");
    }
}
