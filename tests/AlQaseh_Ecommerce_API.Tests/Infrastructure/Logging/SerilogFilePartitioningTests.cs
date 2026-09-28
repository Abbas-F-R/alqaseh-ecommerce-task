using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using FluentAssertions;
using Microsoft.Extensions.Logging;
using Serilog;
using Serilog.Events;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Infrastructure.Logging;

public class SerilogFilePartitioningTests : IDisposable
{
    private readonly string _testLogsFolder;
    private readonly string _errorsFolder;

    public SerilogFilePartitioningTests()
    {
        _testLogsFolder = Path.Combine(Path.GetTempPath(), "TestLogs_" + Guid.NewGuid().ToString("N"));
        _errorsFolder = Path.Combine(_testLogsFolder, "errors");
        Directory.CreateDirectory(_testLogsFolder);
        Directory.CreateDirectory(_errorsFolder);
    }

    public void Dispose()
    {
        Log.CloseAndFlush();
        try
        {
            if (Directory.Exists(_testLogsFolder))
            {
                Directory.Delete(_testLogsFolder, recursive: true);
            }
        }
        catch
        {
            // Ignore cleanup errors in test environment
        }
    }

    [Fact]
    public async Task PartitionedSinks_RouteLogsToDedicatedFilesBasedOnErrorType()
    {
        var outputTemplate = "[{Timestamp:HH:mm:ss} {Level:u3}] {Message:lj}{NewLine}{Exception}";

        var loggerConfig = new LoggerConfiguration()
            .MinimumLevel.Information()
            .Enrich.FromLogContext()
            // Main app log
            .WriteTo.File(
                path: Path.Combine(_testLogsFolder, "app-.log"),
                rollingInterval: RollingInterval.Day,
                outputTemplate: outputTemplate)
            // Consolidated errors
            .WriteTo.Logger(sub => sub
                .Filter.ByIncludingOnly(e => e.Level >= LogEventLevel.Warning)
                .WriteTo.File(
                    path: Path.Combine(_errorsFolder, "all-errors-.log"),
                    rollingInterval: RollingInterval.Day,
                    outputTemplate: outputTemplate))
            // Database errors
            .WriteTo.Logger(sub => sub
                .Filter.ByIncludingOnly(e => MatchesErrorType(e, ErrorType.Database) || (e.Exception != null && ErrorClassifier.IsDatabaseException(e.Exception)))
                .WriteTo.File(
                    path: Path.Combine(_errorsFolder, "database-errors-.log"),
                    rollingInterval: RollingInterval.Day,
                    outputTemplate: outputTemplate))
            // Security errors
            .WriteTo.Logger(sub => sub
                .Filter.ByIncludingOnly(e => MatchesErrorType(e, ErrorType.Security) || (e.Exception != null && ErrorClassifier.IsSecurityException(e.Exception)))
                .WriteTo.File(
                    path: Path.Combine(_errorsFolder, "security-errors-.log"),
                    rollingInterval: RollingInterval.Day,
                    outputTemplate: outputTemplate))
            // Validation errors
            .WriteTo.Logger(sub => sub
                .Filter.ByIncludingOnly(e => MatchesErrorType(e, ErrorType.Validation) || (e.Exception != null && ErrorClassifier.IsValidationException(e.Exception)))
                .WriteTo.File(
                    path: Path.Combine(_errorsFolder, "validation-errors-.log"),
                    rollingInterval: RollingInterval.Day,
                    outputTemplate: outputTemplate))
            // Unhandled errors
            .WriteTo.Logger(sub => sub
                .Filter.ByIncludingOnly(e => MatchesErrorType(e, ErrorType.Unhandled) ||
                                             (e.Level >= LogEventLevel.Error && e.Exception != null &&
                                              !ErrorClassifier.IsDatabaseException(e.Exception) &&
                                              !ErrorClassifier.IsSecurityException(e.Exception) &&
                                              !ErrorClassifier.IsValidationException(e.Exception)))
                .WriteTo.File(
                    path: Path.Combine(_errorsFolder, "unhandled-errors-.log"),
                    rollingInterval: RollingInterval.Day,
                    outputTemplate: outputTemplate));

        var serilogLogger = loggerConfig.CreateLogger();
        using var factory = LoggerFactory.Create(builder => builder.AddSerilog(serilogLogger, dispose: true));
        var logger = factory.CreateLogger<SerilogFilePartitioningTests>();

        // 1. Emit typed events
        logger.LogDatabaseError(new Exception("SQL Server Named Pipes connection refused"), "Database query failed");
        logger.LogSecurityError(new UnauthorizedAccessException("Forbidden access attempt"), "Security token rejected");
        logger.LogValidationError(new ArgumentException("Invalid field format"), "Validation check failed");
        logger.LogUnhandledError(new NullReferenceException("Unexpected object crash"), "Unhandled runtime fault");

        // Flush logger to ensure disk flush
        serilogLogger.Dispose();

        // 2. Read and verify written log files
        var dbFiles = Directory.GetFiles(_errorsFolder, "database-errors-*.log");
        var secFiles = Directory.GetFiles(_errorsFolder, "security-errors-*.log");
        var valFiles = Directory.GetFiles(_errorsFolder, "validation-errors-*.log");
        var unhFiles = Directory.GetFiles(_errorsFolder, "unhandled-errors-*.log");
        var allErrFiles = Directory.GetFiles(_errorsFolder, "all-errors-*.log");
        var appFiles = Directory.GetFiles(_testLogsFolder, "app-*.log");

        dbFiles.Should().NotBeEmpty();
        secFiles.Should().NotBeEmpty();
        valFiles.Should().NotBeEmpty();
        unhFiles.Should().NotBeEmpty();
        allErrFiles.Should().NotBeEmpty();
        appFiles.Should().NotBeEmpty();

        var dbContent = await File.ReadAllTextAsync(dbFiles[0]);
        dbContent.Should().Contain("Database query failed");
        dbContent.Should().NotContain("Security token rejected");

        var secContent = await File.ReadAllTextAsync(secFiles[0]);
        secContent.Should().Contain("Security token rejected");
        secContent.Should().NotContain("Database query failed");

        var valContent = await File.ReadAllTextAsync(valFiles[0]);
        valContent.Should().Contain("Validation check failed");

        var unhContent = await File.ReadAllTextAsync(unhFiles[0]);
        unhContent.Should().Contain("Unhandled runtime fault");

        var allErrContent = await File.ReadAllTextAsync(allErrFiles[0]);
        allErrContent.Should().Contain("Database query failed");
        allErrContent.Should().Contain("Security token rejected");
        allErrContent.Should().Contain("Validation check failed");
        allErrContent.Should().Contain("Unhandled runtime fault");
    }

    private static bool MatchesErrorType(LogEvent logEvent, ErrorType errorType)
    {
        if (logEvent.Properties.TryGetValue("ErrorType", out var propertyValue))
        {
            return propertyValue.ToString().Contains(errorType.ToString(), StringComparison.OrdinalIgnoreCase);
        }
        return false;
    }
}
