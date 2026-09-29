using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using Dapper;
using FluentAssertions;
using Microsoft.Data.SqlClient;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Integration;

/// <summary>
/// A database that was in use before V008 and V009 holds rows those rules forbid (a padded name, a huge stock, a half-filled "last updated",
/// a padded discount code). The migration must still apply: the new constraints guard every new or changed row at once and stay untrusted
/// until the old rows are corrected, after which they become trusted. The old schema is built from the embedded scripts up to V007.
/// </summary>
public sealed class MigrationWithDataTests : IAsyncLifetime
{
    private readonly string _database = $"AlQaseh_Legacy_{Guid.NewGuid():N}";

    private string ConnectionString =>
        new SqlConnectionStringBuilder(ApiFixture.ServerConnection) { InitialCatalog = _database, Pooling = false }.ConnectionString;

    public async Task InitializeAsync()
    {
        if (string.IsNullOrWhiteSpace(ApiFixture.ServerConnection))
            return;

        await using var master = new SqlConnection(new SqlConnectionStringBuilder(ApiFixture.ServerConnection) { InitialCatalog = "master", Pooling = false }.ConnectionString);
        await master.ExecuteAsync($"CREATE DATABASE [{_database}] COLLATE {DatabaseMigrator.DatabaseCollation}");
    }

    public async Task DisposeAsync()
    {
        if (string.IsNullOrWhiteSpace(ApiFixture.ServerConnection))
            return;

        await using var master = new SqlConnection(new SqlConnectionStringBuilder(ApiFixture.ServerConnection) { InitialCatalog = "master", Pooling = false }.ConnectionString);
        await master.ExecuteAsync($"IF DB_ID('{_database}') IS NOT NULL BEGIN ALTER DATABASE [{_database}] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [{_database}]; END");
    }

    /// <summary>Applies the embedded scripts up to and including <paramref name="lastVersion"/> and records them like the migrator does.</summary>
    private static async Task ApplyOldSchema(SqlConnection connection, string lastVersion)
    {
        await connection.ExecuteAsync("CREATE TABLE SchemaMigrations (Version NVARCHAR(200) NOT NULL PRIMARY KEY, AppliedAt DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME())");
        var assembly = typeof(DatabaseMigrator).Assembly;
        var scripts = assembly.GetManifestResourceNames()
            .Where(n => n.Contains(".Database.Migrations.") && n.EndsWith(".sql"))
            .Select(n => (Resource: n, Version: n[(n.IndexOf(".Migrations.", StringComparison.Ordinal) + ".Migrations.".Length)..]))
            .OrderBy(s => s.Version, StringComparer.Ordinal);

        foreach (var (resource, version) in scripts)
        {
            using var reader = new StreamReader(assembly.GetManifestResourceStream(resource)!);
            foreach (var batch in DatabaseMigrator.SplitBatches(await reader.ReadToEndAsync()))
                await connection.ExecuteAsync(batch);
            await connection.ExecuteAsync("INSERT INTO SchemaMigrations (Version) VALUES (@version)", new { version });

            if (version.StartsWith(lastVersion, StringComparison.Ordinal))
                return;
        }
    }

