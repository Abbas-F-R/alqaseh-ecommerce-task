using AlQaseh_Ecommerce_API.Features.Auth.Repositories;
using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using AlQaseh_Ecommerce_API.Shared.Constants;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace AlQaseh_Ecommerce_API.Infrastructure.Middleware;

/// <summary>
/// Sits between authentication and authorization and checks the account behind a validated token against the database, so that a token
/// cannot outlive its account or its role: the user must exist and still have the role the token carries, otherwise the request is
/// rejected with 401 (a validly signed token that no longer matches an account is a security event and is logged as such).
/// One indexed lookup by primary key per authenticated request.
/// </summary>
public sealed class UserContextMiddleware(RequestDelegate next, ILogger<UserContextMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext http, IUserRepository users)
    {
        var anonymous = http.GetEndpoint()?.Metadata.GetMetadata<IAllowAnonymous>() is not null;
        if (anonymous || http.User.Identity?.IsAuthenticated != true)
        {
            await next(http);
            return;
        }

        if (!long.TryParse(http.User.FindFirst(ClaimNames.UserId)?.Value, out var userId))
        {
            logger.LogSecurityError(null, "[UserContext] A validated token reached {Path} carrying no valid user id", http.Request.Path);
            await RejectAsync(http, "The token does not contain a valid user identifier. Please sign in again.");
            return;
        }

        var user = await users.GetById(userId);
        var tokenRole = http.User.FindFirst(ClaimNames.Role)?.Value;
        if (user is null || !string.Equals(user.Role, tokenRole, StringComparison.Ordinal))
        {
            logger.LogSecurityError(null, "[UserContext] A validated token of user {UserId} reached {Path} but the account is {State}",
                userId, http.Request.Path, user is null ? "gone" : "no longer in the role of the token");
            await RejectAsync(http, "The account of this token no longer exists or its role has changed. Please sign in again.");
            return;
        }

        await next(http);
    }

    private static Task RejectAsync(HttpContext http, string detail)
    {
        http.Response.StatusCode = StatusCodes.Status401Unauthorized;
        return http.Response.WriteAsJsonAsync(
            new ProblemDetails { Status = StatusCodes.Status401Unauthorized, Title = "Unauthorized", Detail = detail },
            options: null,
            contentType: "application/problem+json; charset=utf-8");
    }
}
