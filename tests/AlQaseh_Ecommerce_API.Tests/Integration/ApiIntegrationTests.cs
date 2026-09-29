using System.Text.Json;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Integration;

/// <summary>
/// The real application (pipeline, JWT, validation, Dapper, stored procedures, migrations) against a real SQL Server.
/// Every test creates its own products and discount codes, so they are independent of each other.
/// </summary>
public class ApiIntegrationTests(ApiFixture api) : IClassFixture<ApiFixture>
{
    private static object Order(TestProduct product, int quantity, object payment, string? code = null) =>
        new { items = new[] { new { productId = product.Id, quantity } }, payment, discountCode = code };

    // ------------------------------------------------------------------ authentication and roles

    [SqlServerFact]
    public async Task Login_ReturnsATokenWithTheRole_AndWrongCredentialsAre401()
    {
        var ok = await api.PostAsync("/api/auth/login", null, new { userName = "customer1", password = "Customer123!" });
        ok.Status.Should().Be(200);
        ok.Json.GetProperty("role").GetString().Should().Be("Customer");
        ok.Json.GetProperty("token").GetString().Should().NotBeNullOrEmpty();

        var bad = await api.PostAsync("/api/auth/login", null, new { userName = "customer1", password = "nope" });
        (bad.Status, bad.Code).Should().Be((401, "InvalidCredentials"));

        var unknown = await api.PostAsync("/api/auth/login", null, new { userName = "ghost", password = "nope" });
        (unknown.Status, unknown.Code).Should().Be((401, "InvalidCredentials"));
    }

    [SqlServerFact]
    public async Task EveryEndpointExceptLoginRequiresAToken()
    {
        (await api.GetAsync("/api/admin/products")).Status.Should().Be(401);
        (await api.GetAsync("/api/customer/products")).Status.Should().Be(401);
        (await api.GetAsync("/api/orders")).Status.Should().Be(401);
        (await api.GetAsync("/api/orders/my")).Status.Should().Be(401);
        (await api.PostAsync("/api/orders", null, new { })).Status.Should().Be(401);
        (await api.PostAsync("/api/products", null, new { })).Status.Should().Be(401);
        (await api.GetAsync("/api/customer/products", "garbage.token.value")).Status.Should().Be(401);
    }

    [SqlServerFact]
    public async Task RolesAreEnforced()
    {
        var admin = await api.AdminToken();
        var customer = await api.Customer1Token();
        var product = new { name = "X", category = "garden", price = 1, cost = 1, availableQuantity = 1 };

        (await api.PostAsync("/api/products", customer, product)).Status.Should().Be(403);
        (await api.PutAsync("/api/products/abc", customer, product)).Status.Should().Be(403);
        (await api.GetAsync("/api/orders", customer)).Status.Should().Be(403);
        (await api.GetAsync("/api/admin/products", customer)).Status.Should().Be(403);
        (await api.GetAsync("/api/customer/products", admin)).Status.Should().Be(403);
        (await api.GetAsync("/api/orders/my", admin)).Status.Should().Be(403);
        (await api.PostAsync("/api/orders", admin, new { })).Status.Should().Be(403);
    }

    // ------------------------------------------------------------------ products

    [SqlServerFact]
    public async Task CreateProduct_RecordsTheCreator_LowercasesTheCategory_AndRejectsDuplicateNamesInAnyCase()
    {
        var admin = await api.AdminToken();
        var name = "Sofa " + Guid.NewGuid().ToString("N");

        var created = await api.PostAsync("/api/products", admin, new { name, category = "FURNITURE", price = 100, cost = 60, availableQuantity = 3 });

        created.Status.Should().Be(201);
        created.Json.GetProperty("category").GetString().Should().Be("furniture");
        created.Json.GetProperty("createdBy").GetString().Should().NotBeNullOrEmpty();
        created.Json.GetProperty("createdAt").GetString().Should().EndWith("Z");
        created.Json.GetProperty("updatedBy").ValueKind.Should().Be(JsonValueKind.Null);

        var duplicate = await api.PostAsync("/api/products", admin, new { name = "  " + name.ToUpperInvariant() + " ", category = "garden", price = 1, cost = 1, availableQuantity = 1 });
        (duplicate.Status, duplicate.Code).Should().Be((409, "ProductNameAlreadyExists"));
    }

