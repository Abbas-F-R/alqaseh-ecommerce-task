namespace AlQaseh_Ecommerce_API.Shared.Base.dto;

/// <summary>
/// Standardized paginated response envelope providing metadata for consumer clients.
/// </summary>
public class Response<T>
{
    public List<T> Data { get; set; }
    public int PagesCount { get; set; }
    public int CurrentPage { get; set; }
    public int TotalCount { get; set; }
    public bool IsLast { get; set; }

    public Response(List<T>? data, int currentPage, int totalCount, int pageSize)
    {
        Data = data ?? new List<T>();
        CurrentPage = currentPage;
        TotalCount = totalCount;

        if (pageSize <= 0)
        {
            PagesCount = 0;
            IsLast = true;
            return;
        }

        PagesCount = (totalCount + pageSize - 1) / pageSize;
        IsLast = currentPage >= PagesCount;
    }
}
