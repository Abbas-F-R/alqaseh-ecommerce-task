namespace AlQaseh_Ecommerce_API.Shared.Base.dto;

/// <summary>
/// Pagination parameters shared by every list endpoint.
/// </summary>
public class BaseFilter
{
    public const int MaxPageSize = 50;

    /// <summary>Items per page (1-50, default 10).</summary>
    public int PageSize { get; set; } = 10;

    /// <summary>Page number, starting at 1.</summary>
    public int PageNumber { get; set; } = 1;
}
