using AlQaseh_Ecommerce_API.Features.Orders.Dtos;
using AlQaseh_Ecommerce_API.Features.Orders.Validators;
using FluentAssertions;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Features.Orders.Validators;

public class OrderLineValidatorTests
{
    [Fact]
    public void ANullLine_IsInvalid_NotAnException()
    {
        var request = new CreateOrderRequest
        {
            Items = [null!],
            Payment = new PaymentRequest { Method = "CreditCard", CardNumber = "4111111111111111" }
        };

        var validate = () => new CreateOrderRequestValidator().Validate(request);

        validate.Should().NotThrow();
        validate().IsValid.Should().BeFalse();
    }
}
