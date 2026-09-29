using System.Data;
using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using AlQaseh_Ecommerce_API.Shared.Constants;
using AlQaseh_Ecommerce_API.Shared.Utils;
using Dapper;

namespace AlQaseh_Ecommerce_API.Infrastructure.Persistence;

/// <summary>
/// Demo data for local runs (enabled with <c>Seed:Enabled</c>, on by default in Development only):
/// one admin, two customers, products in every category and stock band, and discount codes.
/// Existing rows are never touched, so it is safe to run on every start. Amounts are in IQD.
/// </summary>
public sealed class DatabaseSeeder(DapperContext context, TimeProvider clock, ILogger<DatabaseSeeder> logger)
{
    private static readonly (string UserName, string FullName, string Password, string Role)[] Users =
    [
        ("admin", "System Administrator", "Admin123!", Roles.Admin),
        ("customer1", "Customer One", "Customer123!", Roles.Customer),
        ("customer2", "Customer Two", "Customer123!", Roles.Customer)
    ];

    // Name, category, price, cost, quantity. Quantities cover every stock status: 0-4 low, 5-9 limited, 10+ available.
    private static readonly (string Name, string Category, decimal Price, decimal Cost, int Quantity)[] Products =
    [
        ("Ergonomic Office Chair", "furniture", 75_000m, 50_000m, 12),
        ("Oak Dining Table", "furniture", 250_000m, 170_000m, 3),
        ("Wooden Bookshelf", "furniture", 90_000m, 60_000m, 7),
        ("Smartphone Pro", "electronics", 600_000m, 450_000m, 25),
        ("Wireless Headphones", "electronics", 85_000m, 55_000m, 9),
        ("Smart TV 55 Inch", "electronics", 550_000m, 420_000m, 0),
        ("Vitamin C Face Serum", "beauty", 30_000m, 15_000m, 8),
        ("Daily Moisturizer", "beauty", 22_000m, 11_000m, 40),
        ("Electric Lawn Mower", "garden", 320_000m, 230_000m, 4),
        ("Garden Hose 50 m", "garden", 25_000m, 12_000m, 20)
    ];

    public async Task SeedAsync(CancellationToken cancellationToken = default)
    {
        await using var connection = context.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        foreach (var user in Users)
        {
            await connection.ExecuteAsync(new CommandDefinition(
                "SeedUserIfNotExists",
                new { user.FullName, user.UserName, PasswordHash = PasswordHasher.Hash(user.Password), user.Role },
                commandType: CommandType.StoredProcedure,
                cancellationToken: cancellationToken));
        }

        var adminUser = await connection.QueryFirstOrDefaultAsync<UserDto>(new CommandDefinition(
            "UsersGetByUserName",
            new { UserName = "admin" },
            commandType: CommandType.StoredProcedure,
            cancellationToken: cancellationToken));

        var adminId = adminUser!.Id;

        foreach (var product in Products)
        {
            await connection.ExecuteAsync(new CommandDefinition(
                "SeedProductIfNotExists",
                new { product.Name, product.Category, product.Price, product.Cost, AvailableQuantity = product.Quantity, AdminId = adminId },
                commandType: CommandType.StoredProcedure,
                cancellationToken: cancellationToken));
        }

        var now = clock.GetUtcNow().UtcDateTime;
        (string Code, decimal Amount, decimal MinimumOrderTotal, DateTime ExpiresAt, bool Used)[] discounts =
        [
            ("ABC123", 5_000m, 25_000m, now.AddYears(5), false),
            ("ZYX123", 10_000m, 50_000m, now.AddYears(5), false),
            ("EXPIRED10", 10_000m, 50_000m, now.AddYears(-1), false),
            ("USED5", 5_000m, 25_000m, now.AddYears(5), true)
        ];

        foreach (var discount in discounts)
        {
            await connection.ExecuteAsync(new CommandDefinition(
                "SeedDiscountCodeIfNotExists",
                new { discount.Code, discount.Amount, discount.MinimumOrderTotal, discount.ExpiresAt, discount.Used },
                commandType: CommandType.StoredProcedure,
                cancellationToken: cancellationToken));
        }

        logger.LogInformation("[Seed] Demo users, products and discount codes are in place");
    }
}
