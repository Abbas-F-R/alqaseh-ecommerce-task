using AlQaseh_Ecommerce_API.Infrastructure.Logging;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Xunit;

namespace AlQaseh_Ecommerce_API.Tests.Infrastructure.Logging;

public class ErrorClassifierTests
{
    [Fact]
    public void Classify_WithNullArguments_ReturnsUnhandled()
    {
        var result = ErrorClassifier.Classify(null);
        result.Should().Be(ErrorType.Unhandled);
    }

    [Theory]
    [InlineData(StatusCodes.Status401Unauthorized, ErrorType.Security)]
    [InlineData(StatusCodes.Status403Forbidden, ErrorType.Security)]
    [InlineData(StatusCodes.Status400BadRequest, ErrorType.Validation)]
    [InlineData(StatusCodes.Status422UnprocessableEntity, ErrorType.Validation)]
    [InlineData(StatusCodes.Status404NotFound, ErrorType.NotFound)]
    public void Classify_WithStatusCode_ReturnsExpectedErrorType(int statusCode, ErrorType expected)
    {
        var result = ErrorClassifier.Classify(null, statusCode);
        result.Should().Be(expected);
    }

    [Fact]
    public void Classify_WithDatabaseExceptionMessage_ReturnsDatabase()
    {
        var ex = new Exception("A network-related error occurred: SQL Server was not found");
        var result = ErrorClassifier.Classify(ex);
        result.Should().Be(ErrorType.Database);
    }

    [Fact]
    public void Classify_WithInnerDatabaseException_ReturnsDatabase()
    {
        var inner = new Exception("Could not open a connection to SQL Server (Named Pipes Provider)");
        var outer = new InvalidOperationException("Repository execution failed", inner);

        var result = ErrorClassifier.Classify(outer);
        result.Should().Be(ErrorType.Database);
    }

    [Fact]
    public void Classify_WithSecurityTokenException_ReturnsSecurity()
    {
        var ex = new UnauthorizedAccessException("Attempted unauthorized access to secure resource");
        var result = ErrorClassifier.Classify(ex);
        result.Should().Be(ErrorType.Security);
    }

    [Fact]
    public void Classify_WithJwtMessageInException_ReturnsSecurity()
    {
        var ex = new Exception("Invalid JWT token signature");
        var result = ErrorClassifier.Classify(ex);
        result.Should().Be(ErrorType.Security);
    }

    [Fact]
    public void Classify_WithArgumentException_ReturnsValidation()
    {
        var ex = new ArgumentException("Provided parameter cannot be null or empty");
        var result = ErrorClassifier.Classify(ex);
        result.Should().Be(ErrorType.Validation);
    }

    [Fact]
    public void Classify_WithFormatException_ReturnsValidation()
    {
        var ex = new FormatException("Input string was not in a correct format");
        var result = ErrorClassifier.Classify(ex);
        result.Should().Be(ErrorType.Validation);
    }

    [Fact]
    public void Classify_WithKeyNotFoundException_ReturnsNotFound()
    {
        var ex = new KeyNotFoundException("The requested entity ID was not found in the repository");
        var result = ErrorClassifier.Classify(ex);
        result.Should().Be(ErrorType.NotFound);
    }

    [Fact]
    public void Classify_WithGenericException_ReturnsUnhandled()
    {
        var ex = new NullReferenceException("Object reference not set to an instance of an object");
        var result = ErrorClassifier.Classify(ex);
        result.Should().Be(ErrorType.Unhandled);
    }

    [Theory]
    [InlineData("Deadlock victim detected in database engine", true)]
    [InlineData("TCP Provider failed to establish socket connection", true)]
    [InlineData("Simple arithmetic overflow error", false)]
    public void IsDatabaseException_IdentifiesCorrectly(string message, bool expected)
    {
        var ex = new Exception(message);
        ErrorClassifier.IsDatabaseException(ex).Should().Be(expected);
    }
}
