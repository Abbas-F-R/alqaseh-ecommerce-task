namespace AlQaseh_Ecommerce_API.Infrastructure.Persistence;

/// <summary>
/// Startup database work: apply the migrations (<c>Database:AutoMigrate</c>, default true), then load the demo data (<c>Seed:Enabled</c>, default false).
/// A failure stops the application at startup with the reason in the log instead of leaving it running without a database.
/// </summary>
public static class DatabaseInitializer
{
    public static async Task InitializeDatabaseAsync(this WebApplication app)
    {
        using var scope = app.Services.CreateScope();
        var logger = scope.ServiceProvider.GetRequiredService<ILoggerFactory>().CreateLogger("DatabaseInitializer");

        try
        {
            if (app.Configuration.GetValue("Database:AutoMigrate", true))
                await new DatabaseMigrator(
                    scope.ServiceProvider.GetRequiredService<DapperContext>(),
                    scope.ServiceProvider.GetRequiredService<ILogger<DatabaseMigrator>>()).MigrateAsync();

            if (app.Configuration.GetValue("Seed:Enabled", false))
                await new DatabaseSeeder(
                    scope.ServiceProvider.GetRequiredService<DapperContext>(),
                    scope.ServiceProvider.GetRequiredService<TimeProvider>(),
                    scope.ServiceProvider.GetRequiredService<ILogger<DatabaseSeeder>>()).SeedAsync();
        }
        catch (Exception ex)
        {
            logger.LogCritical(ex,
                "Database initialization failed. Check that SQL Server is running and that ConnectionStrings:DefaultConnection is correct.");
            throw;
        }
    }
}
