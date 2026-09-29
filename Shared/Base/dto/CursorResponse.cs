namespace AlQaseh_Ecommerce_API.Shared.Base.dto;

/// <summary>
/// Standardized keyset/cursor paginated response envelope.
/// </summary>
public class CursorResponse<T>
{
    public List<T> Data { get; set; } = new();
    public string? NextCursor { get; set; }
    public bool HasMore { get; set; }

    public CursorResponse() { }

    public CursorResponse(List<T>? data, string? nextCursor, bool hasMore)
    {
        Data = data ?? new List<T>();
        NextCursor = nextCursor;
        HasMore = hasMore;
    }
}
