using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using AlQaseh_Ecommerce_API.Infrastructure.Persistence;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using FluentValidation;
using FluentValidation.AspNetCore;

namespace AlQaseh_Ecommerce_API.Shared.Extensions;

/// <summary>
/// Registers persistence, FluentValidation, the [Scoped]/[Transient]/[Singleton] classes (Scrutor scan) and the service logging decorators.
/// </summary>
public static class ApplicationServicesExtension
{
    public static IServiceCollection AddApplicationServices(this IServiceCollection services)
    {
        services.AddHttpContextAccessor();
        services.AddSingleton(TimeProvider.System);
        services.AddSingleton<DapperContext>();

        var assembly = typeof(Program).Assembly;
        services.AddValidatorsFromAssembly(assembly);
        services.AddFluentValidationAutoValidation();

        services.Scan(scan => scan
            .FromAssemblies(assembly)
            .AddClasses(classes => classes.WithAttribute<ScopedAttribute>())
            .AsImplementedInterfaces()
            .AsSelf()
            .WithScopedLifetime()

            .AddClasses(classes => classes.WithAttribute<TransientAttribute>())
            .AsImplementedInterfaces()
            .AsSelf()
            .WithTransientLifetime()

            .AddClasses(classes => classes.WithAttribute<SingletonAttribute>())
            .AsImplementedInterfaces()
            .AsSelf()
            .WithSingletonLifetime());

        services.AddServiceLoggingDecorators();

        return services;
    }
}
