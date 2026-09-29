using Dapper;
using FluentAssertions;
using Microsoft.Data.SqlClient;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Integration;

/// <summary>
/// The database itself refuses every impossible state, whoever writes the row: each rule is checked with raw SQL on a real SQL Server,
/// bypassing the API. The name of the violated constraint is part of the assertion, so a rule cannot silently disappear.
/// </summary>
public class DatabaseIntegrityTests(ApiFixture api) : IClassFixture<ApiFixture>
{
    private async Task<long> AdminId() => await api.QuerySqlAsync<long>("SELECT Id FROM Users WHERE UserName = 'admin'");

    private async Task<long> NewCustomer()
    {
        var name = "ck" + Guid.NewGuid().ToString("N")[..12];
        await api.ExecuteSqlAsync("INSERT INTO Users (FullName, UserName, PasswordHash, Role) VALUES ('Test', @name, 'x', 'Customer')", new { name });
        return await api.QuerySqlAsync<long>("SELECT Id FROM Users WHERE UserName = @name", new { name });
    }

    private async Task InsertProduct(string? name = null, string category = "garden", decimal price = 10, decimal cost = 5, int quantity = 5,
        long? createdBy = null, long? updatedBy = null, string? updatedAt = null, string? createdAt = null)
    {
        await api.ExecuteSqlAsync(
            @"INSERT INTO Products (Name, Category, Price, Cost, AvailableQuantity, CreatedBy, UpdatedBy, UpdatedAt, CreatedAt)
              VALUES (@name, @category, @price, @cost, @quantity, @createdBy, @updatedBy, @updatedAt, COALESCE(@createdAt, SYSUTCDATETIME()))",
            new { name = name ?? "P-" + Guid.NewGuid().ToString("N"), category, price, cost, quantity, createdBy = createdBy ?? await AdminId(), updatedBy, updatedAt, createdAt });
    }

    private async Task<long> InsertOrder(long customerId, decimal subtotal = 100, decimal discount = 0, decimal total = 100, long? codeId = null, string method = "CreditCard")
    {
        await api.ExecuteSqlAsync(
            @"INSERT INTO Orders (CustomerId, SubtotalAmount, DiscountAmount, TotalAmount, TotalCost, DiscountCodeId, PaymentMethod)
              VALUES (@customerId, @subtotal, @discount, @total, 1, @codeId, @method)",
            new { customerId, subtotal, discount, total, codeId, method });
        return await api.QuerySqlAsync<long>("SELECT MAX(Id) FROM Orders WHERE CustomerId = @customerId", new { customerId });
    }

    private async Task<long> InsertCode(string code, decimal amount = 5, decimal minimum = 0)
    {
        await api.ExecuteSqlAsync("INSERT INTO DiscountCodes (Code, Amount, MinimumOrderTotal, ExpiresAt) VALUES (@code, @amount, @minimum, DATEADD(day, 1, SYSUTCDATETIME()))", new { code, amount, minimum });
        return await api.QuerySqlAsync<long>("SELECT Id FROM DiscountCodes WHERE Code = @code", new { code });
    }

    private static async Task Rejected(Func<Task> statement, string constraint)
    {
        var thrown = await statement.Should().ThrowAsync<SqlException>();
        thrown.Which.Message.Should().Contain(constraint);
    }

    // ---------------------------------------------------------------- products

    [SqlServerFact]
    public async Task Product_Name_MustBeTrimmed_NotBlank_AndUniqueIgnoringCase()
    {
        await Rejected(() => InsertProduct(" Padded"), "CK_Products_Name_Trimmed");
        await Rejected(() => InsertProduct("Padded "), "CK_Products_Name_Trimmed");
        await Rejected(() => InsertProduct("   "), "CK_Products_Name_");

        var name = "Unique " + Guid.NewGuid().ToString("N");
        await InsertProduct(name);
        await Rejected(() => InsertProduct(name.ToUpperInvariant()), "UQ_Products_Name");
    }

