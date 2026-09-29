using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Integration;

/// <summary>The stock of a product is 0 to 1,000,000: the API refuses more with a 400 and the database refuses it too (see DatabaseIntegrityTests).</summary>
public class ProductStockBoundTests(ApiFixture api) : IClassFixture<ApiFixture>
{
    [SqlServerFact]
    public async Task Product_StockAboveTheMaximum_Is400_AndTheMaximumIsAccepted()
    {
        var admin = await api.AdminToken();

        (await api.PostAsync("/api/products", admin, new { name = "S-" + Guid.NewGuid().ToString("N"), category = "garden", price = 10, cost = 5, availableQuantity = 1_000_001 }))
            .Status.Should().Be(400);
        (await api.PostAsync("/api/products", admin, new { name = "S-" + Guid.NewGuid().ToString("N"), category = "garden", price = 10, cost = 5, availableQuantity = 1_000_000 }))
            .Status.Should().Be(201);
    }
}
