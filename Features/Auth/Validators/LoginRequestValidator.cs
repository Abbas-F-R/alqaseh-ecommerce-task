using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using FluentValidation;

namespace AlQaseh_Ecommerce_API.Features.Auth.Validators;

public class LoginRequestValidator : AbstractValidator<LoginRequest>
{
    public LoginRequestValidator()
    {
        RuleFor(x => x.UserName)
            .NotEmpty().WithMessage("Username is required.")
            .MaximumLength(50).WithMessage("Username cannot exceed 50 characters.")
            // Letters, digits and . _ @ - only: no control character can reach the log or the database.
            // \z, not $: in .NET "$" also matches before a trailing newline, which would let "admin\n" through.
            .Matches(@"^[A-Za-z0-9._@-]+\z").WithMessage("Username may contain only letters, digits and . _ @ -.");

        RuleFor(x => x.Password)
            .NotEmpty().WithMessage("Password is required.")
            .MaximumLength(128).WithMessage("Password cannot exceed 128 characters.");
    }
}