    [SqlServerFact]
    public async Task TheDatabaseCreatedByTheApplication_UsesTheCaseInsensitiveCollation_ThatUniqueNamesDependOn()
    {
        var collation = await api.QuerySqlAsync<string>("SELECT CAST(DATABASEPROPERTYEX(DB_NAME(), 'Collation') AS NVARCHAR(128))");

        collation.Should().Be(DatabaseMigrator.DatabaseCollation);
    }

    [SqlServerFact]
    public async Task CreateProduct_WithInvalidData_Is400()
    {
        var admin = await api.AdminToken();

        foreach (var body in new object[]
                 {
                     new { name = "", category = "garden", price = 1, cost = 1, availableQuantity = 1 },
                     new { name = "A", category = "toys", price = 1, cost = 1, availableQuantity = 1 },
                     new { name = "A", category = "garden", price = 0, cost = 1, availableQuantity = 1 },
                     new { name = "A", category = "garden", price = 1, cost = -1, availableQuantity = 1 },
                     new { name = "A", category = "garden", price = 10, cost = 10.01m, availableQuantity = 1 }, // sold below its cost
                     new { name = "A", category = "garden", price = 1, cost = 1, availableQuantity = -1 },
                     new { name = "A", category = "garden", price = 100.005m, cost = 1, availableQuantity = 1 },  // would be rounded
                     new { name = "A", category = "garden", price = 1e20m, cost = 1, availableQuantity = 1 }     // would overflow decimal(18,2)
                 })
            (await api.PostAsync("/api/products", admin, body)).Status.Should().Be(400);
    }

    [SqlServerFact]
    public async Task CreateProduct_AcceptsTheLargestPrice_AndAnOrderOfItStillFits()
    {
        var product = await api.NewProductAsync(quantity: 10_000, price: 9_999_999_999.99m, cost: 9_999_999_999.99m);

        var order = await api.PostAsync("/api/orders", await api.Customer1Token(), Order(product, 100, ApiFixture.Card()));

        order.Status.Should().Be(201);
        order.Json.GetProperty("totalPrice").GetDecimal().Should().Be(999_999_999_999m);
    }

    [SqlServerFact]
    public async Task ParallelCreationsOfTheSameName_YieldExactlyOneProduct_AndNoServerError()
    {
        var admin = await api.AdminToken();
        var name = "Race " + Guid.NewGuid().ToString("N");

        var results = await Task.WhenAll(Enumerable.Range(0, 8).Select(_ =>
            api.PostAsync("/api/products", admin, new { name, category = "garden", price = 10, cost = 5, availableQuantity = 1 })));

        results.Count(r => r.Status == 201).Should().Be(1);
        results.Count(r => r.Status == 409).Should().Be(7);
    }

    [SqlServerFact]
    public async Task UpdateProduct_ReplacesTheFields_AndRecordsTheUpdater()
    {
        var admin = await api.AdminToken();
        var product = await api.NewProductAsync(quantity: 5);

        var updated = await api.PutAsync($"/api/products/{product.Id}", admin,
            new { name = product.Name + " v2", category = "Beauty", price = 200_000, cost = 100_000, availableQuantity = 8 });

        updated.Status.Should().Be(200);
        updated.Json.GetProperty("name").GetString().Should().Be(product.Name + " v2");
        updated.Json.GetProperty("category").GetString().Should().Be("beauty");
        updated.Json.GetProperty("availableQuantity").GetInt32().Should().Be(8);
        updated.Json.GetProperty("updatedBy").GetString().Should().NotBeNullOrEmpty();
        updated.Json.GetProperty("updatedAt").GetString().Should().EndWith("Z");
        updated.Json.GetProperty("createdBy").GetString().Should().NotBeNullOrEmpty();
    }

    [SqlServerFact]
    public async Task UpdateProduct_Errors_Are404And409()
    {
        var admin = await api.AdminToken();
        var first = await api.NewProductAsync();
        var second = await api.NewProductAsync();
        var body = new { name = first.Name, category = "garden", price = 1, cost = 1, availableQuantity = 1 };

        (await api.PutAsync("/api/products/zzzzzzzz", admin, body)).Code.Should().Be("ProductNotFound");
        var clash = await api.PutAsync($"/api/products/{second.Id}", admin, body);
        (clash.Status, clash.Code).Should().Be((409, "ProductNameAlreadyExists"));
        (await api.PutAsync($"/api/products/{first.Id}", admin, body)).Status.Should().Be(200, "keeping the product's own name is fine");
    }

