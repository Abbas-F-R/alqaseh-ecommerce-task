using System.Reflection;
using System.Text.RegularExpressions;
using Dapper;
using Microsoft.Data.SqlClient;

namespace AlQaseh_Ecommerce_API.Infrastructure.Persistence;

/// <summary>
/// Creates the database when it does not exist and applies the versioned SQL scripts embedded from <c>Database/Migrations</c>
/// (<c>V001__Name.sql</c>, ...) in name order, exactly once each. Applied versions are recorded in <c>SchemaMigrations</c>.
/// A session application lock keeps two instances starting at the same time from migrating concurrently.
/// </summary>
public sealed partial class DatabaseMigrator(DapperContext context, ILogger<DatabaseMigrator> logger)
{
    private const string ResourceMarker = ".Database.Migrations.";
    private const string LockResource = "AlQaseh_Ecommerce_API.Migrations";

    public async Task MigrateAsync(CancellationToken cancellationToken = default)
    {
        await EnsureDatabaseExistsAsync(cancellationToken);

        await using var connection = context.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        var lockResult = await connection.ExecuteScalarAsync<int>(new CommandDefinition(
            "DECLARE @r INT; EXEC @r = sp_getapplock @Resource = @Resource, @LockMode = 'Exclusive', @LockOwner = 'Session', @LockTimeout = 60000; SELECT @r;",
            new { Resource = LockResource }, cancellationToken: cancellationToken));
        if (lockResult < 0)
            throw new InvalidOperationException("Could not acquire the database migration lock (another instance is migrating).");

        await connection.ExecuteAsync(new CommandDefinition(
            "IF OBJECT_ID(N'SchemaMigrations', N'U') IS NULL " +
            "CREATE TABLE SchemaMigrations (Version NVARCHAR(200) NOT NULL PRIMARY KEY, AppliedAt DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME());",
            cancellationToken: cancellationToken));

        var applied = (await connection.QueryAsync<string>(new CommandDefinition("SELECT Version FROM SchemaMigrations", cancellationToken: cancellationToken)))
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        // Self-healing: if V002 was marked as applied in an earlier run, verify that its views and procedures
        // (such as SeedUserIfNotExists and vw_Products) actually exist. If missing, schedule V002 for re-application.
        if (applied.Contains("V002__Procedures.sql"))
        {
            var v002ObjectsExist = await connection.ExecuteScalarAsync<int>(new CommandDefinition(
                "SELECT CASE WHEN OBJECT_ID(N'SeedUserIfNotExists', N'P') IS NOT NULL AND OBJECT_ID(N'vw_Products', N'V') IS NOT NULL THEN 1 ELSE 0 END",
                cancellationToken: cancellationToken));
            if (v002ObjectsExist == 0)
            {
                logger.LogWarning("[Migrations] V002__Procedures.sql was previously applied but key procedures/views are missing; scheduling re-application.");
                applied.Remove("V002__Procedures.sql");
            }
        }

        foreach (var (version, script) in ReadScripts())
        {
            if (applied.Contains(version))
                continue;

            logger.LogInformation("[Migrations] Applying {Version}", version);

            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            foreach (var batch in SplitBatches(script))
                await connection.ExecuteAsync(new CommandDefinition(batch, transaction: transaction, cancellationToken: cancellationToken));

            await connection.ExecuteAsync(new CommandDefinition(
                "IF NOT EXISTS (SELECT 1 FROM SchemaMigrations WHERE Version = @Version) " +
                "INSERT INTO SchemaMigrations (Version) VALUES (@Version) " +
                "ELSE UPDATE SchemaMigrations SET AppliedAt = SYSUTCDATETIME() WHERE Version = @Version;",
                new { Version = version }, transaction, cancellationToken: cancellationToken));
            await transaction.CommitAsync(cancellationToken);
        }
    }

    /// <summary>Splits a script on lines that contain only <c>GO</c> (the sqlcmd batch separator) and drops empty batches.</summary>
    public static IReadOnlyList<string> SplitBatches(string script) =>
        BatchSeparator().Split(script).Select(b => b.Trim()).Where(b => b.Length > 0).ToList();

    private async Task EnsureDatabaseExistsAsync(CancellationToken cancellationToken)
    {
        var builder = new SqlConnectionStringBuilder(context.ConnectionString);
        var database = builder.InitialCatalog;
        if (string.IsNullOrWhiteSpace(database))
            return;

        try
        {
            // Not pooled: a failed login on a pooled connection would block the pool for a few seconds and fail the retry after CREATE DATABASE.
            builder.Pooling = false;
            await using var probe = new SqlConnection(builder.ConnectionString);
            await probe.OpenAsync(cancellationToken);
            return;
        }
        catch (SqlException ex) when (ex.Number == 4060) // cannot open database: it does not exist yet
        {
            logger.LogInformation("[Migrations] Database {Database} does not exist; creating it", database);
        }

        builder.InitialCatalog = "master";
        builder.Pooling = false;
        await using var master = new SqlConnection(builder.ConnectionString);
        await master.OpenAsync(cancellationToken);
        await master.ExecuteAsync(new CommandDefinition(
            "IF DB_ID(@Name) IS NULL BEGIN DECLARE @sql NVARCHAR(MAX) = N'CREATE DATABASE ' + QUOTENAME(@Name); EXEC (@sql); END",
            new { Name = database }, cancellationToken: cancellationToken));
    }

    private static IEnumerable<(string Version, string Script)> ReadScripts()
    {
        var assembly = Assembly.GetExecutingAssembly();

        foreach (var resource in assembly.GetManifestResourceNames()
                     .Where(n => n.Contains(ResourceMarker, StringComparison.Ordinal) && n.EndsWith(".sql", StringComparison.OrdinalIgnoreCase))
                     .OrderBy(n => n, StringComparer.Ordinal))
        {
            using var stream = assembly.GetManifestResourceStream(resource)!;
            using var reader = new StreamReader(stream);
            yield return (resource[(resource.IndexOf(ResourceMarker, StringComparison.Ordinal) + ResourceMarker.Length)..], reader.ReadToEnd());
        }
    }

    [GeneratedRegex(@"^[ \t]*GO[ \t]*\r?$", RegexOptions.Multiline | RegexOptions.IgnoreCase)]
    private static partial Regex BatchSeparator();
}
