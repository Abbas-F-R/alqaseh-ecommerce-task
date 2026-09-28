using System.Diagnostics;
using System.Reflection;

namespace AlQaseh_Ecommerce_API.Infrastructure.Logging;

/// <summary>
/// Decorator pattern implementation using <see cref="DispatchProxy"/> to intercept service calls.
/// Automatically logs method invocation, execution latency, business failures, and exceptions,
/// eliminating repetitive logging code across all domain services.
/// </summary>
public class LoggingDecorator<T> : DispatchProxy where T : class
{
    private T _target = default!;
    private ILogger _logger = default!;

    /// <summary>
    /// Creates a decorated proxy instance wrapping the target service with automated logging.
    /// </summary>
    public static T Create(T target, ILogger logger)
    {
        var proxy = Create<T, LoggingDecorator<T>>() as LoggingDecorator<T>
            ?? throw new InvalidOperationException($"Unable to create logging proxy for {typeof(T).Name}");

        proxy._target = target;
        proxy._logger = logger;
        return (T)(object)proxy;
    }

    protected override object? Invoke(MethodInfo? targetMethod, object?[]? args)
    {
        if (targetMethod == null)
            return null;

        var serviceName = typeof(T).Name;
        var methodName = targetMethod.Name;
        var sw = Stopwatch.StartNew();

        _logger.LogInformation("[{Service}] Invoking {Method}", serviceName, methodName);

        try
        {
            var result = targetMethod.Invoke(_target, args);

            if (result is Task task)
            {
                return InterceptAsyncTask(task, targetMethod, serviceName, methodName, sw);
            }

            sw.Stop();
            LogResultOutcome(result, serviceName, methodName, sw.ElapsedMilliseconds);
            return result;
        }
        catch (TargetInvocationException ex)
        {
            sw.Stop();
            var inner = ex.InnerException ?? ex;
            LogException(inner, serviceName, methodName, sw.ElapsedMilliseconds);
            throw inner;
        }
        catch (Exception ex)
        {
            sw.Stop();
            LogException(ex, serviceName, methodName, sw.ElapsedMilliseconds);
            throw;
        }
    }

    private object InterceptAsyncTask(Task task, MethodInfo method, string serviceName, string methodName, Stopwatch sw)
    {
        var returnType = method.ReturnType;
        if (returnType.IsGenericType && returnType.GetGenericTypeDefinition() == typeof(Task<>))
        {
            var resultType = returnType.GetGenericArguments()[0];
            var helper = typeof(LoggingDecorator<T>)
                .GetMethod(nameof(InterceptGenericTask), BindingFlags.NonPublic | BindingFlags.Instance)!
                .MakeGenericMethod(resultType);

            return helper.Invoke(this, [task, serviceName, methodName, sw])!;
        }

        return InterceptVoidTask(task, serviceName, methodName, sw);
    }

    private async Task InterceptVoidTask(Task task, string serviceName, string methodName, Stopwatch sw)
    {
        try
        {
            await task.ConfigureAwait(false);
            sw.Stop();
            _logger.LogInformation("[{Service}] Completed {Method} in {ElapsedMs}ms", serviceName, methodName, sw.ElapsedMilliseconds);
        }
        catch (Exception ex)
        {
            sw.Stop();
            LogException(ex, serviceName, methodName, sw.ElapsedMilliseconds);
            throw;
        }
    }

    private async Task<TResult> InterceptGenericTask<TResult>(Task<TResult> task, string serviceName, string methodName, Stopwatch sw)
    {
        try
        {
            var result = await task.ConfigureAwait(false);
            sw.Stop();
            LogResultOutcome(result, serviceName, methodName, sw.ElapsedMilliseconds);
            return result;
        }
        catch (Exception ex)
        {
            sw.Stop();
            LogException(ex, serviceName, methodName, sw.ElapsedMilliseconds);
            throw;
        }
    }

    private void LogResultOutcome(object? result, string serviceName, string methodName, long elapsedMs)
    {
        _logger.LogInformation("[{Service}] Completed {Method} in {ElapsedMs}ms", serviceName, methodName, elapsedMs);

        if (result == null)
            return;

        var type = result.GetType();
        var isSuccessProp = type.GetProperty("IsSuccess");
        if (isSuccessProp != null && isSuccessProp.GetValue(result) is false)
        {
            var errorProp = type.GetProperty("Error");
            var error = errorProp?.GetValue(result);
            _logger.LogWarning("[{Service}] {Method} returned business failure: {Error}", serviceName, methodName, error);
        }
    }

    private void LogException(Exception ex, string serviceName, string methodName, long elapsedMs)
    {
        var errorType = ErrorClassifier.Classify(ex);
        _logger.LogTypedError(errorType, ex, "[{Service}] Exception in {Method} after {ElapsedMs}ms: {ErrorMessage}",
            serviceName, methodName, elapsedMs, ex.Message);
    }
}
