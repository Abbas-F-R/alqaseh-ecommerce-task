namespace AlQaseh_Ecommerce_API.Shared.Base.dto;

/// <summary>
/// Pagination parameters shared by every list endpoint.
/// </summary>
public class BaseFilter
{
    public const int MaxPageSize = 50;

    /// <summary>With the largest page size the offset stays far below the int range of the stored procedures.</summary>
    public const int MaxPageNumber = 100_000;

    /// <summary>Items per page (1-50, default 10).</summary>
    public int PageSize { get; set; } = 10;

    /// <summary>Page number, starting at 0.</summary>
    public int PageNumber { get; set; } = 0;

    /// <summary>Alias for PageNumber to support ?page=0 query syntax.</summary>
    public int? Page
    {
        get => PageNumber;
        set { if (value.HasValue) PageNumber = value.Value; }
    }

    /// <summary>Alias for PageSize to support ?size=20 query syntax.</summary>
    public int? Size
    {
        get => PageSize;
        set { if (value.HasValue) PageSize = value.Value; }
    }
}
