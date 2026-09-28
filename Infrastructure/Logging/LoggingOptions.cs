namespace AlQaseh_Ecommerce_API.Infrastructure.Logging;

/// <summary>
/// Strong-typed configuration options for the file-based logging infrastructure.
/// </summary>
public sealed class LoggingOptions
{
    public const string SectionName = "LoggingOptions";

    /// <summary>
    /// Relative or absolute root folder where log files are written. Defaults to "Logs".
    /// </summary>
    public string LogsFolder { get; set; } = "Logs";

    /// <summary>
    /// Number of daily rolling log files to retain before automatic purging. Defaults to 30 days.
    /// </summary>
    public int RetainedFileCountLimit { get; set; } = 30;

    /// <summary>
    /// Maximum file size in bytes before triggering an intra-day roll. Defaults to 10 MB (10,485,760 bytes).
    /// </summary>
    public long FileSizeLimitBytes { get; set; } = 10 * 1024 * 1024;

    /// <summary>
    /// Custom message output template formatting timestamp, level, trace identifier, error category, message, and exception stack trace.
    /// </summary>
    public string OutputTemplate { get; set; } =
        "[{Timestamp:yyyy-MM-dd HH:mm:ss.fff zzz} {Level:u3}] [{TraceId}] {Message:lj}{NewLine}{Exception}";
}
