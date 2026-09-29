using AlQaseh_Ecommerce_API.Shared.Base.dto;
using FluentValidation;

namespace AlQaseh_Ecommerce_API.Shared.Validation;

/// <summary>
/// Pagination rules shared by the offset-paged list endpoints: page number from 0, page size between 1 and 50.
/// </summary>
public class PagedFilterValidator<T> : AbstractValidator<T> where T : BaseFilter
{
    public PagedFilterValidator()
    {
        RuleFor(x => x.PageNumber)
            .GreaterThanOrEqualTo(0).WithMessage("Page number must be at least 0.");

        RuleFor(x => x.PageSize)
            .InclusiveBetween(1, BaseFilter.MaxPageSize).WithMessage($"Page size must be between 1 and {BaseFilter.MaxPageSize}.");
    }
}

/// <summary>Pagination rules for endpoints that take only page number and page size (the customer's own orders).</summary>
public class BaseFilterValidator : PagedFilterValidator<BaseFilter>;
