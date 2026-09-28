namespace AlQaseh_Ecommerce_API.Features.Auth.Dtos;

/// <summary>
/// A user row as read for authentication (internal, never returned by the API).
/// </summary>
public class UserDto
{
    public long Id { get; set; }
    public string FullName { get; set; } = string.Empty;
    public string UserName { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;
    public string Role { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
}
