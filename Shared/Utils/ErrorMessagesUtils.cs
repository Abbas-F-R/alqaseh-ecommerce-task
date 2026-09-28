using AlQaseh_Ecommerce_API.Shared.Constants;

namespace AlQaseh_Ecommerce_API.Shared.Utils;

/// <summary>
/// Localized (Arabic / English) message for every error code in <see cref="Messages"/>.
/// The language comes from the <c>Accept-Language</c> header; English is the default.
/// </summary>
public static class ErrorMessagesUtils
{
    private static readonly Dictionary<string, (string Ar, string En)> MessagesDict = new()
    {
        [Messages.InvalidCredentials] = ("اسم المستخدم أو كلمة المرور غير صحيحة.", "Invalid username or password."),
        [Messages.ProductNotFound] = ("المنتج المطلوب غير موجود.", "Product not found."),
        [Messages.ProductNameAlreadyExists] = ("اسم المنتج موجود بالفعل.", "A product with this name already exists."),
        [Messages.InsufficientStock] = ("الكمية المطلوبة غير متوفرة في المخزون.", "Insufficient stock for requested item."),
        [Messages.DiscountNotFound] = ("كود الخصم غير موجود.", "Discount code not found."),
        [Messages.DiscountExpired] = ("كود الخصم منتهي الصلاحية.", "Discount code is expired."),
        [Messages.DiscountAlreadyUsed] = ("كود الخصم مستخدم مسبقاً.", "Discount code has already been used."),
        [Messages.MinimumOrderTotalNotMet] = ("قيمة الطلب أقل من الحد الأدنى لتطبيق كود الخصم.", "Order total does not meet the minimum requirement for this discount."),
        [Messages.DiscountExceedsTotal] = ("قيمة الخصم تتجاوز إجمالي الطلب.", "Discount amount cannot exceed the order total."),
        [Messages.PaymentFailed] = ("فشلت عملية الدفع.", "Payment declined or failed."),
        [Messages.PaymentMethodNotSupported] = ("طريقة الدفع غير مدعومة.", "Payment method is not supported.")
    };

    /// <summary>Translates an error code into the requested language ("ar" or "en"); unknown codes are returned as-is.</summary>
    public static string GetMessage(this string key, string? lang = "en")
    {
        if (!MessagesDict.TryGetValue(key, out var translations))
            return key;

        return string.Equals(lang?.Trim(), "ar", StringComparison.OrdinalIgnoreCase) ? translations.Ar : translations.En;
    }
}