    [SqlServerFact]
    public async Task ProductList_AdminsSeeExactQuantity_CustomersSeeOnlyAStockStatus()
    {
        var prefix = "Band-" + Guid.NewGuid().ToString("N")[..8];
        foreach (var quantity in new[] { 0, 4, 5, 9, 10, 300 })
            await api.NewProductAsync(quantity, category: "beauty", name: $"{prefix}-{quantity}");

        var customerPage = await api.GetAsync($"/api/customer/products?name={prefix}&limit=50", await api.Customer1Token());
        var adminPage = await api.GetAsync($"/api/admin/products?name={prefix}&pageSize=50", await api.AdminToken());

        var statuses = customerPage.Json.GetProperty("data").EnumerateArray()
            .ToDictionary(p => p.GetProperty("name").GetString()!, p => p.GetProperty("stockStatus").GetString());
        statuses.Should().Equal(new Dictionary<string, string?>
        {
            [$"{prefix}-0"] = "low", [$"{prefix}-4"] = "low", [$"{prefix}-5"] = "limited",
            [$"{prefix}-9"] = "limited", [$"{prefix}-10"] = "available", [$"{prefix}-300"] = "available"
        });
        foreach (var row in customerPage.Json.GetProperty("data").EnumerateArray())
        {
            row.TryGetProperty("availableQuantity", out _).Should().BeFalse();
            row.TryGetProperty("cost", out _).Should().BeFalse();
        }

        adminPage.Json.GetProperty("data").EnumerateArray().Select(p => p.GetProperty("availableQuantity").GetInt32())
            .Should().BeEquivalentTo([0, 4, 5, 9, 10, 300]);
    }

    [SqlServerFact]
    public async Task ProductList_FiltersByNameAndCategory_AndPaginates()
    {
        var prefix = "Filt-" + Guid.NewGuid().ToString("N")[..8];
        for (var i = 1; i <= 5; i++)
            await api.NewProductAsync(category: i <= 3 ? "garden" : "electronics", name: $"{prefix} item {i}");
        var customer = await api.Customer1Token();

        // First page (limit 2)
        var page1 = await api.GetAsync($"/api/customer/products?name={prefix.ToUpperInvariant()}&limit=2", customer);
        page1.Json.GetProperty("data").GetArrayLength().Should().Be(2);
        page1.Json.GetProperty("hasMore").GetBoolean().Should().BeTrue();
        var cursor1 = page1.Json.GetProperty("nextCursor").GetString();
        cursor1.Should().NotBeNullOrWhiteSpace();

        // Second page
        var page2 = await api.GetAsync($"/api/customer/products?name={prefix}&limit=2&cursor={cursor1}", customer);
        page2.Json.GetProperty("data").GetArrayLength().Should().Be(2);
        page2.Json.GetProperty("hasMore").GetBoolean().Should().BeTrue();
        var cursor2 = page2.Json.GetProperty("nextCursor").GetString();
        cursor2.Should().NotBeNullOrWhiteSpace();

        // Ensure no duplicates between pages
        var page1Ids = page1.Json.GetProperty("data").EnumerateArray().Select(x => x.GetProperty("id").GetString()).ToList();
        var page2Ids = page2.Json.GetProperty("data").EnumerateArray().Select(x => x.GetProperty("id").GetString()).ToList();
        page1Ids.Intersect(page2Ids).Should().BeEmpty();

        // Third/last page
        var page3 = await api.GetAsync($"/api/customer/products?name={prefix}&limit=2&cursor={cursor2}", customer);
        page3.Json.GetProperty("data").GetArrayLength().Should().Be(1);
        page3.Json.GetProperty("hasMore").GetBoolean().Should().BeFalse();
        (page3.Json.GetProperty("nextCursor").ValueKind == System.Text.Json.JsonValueKind.Null).Should().BeTrue();

        var page3Ids = page3.Json.GetProperty("data").EnumerateArray().Select(x => x.GetProperty("id").GetString()).ToList();
        page1Ids.Concat(page2Ids).Concat(page3Ids).Should().HaveCount(5);

        // Category filter
        var gardens = await api.GetAsync($"/api/customer/products?name={prefix}&category=GARDEN&limit=10", customer);
        gardens.Json.GetProperty("data").GetArrayLength().Should().Be(3);
        gardens.Json.GetProperty("hasMore").GetBoolean().Should().BeFalse();

        // Literal %
        (await api.GetAsync($"/api/customer/products?name={prefix}%25", customer)).Json.GetProperty("data").GetArrayLength()
            .Should().Be(0, "% is searched literally, not as a wildcard");
    }

