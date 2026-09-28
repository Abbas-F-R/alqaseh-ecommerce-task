namespace AlQaseh_Ecommerce_API.Infrastructure.Logging;

/// <summary>
/// Evaluates exceptions and HTTP contexts to categorize errors into structured <see cref="ErrorType"/>s.
/// Used to route logs to specialized log files and enrich telemetry.
/// </summary>
public static class ErrorClassifier
{
    private static readonly string[] DatabaseKeywords =
        ["SqlClient", "DataException", "DbException", "TimeoutException", "SQL Server", "Named Pipes Provider", "TCP Provider", "Could not open a connection", "deadlock"];

    private static readonly string[] SecurityKeywords =
        ["SecurityToken", "AuthenticationException", "Forbidden", "SecurityException", "Unauthorized", "JWT", "token"];

    private static readonly string[] ValidationKeywords =
        ["ValidationException"];

    /// <summary>
    /// Classifies an exception and optional HTTP status code into an <see cref="ErrorType"/>.
    /// </summary>
    public static ErrorType Classify(Exception? exception, int? statusCode = null) =>
        statusCode switch
        {
            StatusCodes.Status401Unauthorized or StatusCodes.Status403Forbidden => ErrorType.Security,
            StatusCodes.Status400BadRequest or StatusCodes.Status422UnprocessableEntity => ErrorType.Validation,
            StatusCodes.Status404NotFound => ErrorType.NotFound,
            _ when exception == null => ErrorType.Unhandled,
            _ when IsDatabaseException(exception) => ErrorType.Database,
            _ when IsSecurityException(exception) => ErrorType.Security,
            _ when IsValidationException(exception) => ErrorType.Validation,
            _ when IsNotFoundException(exception) => ErrorType.NotFound,
            _ => ErrorType.Unhandled
        };

    /// <summary>Inspects the exception chain for database or connection errors.</summary>
    public static bool IsDatabaseException(Exception ex) =>
        HasException(ex, (cur, type) => MatchesAny(type, cur.Message, DatabaseKeywords));

    /// <summary>Inspects the exception chain for authentication, token, or security violations.</summary>
    public static bool IsSecurityException(Exception ex) =>
        HasException(ex, (cur, type) => cur is UnauthorizedAccessException || MatchesAny(type, cur.Message, SecurityKeywords));

    /// <summary>Inspects the exception chain for input validation or format failures.</summary>
    public static bool IsValidationException(Exception ex) =>
        HasException(ex, (cur, type) => cur is ArgumentException or FormatException || MatchesAny(type, cur.Message, ValidationKeywords));

    /// <summary>Inspects the exception chain for entity/resource not found exceptions.</summary>
    public static bool IsNotFoundException(Exception ex) =>
        HasException(ex, (cur, type) => cur is KeyNotFoundException || type.Contains("NotFoundException", StringComparison.OrdinalIgnoreCase));

    private static bool HasException(Exception ex, Func<Exception, string, bool> predicate)
    {
        for (var cur = (Exception?)ex; cur != null; cur = cur.InnerException)
        {
            if (predicate(cur, cur.GetType().FullName ?? string.Empty))
                return true;
        }
        return false;
    }

    private static bool MatchesAny(string typeName, string message, string[] keywords) =>
        keywords.Any(k => typeName.Contains(k, StringComparison.OrdinalIgnoreCase) || message.Contains(k, StringComparison.OrdinalIgnoreCase));
}
