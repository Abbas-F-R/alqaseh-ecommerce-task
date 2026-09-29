using AlQaseh_Ecommerce_API.Shared.Utils;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Integration;

/// <summary>A token cannot outlive its account or its role: the user behind every authenticated request is checked in the database.</summary>
public class AccountCheckTests(ApiFixture api) : IClassFixture<ApiFixture>
{
    private async Task<string> NewUser(string role)
    {
        var name = "acc" + Guid.NewGuid().ToString("N")[..10];
        await api.ExecuteSqlAsync("INSERT INTO Users (FullName, UserName, PasswordHash, Role) VALUES ('Test', @name, @hash, @role)",
            new { name, hash = PasswordHasher.Hash("Pass1234!"), role });
        return name;
    }

    [SqlServerFact]
    public async Task TokenOfAnExistingAccount_Works()
    {
        var token = await api.LoginAsync(await NewUser("Customer"), "Pass1234!");

        (await api.GetAsync("/api/orders/my", token)).Status.Should().Be(200);
    }

    [SqlServerFact]
    public async Task TokenOfADeletedAccount_Is401()
    {
        var name = await NewUser("Customer");
        var token = await api.LoginAsync(name, "Pass1234!");
        (await api.GetAsync("/api/orders/my", token)).Status.Should().Be(200);

        await api.ExecuteSqlAsync("DELETE FROM Users WHERE UserName = @name", new { name });

        (await api.GetAsync("/api/orders/my", token)).Status.Should().Be(401);
    }

    [SqlServerFact]
    public async Task TokenWhoseRoleChangedAfterItWasIssued_Is401_InBothDirections()
    {
        var customer = await NewUser("Customer");
        var customerToken = await api.LoginAsync(customer, "Pass1234!");
        var admin = await NewUser("Admin");
        var adminToken = await api.LoginAsync(admin, "Pass1234!");
        (await api.GetAsync("/api/orders/my", customerToken)).Status.Should().Be(200);
        (await api.GetAsync("/api/orders", adminToken)).Status.Should().Be(200);

        await api.ExecuteSqlAsync("UPDATE Users SET Role = 'Admin' WHERE UserName = @customer; UPDATE Users SET Role = 'Customer' WHERE UserName = @admin", new { customer, admin });

        (await api.GetAsync("/api/orders/my", customerToken)).Status.Should().Be(401);  // no longer allowed to keep acting as a customer
        (await api.GetAsync("/api/orders", adminToken)).Status.Should().Be(401);        // no longer an admin
    }

    [SqlServerFact]
    public async Task ACustomer_NeverSeesAnotherCustomersOrders()
    {
        var first = await api.NewProductAsync(quantity: 5);
        var second = await api.Customer2Token();
        var order = await api.PostAsync("/api/orders", await api.Customer1Token(),
            new { items = new[] { new { productId = first.Id, quantity = 1 } }, payment = ApiFixture.Card() });
        order.Status.Should().Be(201);

        var mine = await api.GetAsync("/api/orders/my?pageSize=50", await api.Customer1Token());
        var theirs = await api.GetAsync("/api/orders/my?pageSize=50", second);

        mine.Json.GetProperty("data").EnumerateArray().Select(o => o.GetProperty("id").GetString()).Should().Contain(order.Json.GetProperty("id").GetString());
        theirs.Json.GetProperty("data").EnumerateArray().Select(o => o.GetProperty("id").GetString()).Should().NotContain(order.Json.GetProperty("id").GetString());
    }
}
