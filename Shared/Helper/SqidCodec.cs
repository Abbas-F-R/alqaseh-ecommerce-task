using Sqids;

namespace AlQaseh_Ecommerce_API.Shared.Helper;

/// <summary>
/// Utility helper for encoding 64-bit integers into obfuscated URL-safe Sqid strings and decoding them back.
/// </summary>
public static class SqidCodec
{
    private static readonly SqidsEncoder<long> Encoder = new(new SqidsOptions { MinLength = 8 });

    /// <summary>
    /// Encodes a 64-bit integer identifier into an 8+ character Sqid string.
    /// </summary>
    public static string Encode(long value) => Encoder.Encode(value);

    /// <summary>
    /// Attempts to decode a string as either a valid Sqid or a numeric integer string.
    /// </summary>
    public static long? TryDecode(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        value = value.Trim();

        if (long.TryParse(value, out var plain)) return plain;

        try
        {
            var decoded = Encoder.Decode(value);
            return decoded.Count > 0 ? decoded[0] : null;
        }
        catch
        {
            return null;
        }
    }
}
