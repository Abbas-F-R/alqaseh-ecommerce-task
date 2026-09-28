using System.Text.Json;
using System.Text.Json.Serialization;
using Sqids;

namespace AlQaseh_Ecommerce_API.Shared.Helper;

/// <summary>
/// JSON converter factory for serializing/deserializing long and long? values with Sqids encryption.
/// </summary>
public class SqidJsonConverterFactory : JsonConverterFactory
{
    public override bool CanConvert(Type typeToConvert)
    {
        return typeToConvert == typeof(long) || typeToConvert == typeof(long?);
    }

    public override JsonConverter CreateConverter(Type typeToConvert, JsonSerializerOptions options)
    {
        if (typeToConvert == typeof(long))
        {
            return new SqidJsonConverter();
        }
        
        return new NullableSqidJsonConverter();
    }
}

public class SqidJsonConverter : JsonConverter<long>
{
    private static readonly SqidsEncoder<long> Encoder = new(new SqidsOptions { MinLength = 8 });

    public override long Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType == JsonTokenType.Number)
        {
            return reader.GetInt64();
        }
        
        var sqid = reader.GetString();
        if (string.IsNullOrEmpty(sqid)) return 0;
        
        var result = Encoder.Decode(sqid);
        return result.Count > 0 ? result[0] : 0;
    }

    public override void Write(Utf8JsonWriter writer, long value, JsonSerializerOptions options)
    {
        writer.WriteStringValue(Encoder.Encode(value));
    }
}

public class NullableSqidJsonConverter : JsonConverter<long?>
{
    private static readonly SqidsEncoder<long> Encoder = new(new SqidsOptions { MinLength = 8 });

    public override long? Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType == JsonTokenType.Null) return null;
        
        if (reader.TokenType == JsonTokenType.Number)
        {
            return reader.GetInt64();
        }
        
        var sqid = reader.GetString();
        if (string.IsNullOrEmpty(sqid)) return null;
        
        var result = Encoder.Decode(sqid);
        return result.Count > 0 ? result[0] : null;
    }

    public override void Write(Utf8JsonWriter writer, long? value, JsonSerializerOptions options)
    {
        if (value.HasValue)
        {
            writer.WriteStringValue(Encoder.Encode(value.Value));
        }
        else
        {
            writer.WriteNullValue();
        }
    }
}
