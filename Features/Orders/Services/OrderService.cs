using AlQaseh_Ecommerce_API.Features.Discounts;
using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Orders.Repositories;
using AlQaseh_Ecommerce_API.Features.Payments;
using AlQaseh_Ecommerce_API.Features.Products.Repositories;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;

namespace AlQaseh_Ecommerce_API.Features.Orders.Services;

/// <summary>
/// Places orders and lists them.
/// </summary>
[Scoped]
public class OrderService(
    IUnitOfWork unitOfWork,
    IOrderRepository orderRepository,
    IProductRepository productRepository,
    IDiscountCodeRepository discountCodeRepository,
    IPaymentProcessorFactory paymentProcessorFactory,
    TimeProvider clock) : IOrderService
{
    /// <summary>
    /// Everything runs in one database transaction, in this order:
    /// take the stock (which also fixes the price and cost of every line), check and redeem the discount code, store the order,
    /// and only then charge the payment. Nothing is committed before the charge succeeds, so a declined payment, insufficient stock,
    /// an invalid code or any error leaves stock, the code and the orders exactly as they were.
    /// </summary>
    public async Task<ServiceResult<CustomerOrderResponse>> CreateOrder(ServiceRequest<CreateOrderRequest> request)
    {
        var dto = request.Dto;

        var paymentMethod = PaymentMethods.Normalize(dto.Payment.Method);
        var processor = paymentMethod is null ? null : paymentProcessorFactory.GetProcessor(paymentMethod);
        if (paymentMethod is null || processor is null)
            return ServiceResult<CustomerOrderResponse>.Failure(Messages.PaymentMethodNotSupported);

        // The same product listed twice becomes one line. Lines are processed in id order so that concurrent orders take stock in the same order.
        var lines = dto.Items
            .GroupBy(i => i.ProductId)
            .OrderBy(g => g.Key)
            .Select(g => (ProductId: g.Key, Quantity: g.Sum(i => i.Quantity)))
            .ToList();

        return await unitOfWork.ExecuteAsync(request.UserId, async transaction =>
        {
            // 1. Stock: atomic "take if enough"; the product's current price and cost come back with it.
            var items = new List<NewOrderItem>();
            foreach (var (productId, quantity) in lines)
            {
                var product = await productRepository.ReserveStock(productId, quantity, transaction);
                if (product is null)
                {
                    var exists = await productRepository.Exists(productId, transaction);
                    return ServiceResult<CustomerOrderResponse>.Failure(exists ? Messages.InsufficientStock : Messages.ProductNotFound);
                }

                items.Add(new NewOrderItem(product.Id, product.Name, product.Price, product.Cost, quantity));
            }

            var subtotal = items.Sum(i => i.UnitPrice * i.Quantity);
            var totalCost = items.Sum(i => i.UnitCost * i.Quantity);

            // 2. Discount code: one fixed amount, not expired, not used, subtotal at least the code's minimum.
            DiscountCodeDto? discount = null;
            if (!string.IsNullOrWhiteSpace(dto.DiscountCode))
            {
                discount = await discountCodeRepository.GetByCodeForUpdate(dto.DiscountCode.Trim(), transaction);

                var discountError = ValidateDiscount(discount, subtotal);
                if (discountError is not null)
                    return ServiceResult<CustomerOrderResponse>.Failure(discountError);

                if (!await discountCodeRepository.MarkAsUsed(discount!.Id, transaction))
                    return ServiceResult<CustomerOrderResponse>.Failure(Messages.DiscountAlreadyUsed);
            }

            var discountAmount = discount?.Amount ?? 0m;
            var total = subtotal - discountAmount;

            // 3. Store the order and its lines (name and price are snapshots). The audit triggers record every change in the same transaction.
            var order = await orderRepository.InsertOrder(
                new NewOrder(request.UserId, subtotal, discountAmount, total, totalCost, discount?.Id, paymentMethod),
                transaction);
            await orderRepository.InsertItems(order.Id, items, transaction);

            // 4. Charge last. Nothing above is committed yet, so a decline simply rolls the whole order back.
            var payment = processor.Charge(total, dto.Payment);
            if (!payment.Success)
                return ServiceResult<CustomerOrderResponse>.Failure(Messages.PaymentFailed);

            return ServiceResult<CustomerOrderResponse>.Ok(new CustomerOrderResponse
            {
                Id = order.Id,
                TotalPrice = total,
                PaymentMethod = paymentMethod,
                PurchaseDate = order.CreatedAt,
                DiscountAmount = discountAmount,
                Items = items.Select(i => new OrderItemResponse
                {
                    ProductId = i.ProductId,
                    ProductName = i.ProductName,
                    UnitPrice = i.UnitPrice,
                    Quantity = i.Quantity,
                    Subtotal = i.UnitPrice * i.Quantity
                }).ToList()
            });
        });
    }

    public async Task<ServiceResult<List<CustomerOrderResponse>>> GetMyOrders(ServiceRequest<BaseFilter> request)
    {
        var (data, totalCount) = await orderRepository.GetMyOrders(request.UserId, request.Dto);
        return ServiceResult<List<CustomerOrderResponse>>.PagedOk(data, totalCount);
    }

    public async Task<ServiceResult<List<AdminOrderResponse>>> GetAllOrders(ServiceRequest<OrderFilter> request)
    {
        var (data, totalCount) = await orderRepository.GetAllOrders(request.Dto);
        return ServiceResult<List<AdminOrderResponse>>.PagedOk(data, totalCount);
    }

    /// <summary>The error code for a code that cannot be used on this order, or null when it can.</summary>
    private string? ValidateDiscount(DiscountCodeDto? discount, decimal subtotal)
    {
        if (discount is null)
            return Messages.DiscountNotFound;
        if (discount.Used)
            return Messages.DiscountAlreadyUsed;
        if (clock.GetUtcNow().UtcDateTime > discount.ExpiresAt)
            return Messages.DiscountExpired;
        if (subtotal < discount.MinimumOrderTotal)
            return Messages.MinimumOrderTotalNotMet;
        if (discount.Amount > subtotal)
            return Messages.DiscountExceedsTotal;

        return null;
    }
}
