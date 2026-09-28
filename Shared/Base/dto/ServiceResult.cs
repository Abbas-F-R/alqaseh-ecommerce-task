namespace AlQaseh_Ecommerce_API.Shared.Base.dto;

/// <summary>
/// Outcome of a service call: either data (plus the total count for a paged list) or an error code from <c>Messages</c>.
/// Expected business failures are returned this way; unexpected failures are exceptions.
/// </summary>
public class ServiceResult<T>
{
    public T? Data { get; private init; }
    public int TotalCount { get; private init; }
    public string? Error { get; private init; }
    public bool IsSuccess => Error == null;

    public static ServiceResult<T> Ok(T? data) => new() { Data = data };
    public static ServiceResult<T> PagedOk(T? data, int totalCount) => new() { Data = data, TotalCount = totalCount };
    public static ServiceResult<T> Failure(string error) => new() { Error = error };
}
