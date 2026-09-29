using System.Reflection;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Infrastructure.Persistence;

public class DatabaseMigratorTests
{
    [Fact]
    public void SplitBatches_SplitsOnGoLinesOnly()
    {
        var script = "CREATE TABLE A (Id INT);\nGO\nINSERT INTO A VALUES (1);\r\nGO\r\n  go  \nSELECT 'GO' AS Word, 1 AS Category;\nGOTO x";

        var batches = DatabaseMigrator.SplitBatches(script);

        batches.Should().Equal(
            "CREATE TABLE A (Id INT);",
            "INSERT INTO A VALUES (1);",
            "SELECT 'GO' AS Word, 1 AS Category;\nGOTO x");
    }

    [Fact]
    public void SplitBatches_DropsEmptyBatches() =>
        DatabaseMigrator.SplitBatches("GO\n\nGO\nSELECT 1\nGO\n").Should().Equal("SELECT 1");

    [Fact]
    public void TheMigrationScripts_AreEmbeddedInOrder()
    {
        var scripts = typeof(DatabaseMigrator).Assembly.GetManifestResourceNames()
            .Where(n => n.Contains(".Database.Migrations.") && n.EndsWith(".sql"))
            .Select(n => n[(n.IndexOf(".Migrations.", StringComparison.Ordinal) + ".Migrations.".Length)..])
            .OrderBy(n => n, StringComparer.Ordinal)
            .ToList();

        scripts.Should().Equal("V001__Schema.sql", "V002__Procedures.sql", "V003__AuditTriggers.sql", "V004__ProductsKeysetPagination.sql", "V005__IntegrityConstraints.sql", "V006__ProductsGetAllPagesFromZero.sql", "V007__OptionalFilterPlans.sql", "V008__ProductIntegrity.sql", "V009__DiscountCodeTrimmed.sql", "V010__UsersGetById.sql");
    }

    [Fact]
    public void EveryEmbeddedScript_SplitsIntoBatches()
    {
        var assembly = typeof(DatabaseMigrator).Assembly;

        foreach (var name in assembly.GetManifestResourceNames().Where(n => n.EndsWith(".sql")))
        {
            using var reader = new StreamReader(assembly.GetManifestResourceStream(name)!);
            DatabaseMigrator.SplitBatches(reader.ReadToEnd()).Should().NotBeEmpty(name);
        }
    }
}
