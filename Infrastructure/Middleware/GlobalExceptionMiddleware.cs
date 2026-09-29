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
        catch (BadHttpRequestException ex)
        {
            // Kestrel refused the request itself (body over the size limit, malformed framing): the client's mistake, not a server error.
            logger.LogWarning("Bad request for {Method} {Path}: {Reason}", context.Request.Method, context.Request.Path, ex.Message);
            await WriteClientErrorAsync(context, ex.StatusCode);
        }
        catch (System.Text.DecoderFallbackException ex)
        {
            // The body is not valid in the charset the client declared (for example charset=utf-16 with UTF-8 bytes).
            logger.LogWarning("Undecodable request body for {Method} {Path}: {Reason}", context.Request.Method, context.Request.Path, ex.Message);
            await WriteClientErrorAsync(context, StatusCodes.Status400BadRequest);
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

    private static async Task WriteClientErrorAsync(HttpContext context, int statusCode)
    {
        if (context.Response.HasStarted) return;

        var problem = new ProblemDetails
        {
            Status = statusCode,
            Title = statusCode == StatusCodes.Status413PayloadTooLarge ? "Request body too large" : "Bad request",
            Detail = "The request could not be processed."
        };
        problem.Extensions["traceId"] = context.TraceIdentifier;

        context.Response.Clear();
        context.Response.StatusCode = statusCode;
        await context.Response.WriteAsJsonAsync(problem, options: null, contentType: "application/problem+json; charset=utf-8");
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