    [SqlServerFact]
    public async Task ProductList_RejectsBadPagingAndCategories()
    {
        var customer = await api.Customer1Token();

        foreach (var query in new[] { "limit=0", "limit=51", "limit=-1", "limit=abc", "category=toys", "cursor=not-a-valid-cursor" })
            (await api.GetAsync($"/api/customer/products?{query}", customer)).Status.Should().Be(400, query);
    }

    [SqlServerFact]
    public async Task AdminProductList_UsesPageOffsetPagination_WithTotals_ZeroBased()
    {
        var prefix = "Adm-" + Guid.NewGuid().ToString("N")[..8];
        for (var i = 1; i <= 5; i++)
            await api.NewProductAsync(category: i <= 3 ? "garden" : "electronics", name: $"{prefix} item {i}");
        var admin = await api.AdminToken();

        var first = await api.GetAsync($"/api/admin/products?name={prefix}&pageNumber=0&pageSize=2", admin);
        (first.Json.GetProperty("currentPage").GetInt32(), first.Json.GetProperty("pagesCount").GetInt32(),
            first.Json.GetProperty("totalCount").GetInt32(), first.Json.GetProperty("isLast").GetBoolean())
            .Should().Be((0, 3, 5, false));
        first.Json.GetProperty("data").GetArrayLength().Should().Be(2);
        first.Json.TryGetProperty("nextCursor", out _).Should().BeFalse("the admin list has no cursor");

        var last = await api.GetAsync($"/api/admin/products?name={prefix}&page=2&size=2", admin);
        (last.Json.GetProperty("currentPage").GetInt32(), last.Json.GetProperty("isLast").GetBoolean(), last.Json.GetProperty("data").GetArrayLength())
            .Should().Be((2, true, 1));

        var beyond = await api.GetAsync($"/api/admin/products?name={prefix}&pageNumber=9&pageSize=2", admin);
        (beyond.Status, beyond.Json.GetProperty("data").GetArrayLength(), beyond.Json.GetProperty("totalCount").GetInt32()).Should().Be((200, 0, 5));

        (await api.GetAsync($"/api/admin/products?name={prefix}&category=GARDEN", admin)).Json.GetProperty("totalCount").GetInt32().Should().Be(3);

        var ids = new List<string>();
        for (var page = 0; page < 3; page++)
            ids.AddRange((await api.GetAsync($"/api/admin/products?name={prefix}&pageNumber={page}&pageSize=2", admin))
                .Json.GetProperty("data").EnumerateArray().Select(p => p.GetProperty("id").GetString()!));
        ids.Should().HaveCount(5).And.OnlyHaveUniqueItems();

        foreach (var query in new[] { "pageSize=0", "pageSize=51", "pageNumber=-1", "category=toys" })
            (await api.GetAsync($"/api/admin/products?{query}", admin)).Status.Should().Be(400, query);
    }

    // ------------------------------------------------------------------ orders

    [SqlServerFact]
    public async Task PlacingAnOrder_ReducesStock_AppliesTheDiscount_AndTheCodeWorksOnlyOnce()
    {
        var product = await api.NewProductAsync(quantity: 10, price: 30_000, cost: 18_000);
        var code = await api.NewDiscountCodeAsync(amount: 5_000, minimum: 25_000);
        var customer = await api.Customer1Token();

        var order = await api.PostAsync("/api/orders", customer, Order(product, 2, ApiFixture.Card(), code.ToLowerInvariant()));

        order.Status.Should().Be(201);
        order.Json.GetProperty("totalPrice").GetDecimal().Should().Be(55_000, "2 x 30,000 - 5,000");
        order.Json.GetProperty("discountAmount").GetDecimal().Should().Be(5_000);
        order.Json.GetProperty("paymentMethod").GetString().Should().Be("CreditCard");
        order.Json.GetProperty("purchaseDate").GetString().Should().EndWith("Z");
        (await api.StockOf(product)).Should().Be(8);

        var second = await api.PostAsync("/api/orders", await api.Customer2Token(), Order(product, 1, ApiFixture.Wallet(), code));
        (second.Status, second.Code).Should().Be((409, "DiscountAlreadyUsed"));
        (await api.StockOf(product)).Should().Be(8, "the failed order changed nothing");
    }

