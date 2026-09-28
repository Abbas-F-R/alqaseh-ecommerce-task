using AlQaseh_Ecommerce_API.Shared.Helper;

namespace AlQaseh_Ecommerce_API.Shared.Extensions;

/// <summary>
/// Controllers with lowercase routes, Sqid-encoded ids and camelCase JSON.
/// </summary>
public static class ControllersExtension
{
    public static IServiceCollection AddControllersExtension(this IServiceCollection services)
    {
        services.AddRouting(options => options.LowercaseUrls = true);
        services.AddProblemDetails();

        services.AddControllers(options =>
            {
                // Decodes Sqid ids in route and query parameters marked with [Sqid].
                options.ModelBinderProviders.Insert(0, new SqidModelBinderProvider());
            })
            .AddJsonOptions(options =>
            {
                // Encodes / decodes ids in request and response bodies.
                options.JsonSerializerOptions.Converters.Add(new SqidJsonConverterFactory());
                options.JsonSerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.CamelCase;
            });

        return services;
    }
}
