using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Orders.Services;
using AlQaseh_Ecommerce_API.Shared.Base;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace AlQaseh_Ecommerce_API.Features.Orders.Controllers;

/// <summary>
/// Orders: customers place orders and list their own, admins list all orders with profit.
/// </summary>
[Route("api/orders")]
[ApiController]
[Authorize]
public class OrderController(IOrderService orderService) : BaseController
{
    /// <summary>
    /// Places an order (customer only): stock is reduced, the optional discount code is redeemed and the payment is charged, all or nothing.
    /// </summary>
    /// <response code="201">The order was placed.</response>
    /// <response code="400">Invalid request: no items, bad quantity, unknown payment method or missing payment fields.</response>
    /// <response code="401">Missing or invalid token.</response>
    /// <response code="402">The payment was declined; nothing was changed.</response>
    /// <response code="403">The caller is not a customer.</response>
    /// <response code="404">A product or the discount code does not exist.</response>
    /// <response code="409">Not enough stock for a product, or the discount code was already used.</response>
    /// <response code="422">The discount code is expired, needs a higher order total, or is larger than the order total.</response>
    [HttpPost]
    [Authorize(Roles = Roles.Customer)]
    [ProducesResponseType(typeof(CustomerOrderResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status402PaymentRequired)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status409Conflict)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status422UnprocessableEntity)]
    public async Task<ActionResult<CustomerOrderResponse>> CreateOrder([FromBody] CreateOrderRequest request) =>
        Respond(await orderService.CreateOrder(CreateServiceRequest(request)), StatusCodes.Status201Created);

    /// <summary>
    /// Lists the caller's own orders, newest first (customer only): total price, payment method, purchase date, discount amount and items.
    /// </summary>
    /// <response code="200">A page of the caller's orders (empty when there are none).</response>
    /// <response code="400">Invalid page number or page size (1-50).</response>
    /// <response code="401">Missing or invalid token.</response>
    /// <response code="403">The caller is not a customer.</response>
    [HttpGet("my")]
    [Authorize(Roles = Roles.Customer)]
    [ProducesResponseType(typeof(Response<CustomerOrderResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<Response<CustomerOrderResponse>>> ListMyOrders([FromQuery] BaseFilter paging) =>
        RespondPaged(await orderService.GetMyOrders(CreateServiceRequest(paging)), paging);

    /// <summary>
    /// Lists all orders, newest first, each with its profit (admin only). Filter by customer and by payment method.
    /// </summary>
    /// <response code="200">A page of orders (empty when nothing matches).</response>
    /// <response code="400">Invalid payment method, customer id, page number or page size (1-50).</response>
    /// <response code="401">Missing or invalid token.</response>
    /// <response code="403">The caller is not an admin.</response>
    [HttpGet]
    [Authorize(Roles = Roles.Admin)]
    [ProducesResponseType(typeof(Response<AdminOrderResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<Response<AdminOrderResponse>>> ListAllOrders([FromQuery] OrderFilter filter) =>
        RespondPaged(await orderService.GetAllOrders(CreateServiceRequest(filter)), filter);
}
