namespace AlQaseh_Ecommerce_API.Infrastructure.Logging;

/// <summary>
/// Dependency injection extensions for registering Decorator Pattern logging wrappers.
/// </summary>
public static class LoggingDecoratorExtensions
{
    /// <summary>
    /// Decorates an existing service registration with automated structured logging using the Decorator Pattern.
    /// Eliminates manual logging boilerplate in domain services.
    /// </summary>
    public static IServiceCollection DecorateWithLogging<TInterface>(this IServiceCollection services)
        where TInterface : class
    {
        return services.Decorate<TInterface>((target, provider) =>
        {
            var logger = provider.GetRequiredService<ILogger<TInterface>>();
            return LoggingDecorator<TInterface>.Create(target, logger);
        });
    }

    /// <summary>
    /// Applies automated logging decorators to all core domain service contracts.
    /// </summary>
    public static IServiceCollection AddServiceLoggingDecorators(this IServiceCollection services)
    {
        services.DecorateWithLogging<Features.Auth.Services.IAuthService>();
        services.DecorateWithLogging<Features.Products.Services.IProductService>();
        return services;
    }
}
