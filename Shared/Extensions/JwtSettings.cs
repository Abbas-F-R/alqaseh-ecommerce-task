using System.Security.Cryptography;
using System.Text;
using Microsoft.IdentityModel.Tokens;

namespace AlQaseh_Ecommerce_API.Shared.Extensions;

/// <summary>
/// Signing key and lifetime of the access tokens, resolved once at startup and shared by the token issuer and the bearer validation.
/// </summary>
public sealed class JwtSettings
{
    public const string Issuer = "AlQasehEcommerceAPI";
    public const string Audience = "AlQasehEcommerceClients";
    public const int MinKeyLength = 32;

    public string SecretKey { get; }
    public TimeSpan Lifetime { get; }
    public SymmetricSecurityKey SigningKey => new(Encoding.UTF8.GetBytes(SecretKey));

    public JwtSettings(string secretKey, TimeSpan lifetime)
    {
        if (string.IsNullOrWhiteSpace(secretKey) || secretKey.Length < MinKeyLength)
            throw new InvalidOperationException($"Jwt:SecretKey must be at least {MinKeyLength} characters long.");
        if (lifetime <= TimeSpan.Zero)
            throw new InvalidOperationException("Jwt:ExpiresMinutes must be greater than zero.");

        SecretKey = secretKey;
        Lifetime = lifetime;
    }

    /// <summary>
    /// Reads <c>Jwt:SecretKey</c> and <c>Jwt:ExpiresMinutes</c> (default 60). The repository contains no key:
    /// in Development a random key is generated on every start (tokens stop working after a restart), everywhere else the key must be configured.
    /// </summary>
    public static JwtSettings Load(IConfiguration configuration, IHostEnvironment environment)
    {
        var secretKey = configuration["Jwt:SecretKey"];

        if (string.IsNullOrWhiteSpace(secretKey))
        {
            if (!environment.IsDevelopment())
                throw new InvalidOperationException(
                    "Jwt:SecretKey is not configured. Set the Jwt__SecretKey environment variable (at least 32 characters).");

            secretKey = Convert.ToBase64String(RandomNumberGenerator.GetBytes(48));
        }

        return new JwtSettings(secretKey, TimeSpan.FromMinutes(configuration.GetValue("Jwt:ExpiresMinutes", 60)));
    }
}
