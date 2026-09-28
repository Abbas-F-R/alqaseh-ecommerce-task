using System.Data;
using AlQaseh_Ecommerce_API.Features.Discounts;
using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Orders.Repositories;
using AlQaseh_Ecommerce_API.Features.Orders.Services;
using AlQaseh_Ecommerce_API.Features.Payments;
using AlQaseh_Ecommerce_API.Features.Products.Repositories;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using AlQaseh_Ecommerce_API.Tests.TestSupport;
using FluentAssertions;
using Moq;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Orders.Services;

public class OrderServiceTests
{
    private readonly Mock<IUnitOfWork> _unitOfWork = new();
    private readonly Mock<IOrderRepository> _orders = new();
    private readonly Mock<IProductRepository> _products = new();
    private readonly Mock<IDiscountCodeRepository> _discounts = new();
    private readonly Mock<IPaymentProcessorFactory> _paymentFactory = new();
    private readonly Mock<IPaymentProcessor> _processor = new();
    private readonly IDbTransaction _transaction = new Mock<IDbTransaction>().Object;
    private readonly List<string> _calls = [];
    private readonly OrderService _service;

    public OrderServiceTests()
    {
        // The unit of work just runs the work with a fake transaction; commit / rollback is UnitOfWork's job.
        _unitOfWork.Setup(u => u.ExecuteAsync(It.IsAny<long>(), It.IsAny<Func<IDbTransaction, Task<ServiceResult<CustomerOrderResponse>>>>()))
            .Returns((long _, Func<IDbTransaction, Task<ServiceResult<CustomerOrderResponse>>> work) => work(_transaction));

        _processor.Setup(p => p.Method).Returns(PaymentMethods.CreditCard);
        _processor.Setup(p => p.Charge(It.IsAny<decimal>(), It.IsAny<PaymentRequest>()))
            .Callback(() => _calls.Add("charge")).Returns(new PaymentResult(true, "CC-1"));
        _paymentFactory.Setup(f => f.GetProcessor(PaymentMethods.CreditCard)).Returns(_processor.Object);

        _orders.Setup(o => o.InsertOrder(It.IsAny<NewOrder>(), _transaction))
            .Callback(() => _calls.Add("insert order")).ReturnsAsync(new InsertedOrder { Id = 100, CreatedAt = TestHelpers.Now.UtcDateTime });
        _orders.Setup(o => o.InsertItems(100, It.IsAny<IEnumerable<NewOrderItem>>(), _transaction)).Callback(() => _calls.Add("insert items")).Returns(Task.CompletedTask);
        _discounts.Setup(d => d.MarkAsUsed(It.IsAny<long>(), _transaction)).ReturnsAsync(true);

        _service = new OrderService(_unitOfWork.Object, _orders.Object, _products.Object, _discounts.Object,
            _paymentFactory.Object, new FixedTimeProvider(TestHelpers.Now));
    }

    private void Stock(long id, string name, decimal price, decimal cost, int available) =>
        _products.Setup(p => p.ReserveStock(id, It.IsAny<int>(), _transaction))
            .ReturnsAsync((long _, int qty, IDbTransaction _) => qty <= available ? new ReservedProduct(id, name, price, cost) : null);

    private void Discount(string code, decimal amount, decimal minimum, DateTime? expires = null, bool used = false) =>
        _discounts.Setup(d => d.GetByCodeForUpdate(code, _transaction)).ReturnsAsync(new DiscountCodeDto
        {
            Id = 5, Code = code, Amount = amount, MinimumOrderTotal = minimum, Used = used,
            ExpiresAt = expires ?? TestHelpers.Now.UtcDateTime.AddDays(30)
        });

    private static ServiceRequest<CreateOrderRequest> Order(string? code = null, string method = "CreditCard", params (long Product, int Qty)[] items) =>
        new(new CreateOrderRequest
        {
            Items = (items.Length == 0 ? [(1L, 2)] : items).Select(i => new OrderItemRequest { ProductId = i.Product, Quantity = i.Qty }).ToList(),
            DiscountCode = code,
            Payment = new PaymentRequest { Method = method, CardNumber = "4111111111111111" }
        }, userId: 42);

    // ---- success

