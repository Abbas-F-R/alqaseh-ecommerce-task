using AlQaseh_Ecommerce_API.Shared.Attributes;

namespace AlQaseh_Ecommerce_API.Features.Payments;

public interface IPaymentProcessorFactory
{
    /// <summary>The processor for a payment method (any letter case), or null when the method is not supported.</summary>
    IPaymentProcessor? GetProcessor(string method);
}

[Scoped]
public class PaymentProcessorFactory(IEnumerable<IPaymentProcessor> processors) : IPaymentProcessorFactory
{
    public IPaymentProcessor? GetProcessor(string method)
    {
        var normalized = PaymentMethods.Normalize(method);
        return processors.FirstOrDefault(p => p.Method == normalized);
    }
}
