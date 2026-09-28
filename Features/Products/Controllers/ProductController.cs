using AlQaseh_Ecommerce_API.Features.Products.Dtos;
using AlQaseh_Ecommerce_API.Features.Products.Services;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Base;
using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace AlQaseh_Ecommerce_API.Features.Products.Controllers;

/// <summary>
/// Product catalog: admins create and update products, admins and customers list them.
/// </summary>
[Route("api/products")]
[ApiController]
[Authorize]
public class ProductController(IProductService service) : BaseController
{
    /// <summary>
    /// Creates a product (admin only). The creator and creation time are recorded.
    /// </summary>
    /// <response code="201">Created.</response>
    /// <response code="400">Invalid name, category, price, cost or quantity.</response>
    /// <response code="401">Missing or invalid token.</response>
    /// <response code="403">The caller is not an admin.</response>
    /// <response code="409">A product with this name already exists.</response>
    [HttpPost]
    [Authorize(Roles = Roles.Admin)]
    [ProducesResponseType(typeof(AdminProductResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<AdminProductResponse>> CreateProduct([FromBody] ProductForm form) =>
        Respond(await service.Add(CreateServiceRequest(form)), StatusCodes.Status201Created);

    /// <summary>
    /// Replaces all fields of a product (admin only). The updater and update time are recorded.
    /// </summary>
    /// <param name="id">Product id.</param>
    /// <param name="form">The new values of every field.</param>
    /// <response code="200">Updated.</response>
    /// <response code="400">Invalid id, name, category, price, cost or quantity.</response>
    /// <response code="401">Missing or invalid token.</response>
    /// <response code="403">The caller is not an admin.</response>
    /// <response code="404">No product with this id.</response>
    /// <response code="409">Another product already has this name.</response>
    [HttpPut("{id}")]
    [Authorize(Roles = Roles.Admin)]
    [ProducesResponseType(typeof(AdminProductResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status409Conflict)]
    public async Task<ActionResult<AdminProductResponse>> UpdateProduct([Sqid] long id, [FromBody] ProductForm form) =>
        Respond(await service.Update(id, CreateServiceRequest(form)));

    /// <summary>
    /// Lists products, filtered by name (contains) and category, one page at a time (admins and customers).
    /// </summary>
    /// <remarks>
    /// Admins receive <c>cost</c> and the exact <c>availableQuantity</c> (<see cref="AdminProductResponse"/>).
    /// Customers receive <c>stockStatus</c> instead (<see cref="CustomerProductResponse"/>): low (0-4), limited (5-9), available (10+).
    /// </remarks>
    /// <response code="200">A page of products (empty when nothing matches).</response>
    /// <response code="400">Invalid category, page number or page size (1-50).</response>
    /// <response code="401">Missing or invalid token.</response>
    [HttpGet]
    [Authorize(Roles = Roles.AdminOrCustomer)]
    [ProducesResponseType(typeof(Response<AdminProductResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> ListProducts([FromQuery] ProductFilter filter)
    {
        if (Role == Roles.Admin)
            return RespondPaged(await service.GetAll(CreateServiceRequest(filter)), filter);

        return RespondPaged(await service.GetAllForCustomer(CreateServiceRequest(filter)), filter);
    }
}
