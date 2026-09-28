namespace AlQaseh_Ecommerce_API.Infrastructure.Logging;

/// <summary>
/// Categorized error types used for diagnostic routing, telemetry enrichment,
/// and partitioning log events into dedicated log files.
/// </summary>
public enum ErrorType
{
    /// <summary>
    /// Database connectivity, SQL Server, ADO.NET, or Dapper execution errors.
    /// </summary>
    Database,

    /// <summary>
    /// Authentication, JWT validation, authorization, or forbidden access failures.
    /// </summary>
    Security,

    /// <summary>
    /// Client input validation failures or bad request payloads.
    /// </summary>
    Validation,

    /// <summary>
    /// Requested resource or entity not found.
    /// </summary>
    NotFound,

    /// <summary>
    /// General unhandled system exceptions and unexpected runtime faults.
    /// </summary>
    Unhandled
}
