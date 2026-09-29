using System.Data;
using System.Text.RegularExpressions;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using Dapper;
using FluentAssertions;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Integration;

/// <summary>
/// The list procedures must stay cheap as the data grows: a deep keyset page, a category page and an order list filtered by customer
/// have to seek (a handful of page reads), not scan the table. Measured with SET STATISTICS IO on a database of its own that holds
/// 20,000 products and 20,000 orders (the shared test database is not touched). Each procedure is first called the way a first page is
/// (no cursor, no filter), which is what used to fix a scanning plan for every later call.
/// </summary>
public sealed partial class QueryPlanTests(QueryPlanTests.PlanDatabase database) : IClassFixture<QueryPlanTests.PlanDatabase>
{
    private const int MaxPageReads = 30; // a seek is 3-5 reads; a scan of 20,000 rows is 250 or more

    [SqlServerFact]
    public async Task ProductsGetCursor_SeeksToTheCursor_EvenAfterAFirstPageWasCompiled()
    {
        await using var connection = await OpenAsync();
        await ReadsOf(connection, "ProductsGetCursor", new { Limit = 10 }); // first page: no cursor, no filter

        var deepId = await connection.ExecuteScalarAsync<long>("SELECT Id FROM Products ORDER BY Id OFFSET 15000 ROWS FETCH NEXT 1 ROWS ONLY");

        (await ReadsOf(connection, "ProductsGetCursor", new { Limit = 10, AfterId = deepId })).Should().BeLessThan(MaxPageReads);
        (await ReadsOf(connection, "ProductsGetCursor", new { Limit = 10, AfterId = deepId, Category = "beauty" })).Should().BeLessThan(MaxPageReads);
    }

    [SqlServerFact]
    public async Task ProductsGetAll_ReadsOnlyTheCategoryRange_WhenFilteredByCategory()
    {
        await using var connection = await OpenAsync();

        var unfiltered = await ReadsOf(connection, "ProductsGetAll", new { PageNumber = 0, PageSize = 10 });
        var filtered = await ReadsOf(connection, "ProductsGetAll", new { PageNumber = 0, PageSize = 10, Category = "garden" });

        filtered.Should().BeLessThan(unfiltered, "the count of one category must not read the whole table");
    }

    [SqlServerFact]
    public async Task OrdersGetAll_SeeksByCustomer_EvenAfterAnUnfilteredPageWasCompiled()
    {
        await using var connection = await OpenAsync();
        await ReadsOf(connection, "OrdersGetAll", new { PageNumber = 0, PageSize = 10 });

        var customerId = await connection.ExecuteScalarAsync<long>("SELECT MIN(Id) + 7 FROM Users WHERE UserName LIKE 'plan[0-9]%'");
        var reads = await ReadsOf(connection, "OrdersGetAll", new { CustomerId = customerId, PageNumber = 0, PageSize = 10 });

        // the count of that customer's 400 orders (an index range) plus the page (a seek); a scan would read all 20,000 orders twice
        reads.Should().BeLessThan(MaxPageReads * 4);
    }

    [SqlServerFact]
    public async Task OrdersGetAll_UsesTheIndexOfThePaymentMethod()
    {
        await using var connection = await OpenAsync();

        var reads = await ReadsOf(connection, "OrdersGetAll", new { PaymentMethod = "XyzWallet", PageNumber = 0, PageSize = 10 });

        reads.Should().BeLessThan(MaxPageReads * 4);
    }

    [SqlServerFact]
    public async Task TheRedundantCategoryIndexIsGone_AndTheCategoryIdIndexRemains()
    {
        await using var connection = await OpenAsync();

        var indexes = (await connection.QueryAsync<string>(
            "SELECT name FROM sys.indexes WHERE object_id = OBJECT_ID('Products') AND name LIKE 'IX_Products%'")).ToList();

        indexes.Should().Equal("IX_Products_Category_Id");
    }

    private async Task<SqlConnection> OpenAsync()
    {
        var connection = new SqlConnection(database.ConnectionString);
        await connection.OpenAsync();
        return connection;
    }

