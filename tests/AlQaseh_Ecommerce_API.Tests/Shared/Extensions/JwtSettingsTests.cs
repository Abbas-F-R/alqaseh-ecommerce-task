using AlQaseh_Ecommerce_API.Shared.Extensions;
using FluentAssertions;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Hosting;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Shared.Extensions;

public class JwtSettingsTests
{
    private static IConfiguration Config(params (string Key, string? Value)[] values) =>
        new ConfigurationBuilder().AddInMemoryCollection(values.Select(v => new KeyValuePair<string, string?>(v.Key, v.Value))).Build();

    private static IHostEnvironment Env(string name)
    {
        var env = new Mock<IHostEnvironment>();
        env.Setup(e => e.EnvironmentName).Returns(name);
        return env.Object;
    }

    [Fact]
    public void ConfiguredKeyAndLifetime_AreUsed()
    {
        var key = new string('a', 40);

        var settings = JwtSettings.Load(Config(("Jwt:SecretKey", key), ("Jwt:ExpiresMinutes", "15")), Env(Environments.Production));

        settings.SecretKey.Should().Be(key);
        settings.Lifetime.Should().Be(TimeSpan.FromMinutes(15));
    }

    [Fact]
    public void LifetimeDefaultsToOneHour() =>
        JwtSettings.Load(Config(("Jwt:SecretKey", new string('a', 40))), Env(Environments.Production)).Lifetime.Should().Be(TimeSpan.FromHours(1));

    [Fact]
    public void InDevelopment_AMissingKeyBecomesARandomOneEveryTime()
    {
        var first = JwtSettings.Load(Config(), Env(Environments.Development));
        var second = JwtSettings.Load(Config(), Env(Environments.Development));

        first.SecretKey.Length.Should().BeGreaterThanOrEqualTo(JwtSettings.MinKeyLength);
        first.SecretKey.Should().NotBe(second.SecretKey);
    }

    [Theory]
    [InlineData("Production")]
    [InlineData("Staging")]
    public void OutsideDevelopment_AMissingKeyStopsTheApplication(string environment)
    {
        var load = () => JwtSettings.Load(Config(), Env(environment));

        load.Should().Throw<InvalidOperationException>().WithMessage("*Jwt__SecretKey*");
    }

    [Fact]
    public void AShortKey_IsRejected()
    {
        var load = () => JwtSettings.Load(Config(("Jwt:SecretKey", "too-short")), Env(Environments.Development));

        load.Should().Throw<InvalidOperationException>().WithMessage("*at least 32*");
    }

    [Fact]
    public void ANonPositiveLifetime_IsRejected()
    {
        var load = () => JwtSettings.Load(Config(("Jwt:SecretKey", new string('a', 40)), ("Jwt:ExpiresMinutes", "0")), Env(Environments.Development));

        load.Should().Throw<InvalidOperationException>();
    }
}
