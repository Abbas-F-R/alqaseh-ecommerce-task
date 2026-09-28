using System.Reflection;
using AlQaseh_Ecommerce_API.Shared.Attributes;
using AlQaseh_Ecommerce_API.Shared.Constants;
using Microsoft.AspNetCore.Authorization;
using Microsoft.OpenApi;
using Swashbuckle.AspNetCore.SwaggerGen;

namespace AlQaseh_Ecommerce_API.Shared.Extensions;

/// <summary>
/// OpenAPI / Swagger UI: the endpoints, their responses (from the controllers' XML comments and ProducesResponseType attributes)
/// and the bearer-token "Authorize" button.
/// </summary>
public static class SwaggerExtension
{
    public const string AdminDocument = "1-admin";
    public const string CustomerDocument = "2-customer";

    public static IServiceCollection AddSwaggerDocumentation(this IServiceCollection services)
    {
        services.AddSwaggerGen(options =>
        {
            // One document per role, so that each user sees only the endpoints they may call. Login is in both.
            options.SwaggerDoc(AdminDocument, new OpenApiInfo
            {
                Title = "Al Qaseh E-Commerce API - Admin",
                Version = "v1",
                Description = "Endpoints for the Admin role. Sign in with POST /api/auth/login, then click Authorize and paste the token."
            });
            options.SwaggerDoc(CustomerDocument, new OpenApiInfo
            {
                Title = "Al Qaseh E-Commerce API - Customer",
                Version = "v1",
                Description = "Endpoints for the Customer role. Sign in with POST /api/auth/login, then click Authorize and paste the token."
            });

            // An endpoint belongs to the document of every role it allows; an endpoint without roles (login) belongs to both.
            options.DocInclusionPredicate((document, api) =>
            {
                var roles = api.ActionDescriptor.EndpointMetadata.OfType<IAuthorizeData>()
                    .Select(a => a.Roles).Where(r => !string.IsNullOrWhiteSpace(r))
                    .SelectMany(r => r!.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
                    .ToList();

                return roles.Count == 0 || roles.Contains(document == AdminDocument ? Roles.Admin : Roles.Customer);
            });

            options.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
            {
                Name = "Authorization",
                Type = SecuritySchemeType.Http,
                Scheme = "bearer",
                BearerFormat = "JWT",
                In = ParameterLocation.Header,
                Description = "The token returned by POST /api/auth/login."
            });

            var xmlFile = Path.Combine(AppContext.BaseDirectory, $"{typeof(Program).Assembly.GetName().Name}.xml");
            if (File.Exists(xmlFile))
                options.IncludeXmlComments(xmlFile, includeControllerXmlComments: true);

            options.OperationFilter<AuthorizeOperationFilter>();
            options.SchemaFilter<SqidOpenApiFilter>();
            options.ParameterFilter<SqidOpenApiFilter>();
        });

        return services;
    }
}

/// <summary>
/// Adds the bearer requirement to every endpoint that needs a signed-in user (everything except [AllowAnonymous]).
/// </summary>
public class AuthorizeOperationFilter : IOperationFilter
{
    public void Apply(OpenApiOperation operation, OperationFilterContext context)
    {
        var attributes = context.MethodInfo.GetCustomAttributes(true)
            .Concat(context.MethodInfo.DeclaringType?.GetCustomAttributes(true) ?? []);

        if (attributes.OfType<AllowAnonymousAttribute>().Any() || !attributes.OfType<AuthorizeAttribute>().Any())
            return;

        operation.Security ??= [];
        operation.Security.Add(new OpenApiSecurityRequirement
        {
            [new OpenApiSecuritySchemeReference("Bearer")] = []
        });
    }
}

/// <summary>
/// Ids marked with [Sqid] travel as opaque strings (for example "b9X7mK2p"), not as numbers.
/// </summary>
public class SqidOpenApiFilter : ISchemaFilter, IParameterFilter
{
    public void Apply(IOpenApiSchema schema, SchemaFilterContext context)
    {
        if (context.MemberInfo?.GetCustomAttribute<SqidAttribute>() is not null && schema is OpenApiSchema openApiSchema)
            MarkAsSqid(openApiSchema);
    }

    public void Apply(IOpenApiParameter parameter, ParameterFilterContext context)
    {
        var isSqid = context.ParameterInfo?.GetCustomAttribute<SqidAttribute>() is not null
                     || context.PropertyInfo?.GetCustomAttribute<SqidAttribute>() is not null;

        if (isSqid && parameter is OpenApiParameter { Schema: OpenApiSchema schema })
            MarkAsSqid(schema);
    }

    private static void MarkAsSqid(OpenApiSchema schema)
    {
        schema.Type = JsonSchemaType.String;
        schema.Format = null;
        schema.Description = "Opaque id as returned by the API, for example \"b9X7mK2p\".";
    }
}
