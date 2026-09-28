using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using AlQaseh_Ecommerce_API.Shared.Extensions;
using Serilog;

var builder = WebApplication.CreateBuilder(args);

// Structured file logging, partitioned by error type.
builder.AddSerilogLogging();

builder.Services.AddControllersExtension();
builder.Services.AddSecurityExtension(builder.Configuration, builder.Environment);
builder.Services.AddSwaggerDocumentation();
builder.Services.AddApplicationServices();

var app = builder.Build();

// Create / migrate the database and (when enabled) load the demo data before serving requests.
await app.InitializeDatabaseAsync();

app.UseApplicationPipeline();

try
{
    Log.Information("Starting AlQaseh_Ecommerce_API web application host...");
    app.Run();
}
catch (Exception ex)
{
    Log.Fatal(ex, "Application host terminated unexpectedly during execution");
}
finally
{
    Log.CloseAndFlush();
}

/// <summary>Entry point marker so that integration tests can start the application in memory.</summary>
public partial class Program;
