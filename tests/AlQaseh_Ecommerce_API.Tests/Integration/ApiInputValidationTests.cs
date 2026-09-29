using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Integration;

/// <summary>Input that is too long, too large or malformed is a 400 that names the problem: it never reaches a service or the database.</summary>
public class ApiInputValidationTests(ApiFixture api) : IClassFixture<ApiFixture>
{
    private async Task Login400(object body) =>
        (await api.PostAsync("/api/auth/login", null, body)).Status.Should().Be(400);

    [SqlServerFact]
    public async Task Login_TooLongOrForbiddenCharacters_Is400_AndTheBoundariesAre401()
    {
        await Login400(new { userName = new string('a', 51), password = "x" });
        await Login400(new { userName = "admin", password = new string('p', 129) });
        await Login400(new { userName = "ad min", password = "x" });
        await Login400(new { userName = "admin\r\nforged", password = "x" });
        await Login400(new { userName = "", password = "" });

        (await api.PostAsync("/api/auth/login", null, new { userName = new string('a', 50), password = new string('p', 128) })).Status.Should().Be(401);
    }

    [SqlServerFact]
    public async Task Payment_BadCardPhonePasswordOrFieldsOfTheOtherMethod_Is400()
    {
        var product = await api.NewProductAsync(quantity: 5);
        var token = await api.Customer1Token();
        object Order(object payment) => new { items = new[] { new { productId = product.Id, quantity = 1 } }, payment };

        foreach (var payment in new object[]
                 {
                     new { method = "CreditCard", cardNumber = "4111" },
                     new { method = "CreditCard", cardNumber = new string('4', 20) },
                     new { method = "CreditCard", cardNumber = "abcd1111abcd1111" },
                     new { method = "CreditCard", cardNumber = "\u0664\u0661\u0661\u0661\u0661\u0661\u0661\u0661\u0661\u0661\u0661\u0661\u0661\u0661\u0661\u0661" },
                     new { method = "XyzWallet", phoneNumber = "12", walletPassword = "s" },
                     new { method = "XyzWallet", phoneNumber = "+9647800000000", walletPassword = new string('p', 129) },
                     new { method = "CreditCard", cardNumber = "4111111111111111", walletPassword = "secret" },
                     new { method = "XyzWallet", phoneNumber = "+9647800000000", walletPassword = "s", cardNumber = "4111111111111111" },
                     new { method = "Cash" }
                 })
            (await api.PostAsync("/api/orders", token, Order(payment))).Status.Should().Be(400, System.Text.Json.JsonSerializer.Serialize(payment));

        (await api.PostAsync("/api/orders", token, Order(ApiFixture.Wallet()))).Status.Should().Be(201);
    }

    [SqlServerFact]
    public async Task Lists_PageAboveTheMaximum_OrAnOverflowingPage_Is400_NotA500()
    {
        var admin = await api.AdminToken();
        var customer = await api.Customer1Token();

        foreach (var query in new[] { "pageNumber=100001", "pageNumber=2147483647", "page=2147483647&size=50", "name=" + new string('a', 151) })
            (await api.GetAsync("/api/admin/products?" + query, admin)).Status.Should().Be(400, query);
        (await api.GetAsync("/api/admin/products?pageNumber=100000", admin)).Status.Should().Be(200);

        (await api.GetAsync("/api/orders?pageNumber=2147483647", admin)).Status.Should().Be(400);
        (await api.GetAsync("/api/orders?customer=" + new string('a', 51), admin)).Status.Should().Be(400);
        (await api.GetAsync("/api/orders/my?pageNumber=2147483647", customer)).Status.Should().Be(400);
        (await api.GetAsync("/api/customer/products?cursor=" + new string('A', 101), customer)).Status.Should().Be(400);
        (await api.GetAsync("/api/customer/products?name=" + new string('a', 151), customer)).Status.Should().Be(400);
    }

    [SqlServerFact]
    public async Task Product_MissingOrTextNumbers_Is400_AndNothingIsStored()
    {
        var admin = await api.AdminToken();
        var name = "M-" + Guid.NewGuid().ToString("N");

        foreach (var body in new object[]
                 {
                     new { name, category = "garden", price = 10, availableQuantity = 1 },                    // cost missing
                     new { name, category = "garden", price = 10, cost = 5 },                                 // quantity missing
                     new { name, category = "garden", cost = 5, availableQuantity = 1 },                      // price missing
                     new { name, category = "garden", price = 10, cost = 5, availableQuantity = "5" },        // a number sent as text
                     new { name, category = "garden", price = "10", cost = 5, availableQuantity = 1 },
                     new { name, category = "garden", price = 10, cost = 5, availableQuantity = 1.5 }         // a fraction is not truncated
                 })
            (await api.PostAsync("/api/products", admin, body)).Status.Should().Be(400);

        (await api.QuerySqlAsync<int>("SELECT COUNT(*) FROM Products WHERE Name = @name", new { name })).Should().Be(0);
    }

    [SqlServerFact]
    public async Task Ids_OnlyTheExactSqidIsAccepted_AnAliasOrANullLineIs400()
    {
        var product = await api.NewProductAsync(quantity: 5);
        var admin = await api.AdminToken();
        var customer = await api.Customer1Token();
        var payment = new { method = "CreditCard", cardNumber = "4111111111111111" };

        (await api.PostAsync("/api/orders", customer, new { items = new[] { new { productId = "abc", quantity = 1 } }, payment })).Status.Should().Be(400);
        (await api.PostAsync("/api/orders", customer, new { items = new object?[] { null }, payment })).Status.Should().Be(400);

        var alias = product.Id.ToUpperInvariant() == product.Id ? product.Id.ToLowerInvariant() : product.Id.ToUpperInvariant();
        alias.Should().NotBe(product.Id);
        (await api.PutAsync($"/api/products/{alias}", admin, new { name = product.Name, category = "garden", price = 5, cost = 1, availableQuantity = 1 })).Status.Should().Be(400);
        (await api.PostAsync("/api/orders", customer, new { items = new[] { new { productId = alias, quantity = 1 } }, payment })).Status.Should().Be(400);
        (await api.GetAsync("/api/orders?customerId=abc", admin)).Status.Should().Be(400);

        (await api.StockOf(product)).Should().Be(5);
    }

    [SqlServerFact]
    public async Task Product_AnUpdateThatBreaksARule_Is400_AndAnUnknownIdIs404()
    {
        var product = await api.NewProductAsync(quantity: 5);
        var admin = await api.AdminToken();

        (await api.PutAsync($"/api/products/{product.Id}", admin, new { name = product.Name, category = "garden", price = 0, cost = 1, availableQuantity = 1 })).Status.Should().Be(400);
        (await api.PutAsync($"/api/products/{product.Id}", admin, new { name = product.Name, category = "toys", price = 5, cost = 1, availableQuantity = 1 })).Status.Should().Be(400);
        (await api.PutAsync($"/api/products/{product.Id}", admin, new { name = product.Name, category = "garden", price = 5, cost = 5.01m, availableQuantity = 1 })).Status.Should().Be(400);
        (await api.PutAsync($"/api/products/{product.Id}", admin, new { name = product.Name, category = "garden", price = 5, cost = 5, availableQuantity = 1 })).Status.Should().Be(200);
        (await api.PutAsync("/api/products/zzzzzzzz", admin, new { name = "x", category = "garden", price = 5, cost = 1, availableQuantity = 1 })).Status.Should().BeOneOf(400, 404);
    }
}
