using AlQaseh_Ecommerce_API.Shared.Base.dto;
using AlQaseh_Ecommerce_API.Shared.Constants;
using AlQaseh_Ecommerce_API.Shared.Utils;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.WebUtilities;

namespace AlQaseh_Ecommerce_API.Shared.Base;

/// <summary>
/// Base controller: builds service requests from the caller's identity and turns service results into HTTP responses.
/// Failures are RFC 7807 problem details with a stable <c>code</c> and a message localized by Accept-Language.
/// </summary>
[ApiController]
public abstract class BaseController : ControllerBase
{
    private ICurrentUser? _currentUser;

    [NonAction]
    public void SetCurrentUser(ICurrentUser currentUser) => _currentUser = currentUser;

    protected ICurrentUser CurrentUser => _currentUser ??= HttpContext.RequestServices.GetRequiredService<ICurrentUser>();

    protected long Id => CurrentUser.UserId;
    protected string UserName => CurrentUser.UserName;
    protected string Role => CurrentUser.Role;
    protected string Lang => CurrentUser.Lang;

    protected ServiceRequest<T> CreateServiceRequest<T>(T dto) => new(dto, Id, UserName, Role, Lang);

    /// <summary>The data with <paramref name="successStatus"/> (200 by default), or the problem details of the error.</summary>
    protected ObjectResult Respond<T>(ServiceResult<T> result, int successStatus = StatusCodes.Status200OK)
    {
        if (!result.IsSuccess)
            return Fail(result.Error!);

        return successStatus == StatusCodes.Status200OK ? base.Ok(result.Data) : StatusCode(successStatus, result.Data);
    }

    /// <summary>A page of data wrapped in the standard pagination envelope, or the problem details of the error.</summary>
    protected ObjectResult RespondPaged<T>(ServiceResult<List<T>> result, BaseFilter filter)
    {
        if (!result.IsSuccess)
            return Fail(result.Error!);

        return base.Ok(new Response<T>(result.Data, filter.PageNumber, result.TotalCount, filter.PageSize));
    }

    private ObjectResult Fail(string error)
    {
        var status = Messages.StatusCodeOf(error);
        var problem = new ProblemDetails
        {
            Status = status,
            Title = ReasonPhrases.GetReasonPhrase(status),
            Detail = error.GetMessage(Lang)
        };
        problem.Extensions["code"] = error;

        return new ObjectResult(problem)
        {
            StatusCode = status,
            ContentTypes = { "application/problem+json" }
        };
    }
}