    [SqlServerFact]
    public async Task Product_Category_Price_Cost_Quantity_Rules()
    {
        await Rejected(() => InsertProduct(category: "toys"), "CK_Products_Category");
        await Rejected(() => InsertProduct(price: 0, cost: 0), "CK_Products_Price");
        await Rejected(() => InsertProduct(price: -1, cost: 0), "CK_Products_"); // a negative price also breaks the cost rules
        await Rejected(() => InsertProduct(cost: -0.01m), "CK_Products_Cost");
        await Rejected(() => InsertProduct(price: 10, cost: 10.01m), "CK_Products_CostWithinPrice");
        await Rejected(() => InsertProduct(price: 0.01m, cost: 9_999_999_999.99m), "CK_Products_CostWithinPrice");
        await Rejected(() => InsertProduct(quantity: -1), "CK_Products_AvailableQuantity");
        await Rejected(() => InsertProduct(quantity: 1_000_001), "CK_Products_AvailableQuantity_Max");

        await InsertProduct(quantity: 1_000_000);
        await InsertProduct(quantity: 0);
        await InsertProduct(price: 10, cost: 10);     // break-even
        await InsertProduct(price: 0.01m, cost: 0);   // free of cost

        var name = "Reprice " + Guid.NewGuid().ToString("N");
        await InsertProduct(name, price: 10, cost: 5);
        await Rejected(() => api.ExecuteSqlAsync("UPDATE Products SET Price = 4 WHERE Name = @name", new { name }), "CK_Products_CostWithinPrice");
    }

    [SqlServerFact]
    public async Task Product_AuditColumns_ReferenceUsers_AndAreConsistent()
    {
        var admin = await AdminId();

        await Rejected(() => InsertProduct(createdBy: 999_999_999), "FK_Products_CreatedBy");
        await Rejected(() => InsertProduct(updatedBy: 999_999_999, updatedAt: "2999-01-01"), "FK_Products_UpdatedBy");
        await Rejected(() => InsertProduct(updatedBy: admin), "CK_Products_UpdateAudit");            // who without when
        await Rejected(() => InsertProduct(updatedAt: "2999-01-01"), "CK_Products_UpdateAudit");      // when without who
        await Rejected(() => InsertProduct(updatedBy: admin, updatedAt: "2000-01-01"), "CK_Products_UpdateAudit"); // before it was created

        await InsertProduct(updatedBy: admin, updatedAt: "2999-01-01");
        await InsertProduct();
    }

    // ---------------------------------------------------------------- discount codes

    [SqlServerFact]
    public async Task DiscountCode_Format_Amounts_AndUniquenessIgnoringCase()
    {
        await Rejected(() => InsertCode("   "), "CK_DiscountCodes_Code_");
        await Rejected(() => InsertCode(" PADDED"), "CK_DiscountCodes_Code_Trimmed");
        await Rejected(() => InsertCode("PADDED "), "CK_DiscountCodes_Code_Trimmed");
        await Rejected(() => InsertCode("ZERO" + Guid.NewGuid().ToString("N")[..6], amount: 0), "CK_DiscountCodes_Amount");
        await Rejected(() => InsertCode("NEG" + Guid.NewGuid().ToString("N")[..6], minimum: -1), "CK_DiscountCodes_MinimumOrderTotal");

        var code = "U" + Guid.NewGuid().ToString("N")[..8].ToUpperInvariant();
        await InsertCode(code);
        await Rejected(() => InsertCode(code.ToLowerInvariant()), "UQ_DiscountCodes_Code");
    }

    // ---------------------------------------------------------------- orders

    [SqlServerFact]
    public async Task Order_Amounts_PaymentMethod_AndDiscountConsistency()
    {
        var customer = await NewCustomer();
        var code = await InsertCode("O" + Guid.NewGuid().ToString("N")[..8].ToUpperInvariant());

        await Rejected(() => InsertOrder(customer, method: "Cash"), "CK_Orders_PaymentMethod");
        await Rejected(() => InsertOrder(customer, subtotal: 100, discount: 5, total: 90, codeId: code), "CK_Orders_Amounts");   // does not add up
        await Rejected(() => InsertOrder(customer, subtotal: 3, discount: 5, total: -2, codeId: code), "CK_Orders_Amounts");     // negative total
        await Rejected(() => InsertOrder(customer, subtotal: -1, discount: 0, total: -1), "CK_Orders_Amounts");
        await Rejected(() => InsertOrder(customer, subtotal: 100, discount: 5, total: 95), "CK_Orders_DiscountConsistency");     // discount without a code
        await Rejected(() => InsertOrder(customer, subtotal: 100, discount: 0, total: 100, codeId: code), "CK_Orders_DiscountConsistency"); // code without a discount

        await InsertOrder(customer, subtotal: 100, discount: 5, total: 95, codeId: code);
    }

