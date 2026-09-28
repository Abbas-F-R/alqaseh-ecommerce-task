using AlQaseh_Ecommerce_API.Shared.Utils;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Shared.Utils;

public class PasswordHasherTests
{
    [Fact]
    public void Hash_GeneratesValidBCryptHash()
    {
        var password = "SecurePassword123!";
        var hash = PasswordHasher.Hash(password);

        hash.Should().NotBeNullOrWhiteSpace();
        hash.Should().StartWith("$2"); // BCrypt prefix
        hash.Should().NotBe(password);
    }

    [Fact]
    public void Verify_CorrectPassword_ReturnsTrue()
    {
        var password = "MySecretPassword!";
        var hash = PasswordHasher.Hash(password);

        var isValid = PasswordHasher.Verify(password, hash);

        isValid.Should().BeTrue();
    }

    [Fact]
    public void Verify_WrongPassword_ReturnsFalse()
    {
        var password = "CorrectPassword123";
        var hash = PasswordHasher.Hash(password);

        var isValid = PasswordHasher.Verify("IncorrectPassword", hash);

        isValid.Should().BeFalse();
    }

    [Fact]
    public void Hash_ProducesDifferentSaltsForSamePassword()
    {
        var password = "IdenticalPassword";
        var hash1 = PasswordHasher.Hash(password);
        var hash2 = PasswordHasher.Hash(password);

        hash1.Should().NotBe(hash2);
    }
}
