using System.Data;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using Dapper;

namespace AlQaseh_Ecommerce_API.Infrastructure.Persistence;

/// <summary>
/// Runs several repository calls in one database transaction.
/// </summary>
public interface IUnitOfWork
{
    /// <summary>
    /// Opens a connection and a transaction and runs <paramref name="work"/> with it.
    /// The transaction is committed only when the returned result is a success; a failure result or an exception rolls everything back.
    /// <paramref name="userId"/> is made available to the database (<c>SESSION_CONTEXT('UserId')</c>) so that the audit triggers know who is acting.
    /// </summary>
    Task<ServiceResult<T>> ExecuteAsync<T>(long userId, Func<IDbTransaction, Task<ServiceResult<T>>> work);
}

[Scoped]
public class UnitOfWork(DapperContext context) : IUnitOfWork
{
    public async Task<ServiceResult<T>> ExecuteAsync<T>(long userId, Func<IDbTransaction, Task<ServiceResult<T>>> work)
    {
        await using var connection = context.CreateConnection();
        await connection.OpenAsync();
        await using var transaction = await connection.BeginTransactionAsync();

        try
        {
            await connection.ExecuteAsync(
                "EXEC sys.sp_set_session_context @key = N'UserId', @value = @UserId", new { UserId = userId }, transaction);

            var result = await work(transaction);

            if (result.IsSuccess)
                await transaction.CommitAsync();
            else
                await transaction.RollbackAsync();

            return result;
        }
        catch
        {
            try { await transaction.RollbackAsync(); } catch { /* the original exception is the one that matters */ }
            throw;
        }
    }
}
