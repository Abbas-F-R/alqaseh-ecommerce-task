using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Shared.Base.dto;

namespace AlQaseh_Ecommerce_API.Features.Auth.Services;

public interface IAuthService
{
    Task<ServiceResult<LoginResponse>> Login(LoginRequest request);
}