    [Fact]
    public async Task ValidOrder_StoresTheOrderWithProfitInputsAndReturnsIt()
    {
        Stock(1, "Chair", 100, 60, 10);

        var result = await _service.CreateOrder(Order());

        result.IsSuccess.Should().BeTrue();
        result.Data!.Id.Should().Be(100);
        result.Data.TotalPrice.Should().Be(200);
        result.Data.DiscountAmount.Should().Be(0);
        result.Data.PaymentMethod.Should().Be("CreditCard");
        result.Data.PurchaseDate.Should().Be(TestHelpers.Now.UtcDateTime);
        result.Data.Items.Should().ContainSingle().Which.Should().BeEquivalentTo(
            new OrderItemResponse { ProductId = 1, ProductName = "Chair", UnitPrice = 100, Quantity = 2, Subtotal = 200 });
        _orders.Verify(o => o.InsertOrder(new NewOrder(42, 200, 0, 200, 120, null, "CreditCard"), _transaction), Times.Once);
        _unitOfWork.Verify(u => u.ExecuteAsync(42, It.IsAny<Func<IDbTransaction, Task<ServiceResult<CustomerOrderResponse>>>>()), Times.Once,
            "the caller's id is handed to the unit of work so the audit triggers know who is acting");
    }

    [Fact]
    public async Task ValidOrder_WithADiscountCode_SubtractsTheFixedAmountAndConsumesTheCode()
    {
        Stock(1, "Chair", 100, 60, 10);
        Discount("ABC123", amount: 10, minimum: 150);

        var result = await _service.CreateOrder(Order("  ABC123 "));

        result.IsSuccess.Should().BeTrue();
        result.Data!.TotalPrice.Should().Be(190);
        result.Data.DiscountAmount.Should().Be(10);
        _discounts.Verify(d => d.MarkAsUsed(5, _transaction), Times.Once);
        _orders.Verify(o => o.InsertOrder(new NewOrder(42, 200, 10, 190, 120, 5, "CreditCard"), _transaction), Times.Once);
    }

    [Fact]
    public async Task ThePaymentIsChargedLast_AfterEverythingElseIsStored()
    {
        Stock(1, "Chair", 100, 60, 10);
        _products.Setup(p => p.ReserveStock(1, 2, _transaction)).Callback(() => _calls.Add("reserve"))
            .ReturnsAsync(new ReservedProduct(1, "Chair", 100, 60));

        await _service.CreateOrder(Order());

        _calls.Should().Equal("reserve", "insert order", "insert items", "charge");
    }

    [Fact]
    public async Task TheSameProductTwice_BecomesOneLine_AndLinesAreProcessedInIdOrder()
    {
        var order = new List<long>();
        _products.Setup(p => p.ReserveStock(It.IsAny<long>(), It.IsAny<int>(), _transaction))
            .Callback((long id, int _, IDbTransaction _) => order.Add(id))
            .ReturnsAsync((long id, int _, IDbTransaction _) => new ReservedProduct(id, "P" + id, 10, 5));

        var result = await _service.CreateOrder(Order(null, "CreditCard", (9, 1), (2, 1), (9, 3)));

        result.Data!.Items.Select(i => (i.ProductId, i.Quantity)).Should().Equal((2L, 1), (9L, 4));
        order.Should().Equal(2, 9);
        result.Data.TotalPrice.Should().Be(50);
    }

    // ---- failures: nothing after the failing step may run

    [Fact]
    public async Task UnsupportedPaymentMethod_FailsBeforeAnythingIsTouched()
    {
        var result = await _service.CreateOrder(Order(method: "Bitcoin"));

        result.Error.Should().Be(Messages.PaymentMethodNotSupported);
        _unitOfWork.Verify(u => u.ExecuteAsync(It.IsAny<long>(), It.IsAny<Func<IDbTransaction, Task<ServiceResult<CustomerOrderResponse>>>>()), Times.Never);
    }

    [Fact]
    public async Task UnknownProduct_ReturnsProductNotFound()
    {
        _products.Setup(p => p.ReserveStock(1, 2, _transaction)).ReturnsAsync((ReservedProduct?)null);
        _products.Setup(p => p.Exists(1, _transaction)).ReturnsAsync(false);

        var result = await _service.CreateOrder(Order());

        result.Error.Should().Be(Messages.ProductNotFound);
        _orders.Verify(o => o.InsertOrder(It.IsAny<NewOrder>(), It.IsAny<IDbTransaction>()), Times.Never);
    }

    [Fact]
    public async Task NotEnoughStock_ReturnsInsufficientStock_AndDoesNotChargeOrStoreAnything()
    {
        _products.Setup(p => p.ReserveStock(1, 2, _transaction)).ReturnsAsync((ReservedProduct?)null);
        _products.Setup(p => p.Exists(1, _transaction)).ReturnsAsync(true);

        var result = await _service.CreateOrder(Order());

        result.Error.Should().Be(Messages.InsufficientStock);
        _processor.Verify(p => p.Charge(It.IsAny<decimal>(), It.IsAny<PaymentRequest>()), Times.Never);
        _orders.Verify(o => o.InsertOrder(It.IsAny<NewOrder>(), It.IsAny<IDbTransaction>()), Times.Never);
    }

