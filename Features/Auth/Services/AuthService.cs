using System.Security.Claims;
using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Features.Auth.Repositories;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using AlQaseh_Ecommerce_API.Shared.Extensions;
using AlQaseh_Ecommerce_API.Shared.Utils;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Tokens;

namespace AlQaseh_Ecommerce_API.Features.Auth.Services;

/// <summary>
/// Verifies credentials (BCrypt) and issues a signed JWT carrying the user id, username and role.
/// </summary>
[Scoped]
public class AuthService(
    IUserRepository repository,
    JwtSettings jwt,
    TimeProvider clock) : IAuthService
{
    public async Task<ServiceResult<LoginResponse>> Login(LoginRequest request)
    {
        var userName = request.UserName.Trim();
        var user = await repository.GetByUserName(userName);

        // An unknown user is verified against a dummy hash so that both failures take the same time (no username enumeration).
        var passwordMatches = PasswordHasher.Verify(request.Password, user?.PasswordHash ?? PasswordHasher.DummyHash);

        if (user is null || !passwordMatches)
            return ServiceResult<LoginResponse>.Failure(Messages.InvalidCredentials);

        return ServiceResult<LoginResponse>.Ok(CreateToken(user));
    }

    private LoginResponse CreateToken(UserDto user)
    {
        var expires = clock.GetUtcNow().UtcDateTime.Add(jwt.Lifetime);

        var descriptor = new SecurityTokenDescriptor
        {
            Subject = new ClaimsIdentity(
            [
                new Claim(ClaimNames.UserId, user.Id.ToString()),
                new Claim(ClaimNames.UserName, user.UserName),
                new Claim(ClaimNames.Role, user.Role)
            ]),
            Issuer = JwtSettings.Issuer,
            Audience = JwtSettings.Audience,
            Expires = expires,
            SigningCredentials = new SigningCredentials(jwt.SigningKey, SecurityAlgorithms.HmacSha256)
        };

        return new LoginResponse
        {
            UserId = user.Id,
            UserName = user.UserName,
            FullName = user.FullName,
            Role = user.Role,
            Token = new JsonWebTokenHandler().CreateToken(descriptor),
            ExpiresAt = expires
        };
    }
}
