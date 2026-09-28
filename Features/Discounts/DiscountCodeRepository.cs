using System.Data;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using Dapper;

namespace AlQaseh_Ecommerce_API.Features.Discounts;

[Scoped]
public class DiscountCodeRepository : IDiscountCodeRepository
{
    public Task<DiscountCodeDto?> GetByCodeForUpdate(string code, IDbTransaction transaction) =>
        transaction.Connection!.QueryFirstOrDefaultAsync<DiscountCodeDto>(
            @"SELECT Id, Code, Amount, MinimumOrderTotal, ExpiresAt, Used
              FROM DiscountCodes WITH (UPDLOCK, ROWLOCK)
              WHERE Code = @Code",
            new { Code = code },
            transaction);

    public async Task<bool> MarkAsUsed(long id, IDbTransaction transaction) =>
        await transaction.Connection!.ExecuteAsync(
            "UPDATE DiscountCodes SET Used = 1 WHERE Id = @Id AND Used = 0",
            new { Id = id },
            transaction) > 0;
}
