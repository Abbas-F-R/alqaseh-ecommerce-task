namespace AlQaseh_Ecommerce_API.Shared.Utils;

/// <summary>
/// BCrypt password hashing (work factor 11) and verification.
/// </summary>
public static class PasswordHasher
{
    private static readonly Lazy<string> Dummy = new(() => Hash(Guid.NewGuid().ToString("N")));

    /// <summary>A valid hash of a random password, used to spend the same time on unknown users as on known ones.</summary>
    public static string DummyHash => Dummy.Value;

    public static string Hash(string password) => BCrypt.Net.BCrypt.HashPassword(password, workFactor: 11);

    /// <summary>True when <paramref name="password"/> matches the stored BCrypt <paramref name="hash"/>; malformed hashes never match.</summary>
    public static bool Verify(string password, string hash)
    {
        if (string.IsNullOrWhiteSpace(password) || string.IsNullOrWhiteSpace(hash))
            return false;

        try
        {
            return BCrypt.Net.BCrypt.Verify(password, hash);
        }
        catch
        {
            return false;
        }
    }
}
