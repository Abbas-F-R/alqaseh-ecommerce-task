namespace AlQaseh_Ecommerce_API.Shared.Constants;

/// <summary>
/// The two user roles of the system. Used in JWT claims, the database CHECK constraint and [Authorize] attributes.
/// </summary>
public static class Roles
{
    public const string Admin = "Admin";
    public const string Customer = "Customer";
    public const string AdminOrCustomer = Admin + "," + Customer;
}
