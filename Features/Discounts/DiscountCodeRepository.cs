using System.Data;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using Dapper;

namespace AlQaseh_Ecommerce_API.Features.Discounts;

[Scoped]
public class DiscountCodeRepository : IDiscountCodeRepository
{
    public Task<DiscountCodeDto?> GetByCodeForUpdate(string code, IDbTransaction transaction) =>
        transaction.Connection!.QueryFirstOrDefaultAsync<DiscountCodeDto>(
            "DiscountCodesGetByCodeForUpdate",
            new { Code = code },
            transaction,
            commandType: CommandType.StoredProcedure);

    public async Task<bool> MarkAsUsed(long id, IDbTransaction transaction)
    {
        var rowsAffected = await transaction.Connection!.ExecuteScalarAsync<int>(
            "DiscountCodesMarkAsUsed",
            new { Id = id },
            transaction,
            commandType: CommandType.StoredProcedure);

        return rowsAffected > 0;
    }
}
