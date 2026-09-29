using AlQaseh_Ecommerce_API.Infrastructure.Middleware;
using Scalar.AspNetCore;

namespace AlQaseh_Ecommerce_API.Shared.Extensions;

/// <summary>
/// HTTP pipeline: exception handling, API documentation, routing, authentication, user context check, authorization, controllers.
/// </summary>
public static class PipelineExtension
{
    public static WebApplication UseApplicationPipeline(this WebApplication app)
    {
        // Unhandled exceptions become a safe 500 (details only in Development).
        app.UseMiddleware<GlobalExceptionMiddleware>();

        // Bare 401 / 403 / 404 responses get a problem-details body.
        app.UseStatusCodePages();

        if (app.Configuration.GetValue("Swagger:Enabled", true))
        {
            app.UseSwagger();
            app.UseSwaggerUI(options =>
            {
                options.SwaggerEndpoint($"/swagger/{SwaggerExtension.AllDocument}/swagger.json", "All Endpoints (v1)");
                options.SwaggerEndpoint($"/swagger/{SwaggerExtension.AdminDocument}/swagger.json", "Admin");
                options.SwaggerEndpoint($"/swagger/{SwaggerExtension.CustomerDocument}/swagger.json", "Customer");
                options.RoutePrefix = "swagger";
            });
        }

        app.UseRouting();
        app.UseAuthentication();
        app.UseMiddleware<UserContextMiddleware>();
        app.UseAuthorization();
        app.MapControllers();

        if (app.Configuration.GetValue("Swagger:Enabled", true))
        {
            app.MapScalarApiReference(options =>
            {
                options.WithTitle("Al Qaseh E-Commerce API")
                       .WithTheme(ScalarTheme.Moon)
                       .WithOpenApiRoutePattern("/swagger/{documentName}/swagger.json");
            }).AllowAnonymous();
        }

        return app;
    }
}
