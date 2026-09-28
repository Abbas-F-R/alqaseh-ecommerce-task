using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using AlQaseh_Ecommerce_API.Shared.Constants;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace AlQaseh_Ecommerce_API.Infrastructure.Middleware;

/// <summary>
/// Sits between authentication and authorization: an authenticated token must carry a numeric user id,
/// otherwise the request is rejected with 401 (a validly signed token without an identity is a security event and is logged as such).
/// </summary>
public sealed class UserContextMiddleware(RequestDelegate next, ILogger<UserContextMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext http)
    {
        var anonymous = http.GetEndpoint()?.Metadata.GetMetadata<IAllowAnonymous>() is not null;

        if (anonymous || http.User.Identity?.IsAuthenticated != true)
        {
            await next(http);
            return;
        }

        if (!long.TryParse(http.User.FindFirst(ClaimNames.UserId)?.Value, out _))
        {
            logger.LogSecurityError(null, "[UserContext] A validated token reached {Path} carrying no valid user id", http.Request.Path);

            http.Response.StatusCode = StatusCodes.Status401Unauthorized;
            await http.Response.WriteAsJsonAsync(
                new ProblemDetails
                {
                    Status = StatusCodes.Status401Unauthorized,
                    Title = "Unauthorized",
                    Detail = "The token does not contain a valid user identifier. Please sign in again."
                },
                options: null,
                contentType: "application/problem+json; charset=utf-8");
            return;
        }

        await next(http);
    }
}
