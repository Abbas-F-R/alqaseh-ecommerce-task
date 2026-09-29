using AlQaseh_Ecommerce_API.Shared.Base.dto;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Shared.Base;

public class ResponseTests
{
    [Theory]
    [InlineData(100, 10, 0, 10, false)]
    [InlineData(100, 10, 9, 10, true)]
    [InlineData(25, 10, 0, 3, false)]
    [InlineData(25, 10, 2, 3, true)]
    [InlineData(0, 10, 0, 0, true)]
    public void Response_PaginationCalculations_AreAccurate(
        int totalCount, int pageSize, int currentPage, int expectedPagesCount, bool expectedIsLast)
    {
        var items = new List<string> { "item1" };

        var response = new Response<string>(items, currentPage, totalCount, pageSize);

        response.TotalCount.Should().Be(totalCount);
        response.PagesCount.Should().Be(expectedPagesCount);
        response.CurrentPage.Should().Be(currentPage);
        response.IsLast.Should().Be(expectedIsLast);
        response.Data.Should().BeEquivalentTo(items);
    }
}