    [SqlServerFact]
    public async Task DiscountCodeRules_MinimumTotalExpiryUnknownAndTooLarge()
    {
        var product = await api.NewProductAsync(price: 30_000, cost: 10_000);
        var customer = await api.Customer1Token();

        var tooSmall = await api.NewDiscountCodeAsync(minimum: 50_000);
        (await api.PostAsync("/api/orders", customer, Order(product, 1, ApiFixture.Card(), tooSmall))).Code.Should().Be("MinimumOrderTotalNotMet");

        var expired = await api.NewDiscountCodeAsync(expiresInDays: -1);
        (await api.PostAsync("/api/orders", customer, Order(product, 1, ApiFixture.Card(), expired))).Code.Should().Be("DiscountExpired");

        (await api.PostAsync("/api/orders", customer, Order(product, 1, ApiFixture.Card(), "NOSUCHCODE"))).Code.Should().Be("DiscountNotFound");

        var huge = await api.NewDiscountCodeAsync(amount: 999_999, minimum: 0);
        (await api.PostAsync("/api/orders", customer, Order(product, 1, ApiFixture.Card(), huge))).Code.Should().Be("DiscountExceedsTotal");

        var exact = await api.NewDiscountCodeAsync(amount: 5_000, minimum: 30_000);
        (await api.PostAsync("/api/orders", customer, Order(product, 1, ApiFixture.Card(), exact))).Status.Should().Be(201, "a total equal to the minimum qualifies");

        (await api.StockOf(product)).Should().Be(9, "only the last order went through");
    }

    [SqlServerFact]
    public async Task DeclinedPayment_Is402_AndRollsBackStockAndTheDiscountCode()
    {
        var product = await api.NewProductAsync(quantity: 10);
        var code = await api.NewDiscountCodeAsync();
        var customer = await api.Customer1Token();

        var declined = await api.PostAsync("/api/orders", customer, Order(product, 3, ApiFixture.Card("4000000000000002"), code));
        (declined.Status, declined.Code).Should().Be((402, "PaymentFailed"));

        (await api.StockOf(product)).Should().Be(10);
        (await api.QuerySqlAsync<bool>("SELECT Used FROM DiscountCodes WHERE Code = @code", new { code })).Should().BeFalse();
        (await api.GetAsync("/api/orders/my?pageSize=50", customer)).Json.GetProperty("data").EnumerateArray()
            .Should().NotContain(o => o.GetProperty("items").EnumerateArray().Any(i => i.GetProperty("productId").GetString() == product.Id));

        var walletDeclined = await api.PostAsync("/api/orders", customer, Order(product, 1, ApiFixture.Wallet("wrong-password")));
        walletDeclined.Status.Should().Be(402);

        (await api.PostAsync("/api/orders", customer, Order(product, 3, ApiFixture.Card(), code))).Status.Should().Be(201, "the code survived the declined attempt");
    }

    [SqlServerFact]
    public async Task OrderWithAMissingOrOutOfStockProduct_ChangesNothing()
    {
        var plenty = await api.NewProductAsync(quantity: 10);
        var scarce = await api.NewProductAsync(quantity: 1);
        var customer = await api.Customer1Token();

        var insufficient = await api.PostAsync("/api/orders", customer, new
        {
            items = new[] { new { productId = plenty.Id, quantity = 2 }, new { productId = scarce.Id, quantity = 5 } },
            payment = ApiFixture.Card()
        });
        (insufficient.Status, insufficient.Code).Should().Be((409, "InsufficientStock"));
        (await api.StockOf(plenty)).Should().Be(10, "the first line was rolled back with the failing one");

        var missing = await api.PostAsync("/api/orders", customer, new
        {
            items = new[] { new { productId = plenty.Id, quantity = 1 }, new { productId = "zzzzzzzz", quantity = 1 } },
            payment = ApiFixture.Card()
        });
        (missing.Status, missing.Code).Should().Be((404, "ProductNotFound"));
        (await api.StockOf(plenty)).Should().Be(10);
    }

