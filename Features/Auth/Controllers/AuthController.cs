using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Features.Auth.Services;
using AlQaseh_Ecommerce_API.Shared.Base;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace AlQaseh_Ecommerce_API.Features.Auth.Controllers;

/// <summary>
/// Login. Every other endpoint requires the bearer token issued here.
/// </summary>
[Route("api/auth")]
[ApiController]
public class AuthController(IAuthService authService) : BaseController
{
    /// <summary>
    /// Signs in with a username and password and returns a bearer token.
    /// </summary>
    /// <response code="200">Signed in. Send the token as <c>Authorization: Bearer {token}</c>.</response>
    /// <response code="400">Username or password missing.</response>
    /// <response code="401">Wrong username or password.</response>
    [HttpPost("login")]
    [AllowAnonymous]
    [ProducesResponseType(typeof(LoginResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<LoginResponse>> Login([FromBody] LoginRequest request) =>
        Respond(await authService.Login(request));
}
