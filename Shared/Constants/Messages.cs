using System.Net;

namespace AlQaseh_Ecommerce_API.Shared.Constants;

/// <summary>
/// Stable error codes returned by services (<c>ServiceResult.Error</c>).
/// Each code has a localized message (<c>ErrorMessagesUtils</c>) and one HTTP status (<see cref="StatusCodeOf"/>).
/// </summary>
public static class Messages
{
    public const string InvalidCredentials = "InvalidCredentials";
    public const string ProductNotFound = "ProductNotFound";
    public const string ProductNameAlreadyExists = "ProductNameAlreadyExists";
    public const string InsufficientStock = "InsufficientStock";
    public const string DiscountNotFound = "DiscountNotFound";
    public const string DiscountExpired = "DiscountExpired";
    public const string DiscountAlreadyUsed = "DiscountAlreadyUsed";
    public const string MinimumOrderTotalNotMet = "MinimumOrderTotalNotMet";
    public const string DiscountExceedsTotal = "DiscountExceedsTotal";
    public const string PaymentFailed = "PaymentFailed";
    public const string PaymentMethodNotSupported = "PaymentMethodNotSupported";

    private static readonly Dictionary<string, HttpStatusCode> StatusCodes = new()
    {
        [InvalidCredentials] = HttpStatusCode.Unauthorized,
        [ProductNotFound] = HttpStatusCode.NotFound,
        [DiscountNotFound] = HttpStatusCode.NotFound,
        [ProductNameAlreadyExists] = HttpStatusCode.Conflict,
        [InsufficientStock] = HttpStatusCode.Conflict,
        [DiscountAlreadyUsed] = HttpStatusCode.Conflict,
        [DiscountExpired] = HttpStatusCode.UnprocessableEntity,
        [MinimumOrderTotalNotMet] = HttpStatusCode.UnprocessableEntity,
        [DiscountExceedsTotal] = HttpStatusCode.UnprocessableEntity,
        [PaymentFailed] = HttpStatusCode.PaymentRequired,
        [PaymentMethodNotSupported] = HttpStatusCode.BadRequest
    };

    /// <summary>HTTP status of an error code; unknown codes are 400.</summary>
    public static int StatusCodeOf(string error) =>
        (int)(StatusCodes.TryGetValue(error, out var status) ? status : HttpStatusCode.BadRequest);
}
