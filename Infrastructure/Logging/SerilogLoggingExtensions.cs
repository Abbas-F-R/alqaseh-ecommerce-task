using Serilog;
using Serilog.Events;

namespace AlQaseh_Ecommerce_API.Infrastructure.Logging;

/// <summary>
/// Configures and registers Serilog structured file logging partitioned by error types.
/// Incorporates asynchronous non-blocking disk I/O, daily rolling partitions, retention limits,
/// and automated error categorization into specialized log sinks.
/// </summary>
public static class SerilogLoggingExtensions
{
    /// <summary>
    /// Configures Serilog as the primary logging provider with partitioned error-type file sinks.
    /// </summary>
    public static WebApplicationBuilder AddSerilogLogging(this WebApplicationBuilder builder)
    {
        var loggingOptions = builder.Configuration
            .GetSection(LoggingOptions.SectionName)
            .Get<LoggingOptions>() ?? new LoggingOptions();

        var logsFolder = Path.Combine(builder.Environment.ContentRootPath, loggingOptions.LogsFolder);
        var errorsFolder = Path.Combine(logsFolder, "errors");

        Directory.CreateDirectory(logsFolder);
        Directory.CreateDirectory(errorsFolder);

        var loggerConfig = new LoggerConfiguration()
            .MinimumLevel.Information()
            .MinimumLevel.Override("Microsoft", LogEventLevel.Warning)
            .MinimumLevel.Override("Microsoft.Hosting.Lifetime", LogEventLevel.Information)
            .MinimumLevel.Override("System", LogEventLevel.Warning)
            .Enrich.FromLogContext()
            // 1. Console Output for developer visibility
            .WriteTo.Console(
                outputTemplate: "[{Timestamp:HH:mm:ss} {Level:u3}] {Message:lj}{NewLine}{Exception}")
            // 2. Global Rolling Application Log (all Information and above)
            .AddPartitionedSink(Path.Combine(logsFolder, "app-.log"), loggingOptions)
            // 3. Consolidated Errors Log (all Warning, Error, Fatal)
            .AddPartitionedSink(Path.Combine(errorsFolder, "all-errors-.log"), loggingOptions,
                e => e.Level >= LogEventLevel.Warning)
            // 4. Dedicated Database Errors Log (SQL Server, connectivity, query execution)
            .AddPartitionedSink(Path.Combine(errorsFolder, "database-errors-.log"), loggingOptions,
                e => MatchesErrorType(e, ErrorType.Database) || (e.Exception != null && ErrorClassifier.IsDatabaseException(e.Exception)))
            // 5. Dedicated Security Errors Log (Authentication, Authorization, Tokens)
            .AddPartitionedSink(Path.Combine(errorsFolder, "security-errors-.log"), loggingOptions,
                e => MatchesErrorType(e, ErrorType.Security) || (e.Exception != null && ErrorClassifier.IsSecurityException(e.Exception)))
            // 6. Dedicated Validation Errors Log (Input rules, malformed payloads)
            .AddPartitionedSink(Path.Combine(errorsFolder, "validation-errors-.log"), loggingOptions,
                e => MatchesErrorType(e, ErrorType.Validation) || (e.Exception != null && ErrorClassifier.IsValidationException(e.Exception)))
            // 7. Dedicated Unhandled / Runtime Errors Log
            .AddPartitionedSink(Path.Combine(errorsFolder, "unhandled-errors-.log"), loggingOptions,
                e => MatchesErrorType(e, ErrorType.Unhandled) ||
                     (e.Level >= LogEventLevel.Error && e.Exception != null &&
                      !ErrorClassifier.IsDatabaseException(e.Exception) &&
                      !ErrorClassifier.IsSecurityException(e.Exception) &&
                      !ErrorClassifier.IsValidationException(e.Exception) &&
                      !ErrorClassifier.IsNotFoundException(e.Exception)));

        Log.Logger = loggerConfig.CreateLogger();
        builder.Host.UseSerilog();

        return builder;
    }

    /// <summary>
    /// Configures an asynchronous rolling file sink, optionally filtered by predicate.
    /// Eliminates boilerplate configuration across partitioned sinks.
    /// </summary>
    public static LoggerConfiguration AddPartitionedSink(
        this LoggerConfiguration config,
        string path,
        LoggingOptions options,
        Func<LogEvent, bool>? filter = null)
    {
        Action<LoggerConfiguration> writeToFile = c => c.WriteTo.Async(a => a.File(
            path: path,
            rollingInterval: RollingInterval.Day,
            retainedFileCountLimit: options.RetainedFileCountLimit,
            fileSizeLimitBytes: options.FileSizeLimitBytes,
            rollOnFileSizeLimit: true,
            outputTemplate: options.OutputTemplate,
            shared: true));

        if (filter == null)
        {
            writeToFile(config);
            return config;
        }

        return config.WriteTo.Logger(sub =>
        {
            sub.Filter.ByIncludingOnly(filter);
            writeToFile(sub);
        });
    }

    private static bool MatchesErrorType(LogEvent logEvent, ErrorType errorType) =>
        logEvent.Properties.TryGetValue("ErrorType", out var propertyValue) &&
        propertyValue.ToString().Contains(errorType.ToString(), StringComparison.OrdinalIgnoreCase);
}
