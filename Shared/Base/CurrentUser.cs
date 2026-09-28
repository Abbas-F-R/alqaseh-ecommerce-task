using System.Security.Claims;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Constants;

namespace AlQaseh_Ecommerce_API.Shared.Base;

/// <summary>
/// Identity of the caller, read from the validated JWT, plus the response language.
/// </summary>
public interface ICurrentUser
{
    long UserId { get; }
    string UserName { get; }
    string Role { get; }
    string Lang { get; }
}

[Scoped]
public class CurrentUser(IHttpContextAccessor httpContextAccessor) : ICurrentUser
{
    private HttpContext? HttpContext => httpContextAccessor.HttpContext;

    public long UserId => long.TryParse(GetClaim(ClaimNames.UserId), out var id) ? id : 0;

    public string UserName => GetClaim(ClaimNames.UserName) ?? string.Empty;

    public string Role => GetClaim(ClaimNames.Role) ?? string.Empty;

    /// <summary>"ar" when the Accept-Language header starts with "ar", otherwise "en".</summary>
    public string Lang
    {
        get
        {
            var header = HttpContext?.Request.Headers.AcceptLanguage.ToString();
            if (string.IsNullOrWhiteSpace(header))
                return "en";

            var primary = header.Split(',', ';')[0].Trim();
            return primary.StartsWith("ar", StringComparison.OrdinalIgnoreCase) ? "ar" : "en";
        }
    }

    private string? GetClaim(string claimType) => HttpContext?.User.FindFirst(claimType)?.Value;
}