    [Fact]
    public async Task DeclinedPayment_ReturnsPaymentFailed_SoTheUnitOfWorkRollsEverythingBack()
    {
        Stock(1, "Chair", 100, 60, 10);
        Discount("ABC123", 10, 150);
        _processor.Setup(p => p.Charge(It.IsAny<decimal>(), It.IsAny<PaymentRequest>())).Returns(new PaymentResult(false, Error: "declined"));

        var result = await _service.CreateOrder(Order("ABC123"));

        result.IsSuccess.Should().BeFalse();
        result.Error.Should().Be(Messages.PaymentFailed);
    }

    public static TheoryData<string, decimal, decimal, int, string> DiscountRejections => new()
    {
        // code state                            amount minimum  expires-in-days  expected error
        { "used",                                 10,    0,       30,              Messages.DiscountAlreadyUsed },
        { "expired",                              10,    0,       -1,              Messages.DiscountExpired },
        { "needs a bigger order (min 500)",       10,    500,     30,              Messages.MinimumOrderTotalNotMet },
        { "bigger than the order (999 > 200)",    999,   0,       30,              Messages.DiscountExceedsTotal }
    };

    [Theory]
    [MemberData(nameof(DiscountRejections))]
    public async Task UnusableDiscountCode_IsRejected_WithoutConsumingItOrChargingThePayment(
        string state, decimal amount, decimal minimum, int expiresInDays, string expectedError)
    {
        Stock(1, "Chair", 100, 60, 10);
        Discount("CODE", amount, minimum, TestHelpers.Now.UtcDateTime.AddDays(expiresInDays), used: state == "used");

        var result = await _service.CreateOrder(Order("CODE"));

        result.Error.Should().Be(expectedError);
        _discounts.Verify(d => d.MarkAsUsed(It.IsAny<long>(), It.IsAny<IDbTransaction>()), Times.Never);
        _processor.Verify(p => p.Charge(It.IsAny<decimal>(), It.IsAny<PaymentRequest>()), Times.Never);
    }

    [Fact]
    public async Task UnknownDiscountCode_ReturnsDiscountNotFound()
    {
        Stock(1, "Chair", 100, 60, 10);
        _discounts.Setup(d => d.GetByCodeForUpdate("NOPE", _transaction)).ReturnsAsync((DiscountCodeDto?)null);

        (await _service.CreateOrder(Order("NOPE"))).Error.Should().Be(Messages.DiscountNotFound);
    }

    [Fact]
    public async Task DiscountCodeAtExactlyTheMinimumTotal_IsAccepted()
    {
        Stock(1, "Chair", 100, 60, 10);
        Discount("EDGE", amount: 10, minimum: 200);

        (await _service.CreateOrder(Order("EDGE"))).IsSuccess.Should().BeTrue();
    }

    [Fact]
    public async Task DiscountCodeThatLostTheRaceForTheLastUse_IsRejected()
    {
        Stock(1, "Chair", 100, 60, 10);
        Discount("ABC123", 10, 0);
        _discounts.Setup(d => d.MarkAsUsed(5, _transaction)).ReturnsAsync(false);

        (await _service.CreateOrder(Order("ABC123"))).Error.Should().Be(Messages.DiscountAlreadyUsed);
    }

    // ---- lists

    [Fact]
    public async Task GetMyOrders_ReadsOnlyTheCallersOrders()
    {
        var paging = new BaseFilter { PageNumber = 2, PageSize = 5 };
        _orders.Setup(o => o.GetMyOrders(42, paging)).ReturnsAsync(([new CustomerOrderResponse { Id = 1 }], 6));

        var result = await _service.GetMyOrders(new ServiceRequest<BaseFilter>(paging, 42));

        result.Data.Should().ContainSingle();
        result.TotalCount.Should().Be(6);
    }

    [Fact]
    public async Task GetAllOrders_PassesTheFilterThrough()
    {
        var filter = new OrderFilter { CustomerId = 3, PaymentMethod = "XyzWallet" };
        _orders.Setup(o => o.GetAllOrders(filter)).ReturnsAsync(([new AdminOrderResponse { Id = 1, Profit = 40 }], 1));

        var result = await _service.GetAllOrders(new ServiceRequest<OrderFilter>(filter, 1));

        result.Data!.Single().Profit.Should().Be(40);
    }
}
