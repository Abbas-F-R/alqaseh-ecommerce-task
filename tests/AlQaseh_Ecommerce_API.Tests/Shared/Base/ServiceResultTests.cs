using AlQaseh_Ecommerce_API.Shared.Base.dto;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Shared.Base;

public class ServiceResultTests
{
    [Fact]
    public void Ok_CarriesTheData()
    {
        var result = ServiceResult<string>.Ok("data");

        (result.IsSuccess, result.Data, result.Error, result.TotalCount).Should().Be((true, "data", null, 0));
    }

    [Fact]
    public void PagedOk_CarriesTheTotalCount()
    {
        var result = ServiceResult<List<int>>.PagedOk([1, 2], 57);

        result.IsSuccess.Should().BeTrue();
        result.TotalCount.Should().Be(57);
        result.Data.Should().Equal(1, 2);
    }

    [Fact]
    public void Failure_CarriesTheErrorCodeAndNoData()
    {
        var result = ServiceResult<string>.Failure("ProductNotFound");

        (result.IsSuccess, result.Error, result.Data).Should().Be((false, "ProductNotFound", null));
    }
}