    /// <summary>Runs the procedure with SET STATISTICS IO ON and returns the sum of the logical reads it reported.</summary>
    private static async Task<int> ReadsOf(SqlConnection connection, string procedure, object parameters)
    {
        var reads = 0;
        void OnInfo(object _, SqlInfoMessageEventArgs e) => reads += LogicalReads().Matches(e.Message).Sum(m => int.Parse(m.Groups[1].Value));

        await connection.ExecuteAsync("SET STATISTICS IO ON");
        connection.InfoMessage += OnInfo;
        try
        {
            await using var grid = await connection.QueryMultipleAsync(procedure, parameters, commandType: CommandType.StoredProcedure);
            while (!grid.IsConsumed)
                _ = (await grid.ReadAsync()).ToList();
        }
        finally
        {
            connection.InfoMessage -= OnInfo;
            await connection.ExecuteAsync("SET STATISTICS IO OFF");
        }

        return reads;
    }

    [GeneratedRegex(@"logical reads (\d+)")]
    private static partial Regex LogicalReads();

    /// <summary>A database of its own with the migrations applied and 20,000 products and 20,000 orders, created once for the class.</summary>
    public sealed class PlanDatabase : IAsyncLifetime
    {
        private const int Rows = 20_000;

        private readonly string _database = $"AlQaseh_Plan_{Guid.NewGuid():N}";

        public string ConnectionString =>
            new SqlConnectionStringBuilder(ApiFixture.ServerConnection) { InitialCatalog = _database, Pooling = false }.ConnectionString;

        public async Task InitializeAsync()
        {
            if (string.IsNullOrWhiteSpace(ApiFixture.ServerConnection))
                return;

            var configuration = new ConfigurationBuilder()
                .AddInMemoryCollection(new Dictionary<string, string?> { ["ConnectionStrings:DefaultConnection"] = ConnectionString })
                .Build();
            await new DatabaseMigrator(new DapperContext(configuration), NullLogger<DatabaseMigrator>.Instance).MigrateAsync();

            await using var connection = new SqlConnection(ConnectionString);
            await connection.ExecuteAsync("""
                SET QUOTED_IDENTIFIER ON;
                DISABLE TRIGGER ALL ON Products; DISABLE TRIGGER ALL ON Orders;
                INSERT Users (FullName, UserName, PasswordHash, Role)
                SELECT TOP (50) 'Customer', 'plan' + CAST(ROW_NUMBER() OVER (ORDER BY (SELECT 1)) AS VARCHAR(5)), 'x', 'Customer' FROM sys.all_objects;
                INSERT Users (FullName, UserName, PasswordHash, Role) VALUES ('Admin', 'planadmin', 'x', 'Admin');

                DECLARE @admin BIGINT = (SELECT Id FROM Users WHERE UserName = 'planadmin');
                ;WITH n AS (SELECT TOP (@rows) ROW_NUMBER() OVER (ORDER BY (SELECT 1)) AS i FROM sys.all_objects a CROSS JOIN sys.all_objects b)
                INSERT Products (Name, Category, Price, Cost, AvailableQuantity, CreatedBy)
                SELECT 'Plan product ' + CAST(i AS VARCHAR(10)), CHOOSE(i % 4 + 1, 'furniture', 'electronics', 'beauty', 'garden'), 1000, 500, i % 30, @admin FROM n;

                DECLARE @first BIGINT = (SELECT MIN(Id) FROM Users WHERE UserName LIKE 'plan[0-9]%');
                ;WITH n AS (SELECT TOP (@rows) ROW_NUMBER() OVER (ORDER BY (SELECT 1)) AS i FROM sys.all_objects a CROSS JOIN sys.all_objects b)
                INSERT Orders (CustomerId, SubtotalAmount, DiscountAmount, TotalAmount, TotalCost, PaymentMethod, CreatedAt)
                SELECT @first + i % 50, 1000, 0, 1000, 500, CASE WHEN i % 3 = 0 THEN 'XyzWallet' ELSE 'CreditCard' END, DATEADD(MINUTE, -i, SYSUTCDATETIME()) FROM n;

                UPDATE STATISTICS Products; UPDATE STATISTICS Orders;
                """, new { rows = Rows });
        }

        public async Task DisposeAsync()
        {
            if (string.IsNullOrWhiteSpace(ApiFixture.ServerConnection))
                return;

            await using var master = new SqlConnection(new SqlConnectionStringBuilder(ApiFixture.ServerConnection) { InitialCatalog = "master", Pooling = false }.ConnectionString);
            await master.ExecuteAsync(
                $"IF DB_ID('{_database}') IS NOT NULL BEGIN ALTER DATABASE [{_database}] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [{_database}]; END");
        }
    }
}