    [SqlServerFact]
    public async Task OrderValidation_Is400()
    {
        var product = await api.NewProductAsync();
        var customer = await api.Customer1Token();

        foreach (var body in new object[]
                 {
                     new { items = Array.Empty<object>(), payment = ApiFixture.Card() },
                     Order(product, 0, ApiFixture.Card()),
                     Order(product, -2, ApiFixture.Card()),
                     Order(product, 1, new { method = "Bitcoin" }),
                     Order(product, 1, new { method = "CreditCard" }),
                     Order(product, 1, new { method = "XyzWallet", walletPassword = "x" }),
                     Order(product, 1, new { method = "XyzWallet", phoneNumber = "07701234567" })
                 })
            (await api.PostAsync("/api/orders", customer, body)).Status.Should().Be(400);

        (await api.StockOf(product)).Should().Be(10);
    }

    [SqlServerFact]
    public async Task ParallelOrders_NeverOversell_AndACodeIsRedeemedOnce()
    {
        var product = await api.NewProductAsync(quantity: 3);
        var customers = new[] { await api.Customer1Token(), await api.Customer2Token() };

        var orders = await Task.WhenAll(Enumerable.Range(0, 10).Select(i =>
            api.PostAsync("/api/orders", customers[i % 2], Order(product, 1, ApiFixture.Card()))));

        orders.Count(o => o.Status == 201).Should().Be(3);
        orders.Count(o => o.Status == 409).Should().Be(7);
        (await api.StockOf(product)).Should().Be(0);

        var stocked = await api.NewProductAsync(quantity: 50);
        var code = await api.NewDiscountCodeAsync();
        var redemptions = await Task.WhenAll(Enumerable.Range(0, 8).Select(i =>
            api.PostAsync("/api/orders", customers[i % 2], Order(stocked, 1, ApiFixture.Card(), code))));

        redemptions.Count(o => o.Status == 201).Should().Be(1);
        redemptions.Count(o => o.Status == 409).Should().Be(7);
        (await api.StockOf(stocked)).Should().Be(49, "the six losing orders gave their stock back");
    }

    [SqlServerFact]
    public async Task MyOrders_ShowsOnlyTheCallersOrders_NewestFirst_WithTheRequiredFields()
    {
        var product = await api.NewProductAsync(price: 40_000, cost: 25_000);
        var customer1 = await api.Customer1Token();
        var customer2 = await api.Customer2Token();
        var code = await api.NewDiscountCodeAsync(amount: 5_000, minimum: 25_000);

        var first = await api.PostAsync("/api/orders", customer1, Order(product, 1, ApiFixture.Card(), code));
        var second = await api.PostAsync("/api/orders", customer1, Order(product, 2, ApiFixture.Wallet()));
        var other = await api.PostAsync("/api/orders", customer2, Order(product, 1, ApiFixture.Card()));

        var mine = await api.GetAsync("/api/orders/my?pageSize=50", customer1);
        var ids = mine.Json.GetProperty("data").EnumerateArray().Select(o => o.GetProperty("id").GetString()).ToList();
        ids.Should().Contain([first.Json.GetProperty("id").GetString(), second.Json.GetProperty("id").GetString()]);
        ids.Should().NotContain(other.Json.GetProperty("id").GetString());
        ids.IndexOf(second.Json.GetProperty("id").GetString()).Should().BeLessThan(ids.IndexOf(first.Json.GetProperty("id").GetString()), "newest first");

        var row = mine.Json.GetProperty("data").EnumerateArray().Single(o => o.GetProperty("id").GetString() == first.Json.GetProperty("id").GetString());
        row.GetProperty("totalPrice").GetDecimal().Should().Be(35_000);
        row.GetProperty("discountAmount").GetDecimal().Should().Be(5_000);
        row.GetProperty("paymentMethod").GetString().Should().Be("CreditCard");
        row.GetProperty("purchaseDate").GetString().Should().EndWith("Z");

        (await api.GetAsync("/api/orders/my?pageSize=101", customer1)).Status.Should().Be(400);
        (await api.GetAsync("/api/orders/my?pageNumber=999", customer1)).Json.GetProperty("data").GetArrayLength().Should().Be(0);
    }

