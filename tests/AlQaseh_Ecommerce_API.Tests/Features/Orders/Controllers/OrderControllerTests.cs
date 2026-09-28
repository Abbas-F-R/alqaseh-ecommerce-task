using AlQaseh_Ecommerce_API.Features.Orders.Controllers;
using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Orders.Services;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using AlQaseh_Ecommerce_API.Tests.TestSupport;
using FluentAssertions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Orders.Controllers;

public class OrderControllerTests
{
    private readonly Mock<IOrderService> _service = new();

    private OrderController Controller(string role = "Customer", long id = 2) =>
        new OrderController(_service.Object).WithUser(id, "someone", role);

    private static string? RolesOf(string method) =>
        typeof(OrderController).GetMethod(method)!.GetCustomAttributes(typeof(AuthorizeAttribute), false)
            .Cast<AuthorizeAttribute>().Single().Roles;

    [Fact]
    public void PlacingOrdersAndListingMyOrders_AreCustomerOnly()
    {
        RolesOf(nameof(OrderController.CreateOrder)).Should().Be("Customer");
        RolesOf(nameof(OrderController.ListMyOrders)).Should().Be("Customer");
    }

    [Fact]
    public void ListingAllOrders_IsAdminOnly() => RolesOf(nameof(OrderController.ListAllOrders)).Should().Be("Admin");

    [Fact]
    public async Task CreateOrder_Returns201WithTheOrder()
    {
        var order = new CustomerOrderResponse { Id = 10, TotalPrice = 200, PaymentMethod = "CreditCard" };
        _service.Setup(s => s.CreateOrder(It.IsAny<ServiceRequest<CreateOrderRequest>>())).ReturnsAsync(ServiceResult<CustomerOrderResponse>.Ok(order));

        var result = await Controller().CreateOrder(new CreateOrderRequest());

        var created = result.Result.Should().BeAssignableTo<ObjectResult>().Subject;
        created.StatusCode.Should().Be(201);
        created.Value.Should().BeSameAs(order);
        _service.Verify(s => s.CreateOrder(It.Is<ServiceRequest<CreateOrderRequest>>(r => r.UserId == 2)), Times.Once);
    }

    [Theory]
    [InlineData(Messages.PaymentFailed, 402)]
    [InlineData(Messages.InsufficientStock, 409)]
    [InlineData(Messages.DiscountAlreadyUsed, 409)]
    [InlineData(Messages.ProductNotFound, 404)]
    [InlineData(Messages.DiscountNotFound, 404)]
    [InlineData(Messages.DiscountExpired, 422)]
    [InlineData(Messages.MinimumOrderTotalNotMet, 422)]
    [InlineData(Messages.DiscountExceedsTotal, 422)]
    [InlineData(Messages.PaymentMethodNotSupported, 400)]
    public async Task CreateOrder_Failures_MapToTheirHttpStatusAndCode(string error, int expectedStatus)
    {
        _service.Setup(s => s.CreateOrder(It.IsAny<ServiceRequest<CreateOrderRequest>>()))
            .ReturnsAsync(ServiceResult<CustomerOrderResponse>.Failure(error));

        var result = await Controller().CreateOrder(new CreateOrderRequest());

        var (status, code, detail) = result.Result!.Problem();
        (status, code).Should().Be((expectedStatus, error));
        detail.Should().NotBeNullOrWhiteSpace().And.NotBe(error);
    }

    [Fact]
    public async Task ListMyOrders_ReturnsThePageWithPaginationMetadata()
    {
        _service.Setup(s => s.GetMyOrders(It.IsAny<ServiceRequest<BaseFilter>>()))
            .ReturnsAsync(ServiceResult<List<CustomerOrderResponse>>.PagedOk([new CustomerOrderResponse { Id = 1 }], 11));

        var result = await Controller().ListMyOrders(new BaseFilter { PageNumber = 2, PageSize = 10 });

        var page = result.Result.Should().BeOfType<OkObjectResult>().Subject.Value.Should().BeOfType<Response<CustomerOrderResponse>>().Subject;
        (page.CurrentPage, page.TotalCount, page.PagesCount, page.IsLast).Should().Be((2, 11, 2, true));
    }

    [Fact]
    public async Task ListMyOrders_AsksTheServiceForTheCallersOrdersOnly()
    {
        _service.Setup(s => s.GetMyOrders(It.IsAny<ServiceRequest<BaseFilter>>()))
            .ReturnsAsync(ServiceResult<List<CustomerOrderResponse>>.PagedOk([], 0));

        await Controller(id: 77).ListMyOrders(new BaseFilter());

        _service.Verify(s => s.GetMyOrders(It.Is<ServiceRequest<BaseFilter>>(r => r.UserId == 77)), Times.Once);
    }

    [Fact]
    public async Task ListAllOrders_ReturnsTheAdminPage()
    {
        _service.Setup(s => s.GetAllOrders(It.IsAny<ServiceRequest<OrderFilter>>()))
            .ReturnsAsync(ServiceResult<List<AdminOrderResponse>>.PagedOk([new AdminOrderResponse { Id = 1, Profit = 40 }], 1));

        var result = await Controller("Admin", 1).ListAllOrders(new OrderFilter { PaymentMethod = "XyzWallet" });

        var page = result.Result.Should().BeOfType<OkObjectResult>().Subject.Value.Should().BeOfType<Response<AdminOrderResponse>>().Subject;
        page.Data.Single().Profit.Should().Be(40);
        page.IsLast.Should().BeTrue();
    }
}
