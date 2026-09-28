using AlQaseh_Ecommerce_API.Features.Auth.Dtos;

namespace AlQaseh_Ecommerce_API.Features.Auth.Repositories;

public interface IUserRepository
{
    Task<UserDto?> GetByUserName(string userName);
}
