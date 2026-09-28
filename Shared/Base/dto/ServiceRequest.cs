namespace AlQaseh_Ecommerce_API.Shared.Base.dto;

/// <summary>
/// Execution request wrapper bundling input DTO with acting user security context.
/// Decouples service layer from direct HttpContext dependencies.
/// </summary>
public class ServiceRequest<T>
{
    public T Dto { get; set; } = default!;
    public long UserId { get; set; }
    public string UserName { get; set; } = string.Empty;
    public string Role { get; set; } = string.Empty;
    public string Lang { get; set; } = "en";

    public ServiceRequest() { }

    public ServiceRequest(T dto, long userId = 0, string userName = "", string role = "User", string lang = "en")
    {
        Dto = dto;
        UserId = userId;
        UserName = userName;
        Role = role;
        Lang = string.IsNullOrWhiteSpace(lang) ? "en" : lang;
    }
}