    [SqlServerFact]
    public async Task Order_ForeignKeys_AndSingleUseOfACode()
    {
        var customer = await NewCustomer();
        var code = await InsertCode("S" + Guid.NewGuid().ToString("N")[..8].ToUpperInvariant());

        await Rejected(() => InsertOrder(999_999_999), "FK_Orders_Customer");
        await Rejected(() => InsertOrder(customer, subtotal: 100, discount: 5, total: 95, codeId: 999_999_999), "FK_Orders_DiscountCode");

        await InsertOrder(customer, subtotal: 100, discount: 5, total: 95, codeId: code);
        await Rejected(() => InsertOrder(customer, subtotal: 100, discount: 5, total: 95, codeId: code), "UQ_Orders_DiscountCodeId");
    }

    [SqlServerFact]
    public async Task OrderLines_Quantity_OneLinePerProduct_AndForeignKeys()
    {
        var customer = await NewCustomer();
        var order = await InsertOrder(customer);
        await InsertProduct("Line " + Guid.NewGuid().ToString("N"));
        var product = await api.QuerySqlAsync<long>("SELECT MAX(Id) FROM Products");
        const string line = "INSERT INTO OrderItems (OrderId, ProductId, ProductName, UnitPrice, UnitCost, Quantity) VALUES (@orderId, @productId, 'x', 1, 1, @quantity)";

        await Rejected(() => api.ExecuteSqlAsync(line, new { orderId = order, productId = product, quantity = 0 }), "CK_OrderItems_Quantity");
        await Rejected(() => api.ExecuteSqlAsync(line, new { orderId = order, productId = 999_999_999, quantity = 1 }), "FK_OrderItems_Product");
        await Rejected(() => api.ExecuteSqlAsync(line, new { orderId = 999_999_999, productId = product, quantity = 1 }), "FK_OrderItems_Order");

        await api.ExecuteSqlAsync(line, new { orderId = order, productId = product, quantity = 1 });
        await Rejected(() => api.ExecuteSqlAsync(line, new { orderId = order, productId = product, quantity = 2 }), "UQ_OrderItems_Order_Product");
    }

    // ---------------------------------------------------------------- users

    [SqlServerFact]
    public async Task User_Role_NotBlankName_AndUniquenessIgnoringCase()
    {
        const string insert = "INSERT INTO Users (FullName, UserName, PasswordHash, Role) VALUES ('T', @name, 'x', @role)";

        await Rejected(() => api.ExecuteSqlAsync(insert, new { name = "role" + Guid.NewGuid().ToString("N")[..6], role = "Owner" }), "CK_Users_Role");
        await Rejected(() => api.ExecuteSqlAsync(insert, new { name = "   ", role = "Customer" }), "CK_Users_UserName_NotBlank");
        await Rejected(() => api.ExecuteSqlAsync(insert, new { name = "ADMIN", role = "Customer" }), "UQ_Users_UserName");
    }

    // ---------------------------------------------------------------- on a new database nothing is left untrusted

    [SqlServerFact]
    public async Task OnANewDatabase_EveryCheckAndForeignKeyIsTrusted()
    {
        (await api.QuerySqlAsync<int>("SELECT COUNT(*) FROM sys.check_constraints WHERE is_not_trusted = 1")).Should().Be(0);
        (await api.QuerySqlAsync<int>("SELECT COUNT(*) FROM sys.foreign_keys WHERE is_not_trusted = 1")).Should().Be(0);
        (await api.QuerySqlAsync<int>("SELECT COUNT(*) FROM sys.check_constraints WHERE is_disabled = 1")).Should().Be(0);
    }
}
