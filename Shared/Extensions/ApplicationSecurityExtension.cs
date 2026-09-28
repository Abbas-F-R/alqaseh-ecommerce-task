using AlQaseh_Ecommerce_API.Shared.Constants;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

namespace AlQaseh_Ecommerce_API.Shared.Extensions;

/// <summary>
/// JWT bearer authentication: every endpoint except login requires a valid token, roles are enforced with [Authorize(Roles = ...)].
/// </summary>
public static class ApplicationSecurityExtension
{
    public static IServiceCollection AddSecurityExtension(this IServiceCollection services, IConfiguration configuration, IHostEnvironment environment)
    {
        var jwt = JwtSettings.Load(configuration, environment);
        services.AddSingleton(jwt);

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(options =>
            {
                options.MapInboundClaims = false;
                options.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuer = true,
                    ValidIssuer = JwtSettings.Issuer,
                    ValidateAudience = true,
                    ValidAudience = JwtSettings.Audience,
                    ValidateIssuerSigningKey = true,
                    IssuerSigningKey = jwt.SigningKey,
                    ValidateLifetime = true,
                    ClockSkew = TimeSpan.Zero,
                    NameClaimType = ClaimNames.UserName,
                    RoleClaimType = ClaimNames.Role
                };
            });

        services.AddAuthorization(options =>
        {
            // Everything requires a signed-in user unless it is explicitly [AllowAnonymous] (login).
            options.FallbackPolicy = new Microsoft.AspNetCore.Authorization.AuthorizationPolicyBuilder()
                .RequireAuthenticatedUser()
                .Build();
        });

        return services;
    }
}