    [SqlServerFact]
    public async Task AdminOrderList_ShowsProfit_AndFiltersByCustomerAndPaymentMethod()
    {
        var product = await api.NewProductAsync(price: 50_000, cost: 30_000);
        var customer1 = await api.Customer1Token();
        var customer2 = await api.Customer2Token();
        var code = await api.NewDiscountCodeAsync(amount: 5_000, minimum: 25_000);
        var admin = await api.AdminToken();

        var card = await api.PostAsync("/api/orders", customer1, Order(product, 2, ApiFixture.Card(), code));
        var wallet = await api.PostAsync("/api/orders", customer2, Order(product, 1, ApiFixture.Wallet()));

        var all = await api.GetAsync("/api/orders?pageSize=50", admin);
        var cardRow = all.Json.GetProperty("data").EnumerateArray().Single(o => o.GetProperty("id").GetString() == card.Json.GetProperty("id").GetString());
        cardRow.GetProperty("totalAmount").GetDecimal().Should().Be(95_000);
        cardRow.GetProperty("totalCost").GetDecimal().Should().Be(60_000);
        cardRow.GetProperty("profit").GetDecimal().Should().Be(35_000, "95,000 paid - 60,000 cost");
        cardRow.GetProperty("customerUsername").GetString().Should().Be("customer1");
        foreach (var row in all.Json.GetProperty("data").EnumerateArray())
            row.GetProperty("profit").GetDecimal().Should().Be(row.GetProperty("totalAmount").GetDecimal() - row.GetProperty("totalCost").GetDecimal());

        var customer1Id = cardRow.GetProperty("customerId").GetString();
        var byCustomer = await api.GetAsync($"/api/orders?customerId={customer1Id}&pageSize=50", admin);
        byCustomer.Json.GetProperty("data").EnumerateArray().Should().OnlyContain(o => o.GetProperty("customerUsername").GetString() == "customer1");

        var byMethod = await api.GetAsync("/api/orders?paymentMethod=xyzwallet&pageSize=50", admin);
        byMethod.Json.GetProperty("data").EnumerateArray().Should().OnlyContain(o => o.GetProperty("paymentMethod").GetString() == "XyzWallet")
            .And.Contain(o => o.GetProperty("id").GetString() == wallet.Json.GetProperty("id").GetString());

        var both = await api.GetAsync($"/api/orders?customerId={customer1Id}&paymentMethod=XyzWallet&pageSize=50", admin);
        both.Json.GetProperty("data").EnumerateArray().Should().NotContain(o => o.GetProperty("id").GetString() == card.Json.GetProperty("id").GetString());

        (await api.GetAsync("/api/orders?paymentMethod=Bitcoin", admin)).Status.Should().Be(400);
        (await api.GetAsync("/api/orders?pageSize=51", admin)).Status.Should().Be(400);
    }

    // ------------------------------------------------------------------ errors, languages, documentation

    [SqlServerFact]
    public async Task ErrorMessages_FollowAcceptLanguage()
    {
        var english = await api.SendAsync(HttpMethod.Post, "/api/auth/login", body: new { userName = "admin", password = "x" });
        var arabic = await api.SendAsync(HttpMethod.Post, "/api/auth/login", body: new { userName = "admin", password = "x" }, language: "ar");

        english.Json.GetProperty("detail").GetString().Should().Be("Invalid username or password.");
        arabic.Json.GetProperty("detail").GetString().Should().MatchRegex("[؀-ۿ]");
        arabic.Code.Should().Be("InvalidCredentials");
    }

    private async Task<List<string>> OperationsOf(string document)
    {
        var doc = await api.GetAsync($"/swagger/{document}/swagger.json");
        doc.Status.Should().Be(200);
        doc.Json.GetProperty("components").GetProperty("securitySchemes").GetProperty("Bearer").GetProperty("scheme").GetString().Should().Be("bearer");

        return doc.Json.GetProperty("paths").EnumerateObject()
            .SelectMany(p => p.Value.EnumerateObject().Select(m => $"{m.Name.ToUpperInvariant()} {p.Name}"))
            .OrderBy(x => x, StringComparer.Ordinal).ToList();
    }

    [SqlServerFact]
    public async Task Swagger_HasOneDocumentPerRole_WithOnlyThatRolesEndpoints_AndLoginInBoth_PlusACombinedReference()
    {
        (await OperationsOf("1-admin")).Should().Equal("GET /api/admin/products", "GET /api/orders",
            "POST /api/auth/login", "POST /api/products", "PUT /api/products/{id}");

        (await OperationsOf("2-customer")).Should().Equal("GET /api/customer/products", "GET /api/orders/my",
            "POST /api/auth/login", "POST /api/orders");

        // The combined reference (also used by the Scalar page) lists every endpoint once.
        (await OperationsOf("v1")).Should().Equal("GET /api/admin/products", "GET /api/customer/products", "GET /api/orders",
            "GET /api/orders/my", "POST /api/auth/login", "POST /api/orders", "POST /api/products", "PUT /api/products/{id}");
    }

