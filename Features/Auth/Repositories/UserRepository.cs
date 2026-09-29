using System.Data;
using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using Dapper;

namespace AlQaseh_Ecommerce_API.Features.Auth.Repositories;

[Scoped]
public class UserRepository(DapperContext context) : IUserRepository
{
    public async Task<UserDto?> GetByUserName(string userName)
    {
        await using var connection = context.CreateConnection();
        return await connection.QueryFirstOrDefaultAsync<UserDto>(
            "UsersGetByUserName",
            new { UserName = userName },
            commandType: CommandType.StoredProcedure);
    }

    public async Task<UserDto?> GetById(long id)
    {
        await using var connection = context.CreateConnection();
        return await connection.QueryFirstOrDefaultAsync<UserDto>(
            "UsersGetById",
            new { Id = id },
            commandType: CommandType.StoredProcedure);
    }
}
