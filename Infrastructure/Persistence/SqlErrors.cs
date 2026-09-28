using Microsoft.Data.SqlClient;

namespace AlQaseh_Ecommerce_API.Infrastructure.Persistence;

/// <summary>
/// Recognizes SQL Server error numbers that the application turns into business errors.
/// </summary>
public static class SqlErrors
{
    /// <summary>Duplicate key in a unique index (2601) or unique constraint (2627).</summary>
    public static bool IsUniqueViolation(Exception exception) =>
        exception is SqlException { Number: 2601 or 2627 };
}