    // ------------------------------------------------------------------ audit trail (database triggers)

    [SqlServerFact]
    public async Task ProductChanges_AreAuditedByTriggers_WithTheActingAdmin()
    {
        var adminId = await api.QuerySqlAsync<long>("SELECT Id FROM Users WHERE UserName = 'admin'");
        var product = await api.NewProductAsync(quantity: 5);
        var id = await api.QuerySqlAsync<long>("SELECT Id FROM Products WHERE Name = @name", new { name = product.Name });

        await api.PutAsync($"/api/products/{product.Id}", await api.AdminToken(),
            new { name = product.Name, category = "garden", price = 5, cost = 1, availableQuantity = 9 });

        var actions = await api.QuerySqlAsync<string>(
            @"SELECT STRING_AGG(ActionType + ':' + CAST(UserId AS VARCHAR(20)), ',') WITHIN GROUP (ORDER BY Id)
              FROM AuditLog WHERE TableId = (SELECT TableId FROM AuditTables WHERE TableName = 'Products') AND RecordId = @id", new { id });

        actions.Should().Be($"INSERT:{adminId},UPDATE:{adminId}");
    }

    [SqlServerFact]
    public async Task Orders_AreAuditedWithTheCustomerId_IncludingStockAndDiscountChanges()
    {
        var customerId = await api.QuerySqlAsync<long>("SELECT Id FROM Users WHERE UserName = 'customer1'");
        var product = await api.NewProductAsync(quantity: 5);
        var code = await api.NewDiscountCodeAsync();

        var order = await api.PostAsync("/api/orders", await api.Customer1Token(), Order(product, 1, ApiFixture.Card(), code));
        order.Status.Should().Be(201);

        var tables = await api.QuerySqlAsync<string>(
            @"SELECT STRING_AGG(t.TableName, ',') WITHIN GROUP (ORDER BY t.TableName)
              FROM AuditLog a JOIN AuditTables t ON t.TableId = a.TableId
              WHERE a.UserId = @customerId AND a.CreatedAt > DATEADD(minute, -5, SYSUTCDATETIME())
                AND a.ActionType IN ('INSERT', 'UPDATE')
                AND (a.Details LIKE '%' + @name + '%' OR a.Details LIKE '%' + @code + '%' OR t.TableName IN ('Orders', 'OrderItems'))",
            new { customerId, name = product.Name, code });

        tables.Should().Contain("DiscountCodes").And.Contain("Orders").And.Contain("OrderItems").And.Contain("Products");
    }

    [SqlServerFact]
    public async Task ADeclinedPayment_LeavesNoAuditTrace()
    {
        var product = await api.NewProductAsync(quantity: 5);
        var before = await api.QuerySqlAsync<int>("SELECT COUNT(*) FROM AuditLog WHERE Details LIKE '%' + @name + '%' AND ActionType = 'UPDATE'", new { name = product.Name });

        (await api.PostAsync("/api/orders", await api.Customer1Token(), Order(product, 1, ApiFixture.Card("4000000000000002")))).Status.Should().Be(402);

        (await api.QuerySqlAsync<int>("SELECT COUNT(*) FROM AuditLog WHERE Details LIKE '%' + @name + '%' AND ActionType = 'UPDATE'", new { name = product.Name }))
            .Should().Be(before, "the rolled-back stock change is not in the audit trail either");
    }

    [SqlServerFact]
    public async Task UsersAreNotAudited_BecauseTheirRowsHoldPasswordHashes()
    {
        (await api.QuerySqlAsync<int>("SELECT COUNT(*) FROM AuditTables WHERE TableName = 'Users'")).Should().Be(0);
        (await api.QuerySqlAsync<int>("SELECT COUNT(*) FROM AuditLog WHERE Details LIKE '%PasswordHash%'")).Should().Be(0);
    }

    [SqlServerFact]
    public async Task UnknownRoutesAndMethods_AreNotServerErrors()
    {
        var admin = await api.AdminToken();

        (await api.GetAsync("/api/nothing", admin)).Status.Should().Be(404);
        (await api.SendAsync(HttpMethod.Delete, "/api/products/abc", admin)).Status.Should().Be(405, "there is no DELETE endpoint");
    }
}
