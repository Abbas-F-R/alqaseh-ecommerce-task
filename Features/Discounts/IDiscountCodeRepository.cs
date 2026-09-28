using System.Data;

namespace AlQaseh_Ecommerce_API.Features.Discounts;

public interface IDiscountCodeRepository
{
    /// <summary>Reads a code (case-insensitive) and locks its row until the transaction ends, so two orders cannot redeem it together.</summary>
    Task<DiscountCodeDto?> GetByCodeForUpdate(string code, IDbTransaction transaction);

    /// <summary>Marks an unused code as used; false when it was already used.</summary>
    Task<bool> MarkAsUsed(long id, IDbTransaction transaction);
}
