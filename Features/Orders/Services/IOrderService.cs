using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Shared.Base.dto;

namespace AlQaseh_Ecommerce_API.Features.Orders.Services;

public interface IOrderService
{
    Task<ServiceResult<CustomerOrderResponse>> CreateOrder(ServiceRequest<CreateOrderRequest> request);

    /// <summary>The calling customer's own orders.</summary>
    Task<ServiceResult<List<CustomerOrderResponse>>> GetMyOrders(ServiceRequest<BaseFilter> request);

    /// <summary>All orders with profit (admin).</summary>
    Task<ServiceResult<List<AdminOrderResponse>>> GetAllOrders(ServiceRequest<OrderFilter> request);
}
