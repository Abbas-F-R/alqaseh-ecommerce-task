using AlQaseh_Ecommerce_API.Shared.Helper;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Shared.Helper;

public class SqidCodecTests
{
    [Theory]
    [InlineData(1L)]
    [InlineData(42L)]
    [InlineData(1000500L)]
    [InlineData(long.MaxValue)]
    public void EncodeAndDecode_RoundTrip_PreservesValue(long originalId)
    {
        var encoded = SqidCodec.Encode(originalId);

        encoded.Should().NotBeNullOrWhiteSpace();
        encoded.Should().NotBe(originalId.ToString());

        var decoded = SqidCodec.TryDecode(encoded);
        decoded.Should().Be(originalId);
    }

    [Theory]
    [InlineData("")]
    [InlineData(" ")]
    [InlineData(null)]
    public void TryDecode_NullOrEmpty_ReturnsNull(string? invalidSqid)
    {
        var decoded = SqidCodec.TryDecode(invalidSqid);
        decoded.Should().BeNull();
    }

    [Fact]
    public void TryDecode_PlainNumericString_ParsesDirectly()
    {
        var decoded = SqidCodec.TryDecode("12345");
        decoded.Should().Be(12345L);
    }
}
