namespace AlQaseh_Ecommerce_API.Infrastructure.Persistence;

public static class SqlLike
{
    /// <summary>
    /// Escapes the LIKE wildcards so that a name filter is always a plain "contains" search.
    /// The stored procedures use <c>ESCAPE '\'</c>.
    /// </summary>
    public static string Escape(string value) =>
        value.Replace("\\", "\\\\").Replace("%", "\\%").Replace("_", "\\_").Replace("[", "\\[");
}
