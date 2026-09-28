using Serilog.Context;

namespace AlQaseh_Ecommerce_API.Infrastructure.Logging;

/// <summary>
/// Extension methods enriching standard <see cref="ILogger"/> instances with structured
/// error type tagging for automated routing to dedicated log files.
/// </summary>
public static class AppLoggerExtensions
{
    /// <summary>
    /// Logs an error enriched with a specific <see cref="ErrorType"/> tag into the Serilog diagnostic context.
    /// </summary>
    public static void LogTypedError(
        this ILogger logger,
        ErrorType errorType,
        Exception? exception,
        string message,
        params object?[] args)
    {
        using (LogContext.PushProperty("ErrorType", errorType.ToString()))
        {
            logger.LogError(exception, $"[{errorType}] {message}", args);
        }
    }

    /// <summary>
    /// Logs a database-related error (SQL Server, connectivity, query execution) into the database-specific log sink.
    /// </summary>
    public static void LogDatabaseError(
        this ILogger logger,
        Exception? exception,
        string message,
        params object?[] args) =>
        logger.LogTypedError(ErrorType.Database, exception, message, args);

    /// <summary>
    /// Logs a security or authentication/authorization failure into the security-specific log sink.
    /// </summary>
    public static void LogSecurityError(
        this ILogger logger,
        Exception? exception,
        string message,
        params object?[] args) =>
        logger.LogTypedError(ErrorType.Security, exception, message, args);

    /// <summary>
    /// Logs an input validation failure into the validation-specific log sink.
    /// </summary>
    public static void LogValidationError(
        this ILogger logger,
        Exception? exception,
        string message,
        params object?[] args) =>
        logger.LogTypedError(ErrorType.Validation, exception, message, args);

    /// <summary>
    /// Logs an unexpected runtime exception into the unhandled-specific log sink.
    /// </summary>
    public static void LogUnhandledError(
        this ILogger logger,
        Exception? exception,
        string message,
        params object?[] args) =>
        logger.LogTypedError(ErrorType.Unhandled, exception, message, args);
}
