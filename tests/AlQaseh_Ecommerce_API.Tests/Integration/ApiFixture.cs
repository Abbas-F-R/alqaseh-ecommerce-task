using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using Dapper;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Data.SqlClient;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Integration;

/// <summary>
/// Marks a test that needs a real SQL Server. It runs when <c>ALQASEH_TEST_SQLSERVER</c> holds a server-level connection string
/// (for example <c>Server=localhost,1433;User Id=sa;Password=...;TrustServerCertificate=True</c>) and is skipped otherwise.
/// </summary>
public sealed class SqlServerFactAttribute : FactAttribute
{
    public SqlServerFactAttribute()
    {
        if (string.IsNullOrWhiteSpace(ApiFixture.ServerConnection))
            Skip = $"Set {ApiFixture.EnvVar} to a SQL Server connection string (without Database=) to run the database integration tests.";
    }
}

/// <summary>
/// Starts the real application in memory against a throw-away database on the configured SQL Server:
/// migrations and the demo seed run exactly as they do in production startup. The database is dropped afterwards.
/// </summary>
public sealed class ApiFixture : IAsyncLifetime
{
    public const string EnvVar = "ALQASEH_TEST_SQLSERVER";
    public static string? ServerConnection => Environment.GetEnvironmentVariable(EnvVar);

    private readonly string _database = $"AlQaseh_Test_{Guid.NewGuid():N}";
    private WebApplicationFactory<Program>? _factory;

    public string ConnectionString =>
        new SqlConnectionStringBuilder(ServerConnection) { InitialCatalog = _database, Pooling = false }.ConnectionString;

    public HttpClient Client => _factory!.CreateClient();

    public async Task InitializeAsync()
    {
        if (string.IsNullOrWhiteSpace(ServerConnection))
            return;

        _factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Development");
            builder.UseSetting("ConnectionStrings:DefaultConnection", ConnectionString);
            builder.UseSetting("Seed:Enabled", "true");
        });

        // Building the host creates the database, applies the migrations and loads the demo data.
        using var probe = _factory.CreateClient();
        await Task.CompletedTask;
    }

    public async Task DisposeAsync()
    {
        _factory?.Dispose();
        if (string.IsNullOrWhiteSpace(ServerConnection))
            return;

        await using var master = new SqlConnection(new SqlConnectionStringBuilder(ServerConnection) { InitialCatalog = "master", Pooling = false }.ConnectionString);
        await master.ExecuteAsync(
            $"IF DB_ID('{_database}') IS NOT NULL BEGIN ALTER DATABASE [{_database}] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [{_database}]; END");
    }

    public async Task ExecuteSqlAsync(string sql, object? parameters = null)
    {
        await using var connection = new SqlConnection(ConnectionString);
        await connection.ExecuteAsync(sql, parameters);
    }

    public async Task<T> QuerySqlAsync<T>(string sql, object? parameters = null)
    {
        await using var connection = new SqlConnection(ConnectionString);
        return await connection.ExecuteScalarAsync<T>(sql, parameters) ?? throw new InvalidOperationException("No value");
    }

    /// <summary>A discount code that only this test uses.</summary>
    public async Task<string> NewDiscountCodeAsync(decimal amount = 5_000, decimal minimum = 25_000, int expiresInDays = 30)
    {
        var code = "T" + Guid.NewGuid().ToString("N")[..10].ToUpperInvariant();
        await ExecuteSqlAsync(
            "INSERT INTO DiscountCodes (Code, Amount, MinimumOrderTotal, ExpiresAt) VALUES (@code, @amount, @minimum, DATEADD(day, @days, SYSUTCDATETIME()))",
            new { code, amount, minimum, days = expiresInDays });
        return code;
    }

    // ---- HTTP helpers

    public async Task<string> LoginAsync(string userName, string password)
    {
        using var client = Client;
        var response = await client.PostAsJsonAsync("/api/auth/login", new { userName, password });
        response.EnsureSuccessStatusCode();
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        return json.RootElement.GetProperty("token").GetString()!;
    }

    public Task<string> AdminToken() => LoginAsync("admin", "Admin123!");
    public Task<string> Customer1Token() => LoginAsync("customer1", "Customer123!");
    public Task<string> Customer2Token() => LoginAsync("customer2", "Customer123!");

    public async Task<ApiResponse> SendAsync(HttpMethod method, string path, string? token = null, object? body = null, string? language = null)
    {
        using var client = Client;
        using var request = new HttpRequestMessage(method, path);
        if (token is not null)
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        if (language is not null)
            request.Headers.AcceptLanguage.ParseAdd(language);
        if (body is not null)
            request.Content = JsonContent.Create(body);

        using var response = await client.SendAsync(request);
        var text = await response.Content.ReadAsStringAsync();
        return new ApiResponse((int)response.StatusCode, string.IsNullOrWhiteSpace(text) ? default : JsonDocument.Parse(text).RootElement.Clone());
    }

    public Task<ApiResponse> GetAsync(string path, string? token = null) => SendAsync(HttpMethod.Get, path, token);
    public Task<ApiResponse> PostAsync(string path, string? token, object body) => SendAsync(HttpMethod.Post, path, token, body);
    public Task<ApiResponse> PutAsync(string path, string? token, object body) => SendAsync(HttpMethod.Put, path, token, body);

    /// <summary>Creates a product as admin.</summary>
    public async Task<TestProduct> NewProductAsync(int quantity = 10, decimal price = 100_000, decimal cost = 60_000, string category = "furniture", string? name = null)
    {
        name ??= "P-" + Guid.NewGuid().ToString("N");
        var response = await PostAsync("/api/products", await AdminToken(), new { name, category, price, cost, availableQuantity = quantity });
        Assert.Equal(201, response.Status);
        return new TestProduct(response.Json.GetProperty("id").GetString()!, name);
    }

    /// <summary>The exact stock of a product, as the admin list shows it.</summary>
    public async Task<int> StockOf(TestProduct product)
    {
        var page = await GetAsync($"/api/admin/products?name={Uri.EscapeDataString(product.Name)}", await AdminToken());
        return page.Json.GetProperty("data").EnumerateArray().Single(p => p.GetProperty("id").GetString() == product.Id)
            .GetProperty("availableQuantity").GetInt32();
    }

    public static object Card(string number = "4111111111111111") => new { method = "CreditCard", cardNumber = number };
    public static object Wallet(string password = "secret") => new { method = "XyzWallet", phoneNumber = "+9647701234567", walletPassword = password };
}

public sealed record TestProduct(string Id, string Name);

public sealed record ApiResponse(int Status, JsonElement Json)
{
    public string? Code => Json.ValueKind == JsonValueKind.Object && Json.TryGetProperty("code", out var code) ? code.GetString() : null;
}
