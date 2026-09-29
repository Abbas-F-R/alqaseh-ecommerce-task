using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Auth.Dtos;

/// <summary>
/// Successful login: the bearer token and who it belongs to.
/// </summary>
public class LoginResponse
{
    [Sqid]
    public long UserId { get; set; }
    public string UserName { get; set; } = string.Empty;
    public string FullName { get; set; } = string.Empty;

    /// <summary>"Admin" or "Customer".</summary>
    public string Role { get; set; } = string.Empty;

    public string Token { get; set; } = string.Empty;

    /// <summary>UTC time at which the token stops being valid.</summary>
    public DateTime ExpiresAt { get; set; }
}
