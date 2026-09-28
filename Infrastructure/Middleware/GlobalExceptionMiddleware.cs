using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using Microsoft.AspNetCore.Mvc;
using Serilog.Context;

namespace AlQaseh_Ecommerce_API.Infrastructure.Middleware;

/// <summary>
/// Turns any unhandled exception into a 500 problem-details response and logs it with its error type.
/// The exception type, message and stack trace are only included in Development; other environments get a generic message.
/// </summary>
public class GlobalExceptionMiddleware(
    RequestDelegate next,
    ILogger<GlobalExceptionMiddleware> logger,
    IHostEnvironment environment)
{
    public const string ErrorCode = "InternalServerError";

    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await next(context);
        }
        catch (Exception ex)
        {
            var errorType = ErrorClassifier.Classify(ex);

            using (LogContext.PushProperty("TraceId", context.TraceIdentifier))
            {
                logger.LogTypedError(errorType, ex, "Unhandled exception occurred during request execution for {Method} {Path}",
                    context.Request.Method, context.Request.Path);
            }

            await WriteProblemAsync(context, ex, errorType);
        }
    }

    private async Task WriteProblemAsync(HttpContext context, Exception exception, ErrorType errorType)
    {
        if (context.Response.HasStarted)
        {
            logger.LogWarning("The response has already started; the global exception middleware will not write a response.");
            return;
        }

        var problem = new ProblemDetails
        {
            Status = StatusCodes.Status500InternalServerError,
            Title = "Internal Server Error",
            Detail = environment.IsDevelopment()
                ? exception.Message
                : "An unexpected error occurred while processing your request."
        };
        problem.Extensions["code"] = ErrorCode;
        problem.Extensions["traceId"] = context.TraceIdentifier;

        if (environment.IsDevelopment())
        {
            problem.Extensions["errorType"] = errorType.ToString();
            problem.Extensions["exceptionType"] = exception.GetType().FullName;
            problem.Extensions["stackTrace"] = exception.StackTrace;
        }

        context.Response.Clear();
        context.Response.StatusCode = StatusCodes.Status500InternalServerError;
        context.Response.ContentType = "application/problem+json; charset=utf-8";
        await context.Response.WriteAsJsonAsync(problem, options: null, contentType: "application/problem+json; charset=utf-8");
    }
}
