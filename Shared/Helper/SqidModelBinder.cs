using AlQaseh_Ecommerce_API.Shared.Attributes;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using Microsoft.AspNetCore.Mvc.ModelBinding.Metadata;
using Sqids;

namespace AlQaseh_Ecommerce_API.Shared.Helper;

/// <summary>
/// Model binder for decoding Sqid strings from route values and query parameters into numeric IDs.
/// </summary>
public class SqidModelBinder : IModelBinder
{
    private static readonly SqidsEncoder<long> Encoder = new(new SqidsOptions { MinLength = 8 });

    public Task BindModelAsync(ModelBindingContext bindingContext)
    {
        var valueProviderResult = bindingContext.ValueProvider.GetValue(bindingContext.ModelName);

        if (valueProviderResult == ValueProviderResult.None)
        {
            return Task.CompletedTask;
        }

        bindingContext.ModelState.SetModelValue(bindingContext.ModelName, valueProviderResult);
        var value = valueProviderResult.FirstValue;

        if (string.IsNullOrEmpty(value))
        {
            return Task.CompletedTask;
        }

        if (long.TryParse(value, out var longValue))
        {
            bindingContext.Result = ModelBindingResult.Success(longValue);
            return Task.CompletedTask;
        }

        try
        {
            var result = Encoder.Decode(value);
            if (result.Count > 0)
            {
                bindingContext.Result = ModelBindingResult.Success(result[0]);
            }
            else
            {
                bindingContext.ModelState.TryAddModelError(bindingContext.ModelName, "Invalid Sqid format.");
            }
        }
        catch
        {
            bindingContext.ModelState.TryAddModelError(bindingContext.ModelName, "Failed to decode Sqid.");
        }

        return Task.CompletedTask;
    }
}

public class SqidModelBinderProvider : IModelBinderProvider
{
    public IModelBinder? GetBinder(ModelBinderProviderContext context)
    {
        if (context == null) throw new ArgumentNullException(nameof(context));

        var isLong = context.Metadata.ModelType == typeof(long) || context.Metadata.ModelType == typeof(long?);
        if (!isLong) return null;

        // Only properties and action parameters marked with [Sqid] hold Sqid-encoded ids.
        var hasSqidAttr = context.Metadata is DefaultModelMetadata defaultMetadata
                          && defaultMetadata.Attributes.Attributes.OfType<SqidAttribute>().Any();

        return hasSqidAttr ? new SqidModelBinder() : null;
    }
}
