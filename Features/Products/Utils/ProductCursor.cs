using Microsoft.AspNetCore.WebUtilities;
using System.Text;
using System.Text.Json;

namespace AlQaseh_Ecommerce_API.Features.Products.Utils;

/// <summary>
/// Encodes and decodes opaque Base64 cursors for Keyset/Seek pagination.
/// </summary>
public static class ProductCursor
{
    private sealed class CursorPayload
    {
        public long Id { get; set; }
    }

    public static string Encode(long id)
    {
        var json = JsonSerializer.Serialize(new CursorPayload { Id = id });
        return WebEncoders.Base64UrlEncode(Encoding.UTF8.GetBytes(json)); // URL-safe: no +, / or = to escape
    }

    public static bool TryDecode(string? cursor, out long id)
    {
        id = 0;
        if (string.IsNullOrWhiteSpace(cursor))
            return true;

        try
        {
            var bytes = WebEncoders.Base64UrlDecode(cursor.Trim());
            var json = Encoding.UTF8.GetString(bytes);
            var payload = JsonSerializer.Deserialize<CursorPayload>(json);
            if (payload == null || payload.Id <= 0)
                return false;

            id = payload.Id;
            return true;
        }
        catch
        {
            return false;
        }
    }

    public static bool IsValid(string? cursor) => TryDecode(cursor, out _);
}