    private async Task Migrate()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?> { ["ConnectionStrings:DefaultConnection"] = ConnectionString }).Build();
        await new DatabaseMigrator(new DapperContext(configuration), NullLogger<DatabaseMigrator>.Instance).MigrateAsync();
    }

    [SqlServerFact]
    public async Task V008AndV009Apply_ToADatabaseWithLegacyRows_AndGuardNewRowsAtOnce()
    {
        await using var connection = new SqlConnection(ConnectionString);
        await connection.OpenAsync();
        await ApplyOldSchema(connection, "V007");

        await connection.ExecuteAsync("INSERT INTO Users (FullName, UserName, PasswordHash, Role) VALUES ('Admin', 'legacyadmin', 'x', 'Admin')");
        var admin = await connection.ExecuteScalarAsync<long>("SELECT Id FROM Users WHERE UserName = 'legacyadmin'");
        const string product = "INSERT INTO Products (Name, Category, Price, Cost, AvailableQuantity, CreatedBy, UpdatedBy) VALUES (@name, 'garden', 10, 5, @quantity, @admin, @updatedBy)";
        await connection.ExecuteAsync(product, new { name = " Padded legacy ", quantity = 1, admin, updatedBy = (long?)null });
        await connection.ExecuteAsync(product, new { name = "Overstocked legacy", quantity = 2_000_000, admin, updatedBy = (long?)null });
        await connection.ExecuteAsync(product, new { name = "Half updated legacy", quantity = 1, admin, updatedBy = (long?)admin }); // who without when
        await connection.ExecuteAsync("INSERT INTO Products (Name, Category, Price, Cost, AvailableQuantity, CreatedBy) VALUES ('Below cost legacy', 'garden', 3, 5, 1, @admin)", new { admin });
        await connection.ExecuteAsync(product, new { name = "Fine legacy", quantity = 1, admin, updatedBy = (long?)null });
        await connection.ExecuteAsync("INSERT INTO DiscountCodes (Code, Amount, MinimumOrderTotal, ExpiresAt) VALUES (' PADDED ', 5, 0, DATEADD(day, 1, SYSUTCDATETIME()))");

        await Migrate(); // must not fail although the legacy rows break the new rules

        (await connection.ExecuteScalarAsync<int>("SELECT COUNT(*) FROM Products")).Should().Be(5);
        var untrusted = (await connection.QueryAsync<string>("SELECT name FROM sys.check_constraints WHERE is_not_trusted = 1 ORDER BY name")).ToList();
        untrusted.Should().Equal("CK_DiscountCodes_Code_Trimmed", "CK_Products_AvailableQuantity_Max", "CK_Products_CostWithinPrice", "CK_Products_Name_Trimmed", "CK_Products_UpdateAudit");

        // new rows are guarded from the first moment ...
        var padded = async () => await connection.ExecuteAsync(product, new { name = " Bad new", quantity = 1, admin, updatedBy = (long?)null });
        (await padded.Should().ThrowAsync<SqlException>()).Which.Message.Should().Contain("CK_Products_Name_Trimmed");
        var overstocked = async () => await connection.ExecuteAsync(product, new { name = "Bad new stock", quantity = 1_000_001, admin, updatedBy = (long?)null });
        (await overstocked.Should().ThrowAsync<SqlException>()).Which.Message.Should().Contain("CK_Products_AvailableQuantity_Max");
        var belowCost = async () => await connection.ExecuteAsync("INSERT INTO Products (Name, Category, Price, Cost, AvailableQuantity, CreatedBy) VALUES ('Below cost new', 'garden', 4, 5, 1, @admin)", new { admin });
        (await belowCost.Should().ThrowAsync<SqlException>()).Which.Message.Should().Contain("CK_Products_CostWithinPrice");
        // ... and so are changes of a legacy row into another invalid state
        var change = async () => await connection.ExecuteAsync("UPDATE Products SET AvailableQuantity = 3000000 WHERE Name = 'Fine legacy'");
        (await change.Should().ThrowAsync<SqlException>()).Which.Message.Should().Contain("CK_Products_AvailableQuantity_Max");

        // once the legacy rows are corrected the constraints can be made trusted
        await connection.ExecuteAsync("UPDATE Products SET Name = LTRIM(RTRIM(Name)), Price = 10, AvailableQuantity = 1, UpdatedBy = NULL, UpdatedAt = NULL");
        await connection.ExecuteAsync("UPDATE DiscountCodes SET Code = LTRIM(RTRIM(Code))");
        foreach (var (table, name) in new[]
                 {
                     ("Products", "CK_Products_Name_Trimmed"), ("Products", "CK_Products_AvailableQuantity_Max"),
                     ("Products", "CK_Products_UpdateAudit"), ("Products", "CK_Products_CostWithinPrice"), ("DiscountCodes", "CK_DiscountCodes_Code_Trimmed")
                 })
            await connection.ExecuteAsync($"ALTER TABLE {table} WITH CHECK CHECK CONSTRAINT {name}");
        (await connection.ExecuteScalarAsync<int>("SELECT COUNT(*) FROM sys.check_constraints WHERE is_not_trusted = 1")).Should().Be(0);
    }

    [SqlServerFact]
    public async Task V008AndV009_OnADatabaseWithCleanData_AreAddedTrusted()
    {
        await using var connection = new SqlConnection(ConnectionString);
        await connection.OpenAsync();
        await ApplyOldSchema(connection, "V007");
        await connection.ExecuteAsync("INSERT INTO Users (FullName, UserName, PasswordHash, Role) VALUES ('Admin', 'cleanadmin', 'x', 'Admin')");
        await connection.ExecuteAsync("INSERT INTO Products (Name, Category, Price, Cost, AvailableQuantity, CreatedBy) SELECT 'Clean', 'garden', 10, 5, 5, Id FROM Users WHERE UserName = 'cleanadmin'");
        await connection.ExecuteAsync("INSERT INTO DiscountCodes (Code, Amount, MinimumOrderTotal, ExpiresAt) VALUES ('CLEAN5', 5, 0, DATEADD(day, 1, SYSUTCDATETIME()))");

        await Migrate();

        (await connection.ExecuteScalarAsync<int>("SELECT COUNT(*) FROM sys.check_constraints WHERE is_not_trusted = 1")).Should().Be(0);
        (await connection.ExecuteScalarAsync<int>("SELECT COUNT(*) FROM Products")).Should().Be(1);
    }
}
