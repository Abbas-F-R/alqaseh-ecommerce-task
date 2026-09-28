using AlQaseh_Ecommerce_API.Features.Auth.Dtos;
using FluentValidation;

namespace AlQaseh_Ecommerce_API.Features.Auth.Validators;

public class LoginRequestValidator : AbstractValidator<LoginRequest>
{
    public LoginRequestValidator()
    {
        RuleFor(x => x.UserName)
            .NotEmpty().WithMessage("Username is required.")
            .MaximumLength(50).WithMessage("Username cannot exceed 50 characters.");

        RuleFor(x => x.Password)
            .NotEmpty().WithMessage("Password is required.")
            .MaximumLength(200).WithMessage("Password cannot exceed 200 characters.");
    }
}
