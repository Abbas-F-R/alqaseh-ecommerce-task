using System.Data;
using System.Data.Common;
using Dapper;
using Microsoft.Data.SqlClient;

namespace AlQaseh_Ecommerce_API.Infrastructure.Persistence;

/// <summary>
/// Creates SQL Server connections from <c>ConnectionStrings:DefaultConnection</c>
/// (override with the <c>ConnectionStrings__DefaultConnection</c> environment variable).
/// </summary>
public class DapperContext
{
    static DapperContext()
    {
        // DATETIME2 values are UTC; give them Kind=Utc so they serialize with a "Z".
        SqlMapper.AddTypeHandler(new UtcDateTimeHandler());
    }

    public DapperContext(IConfiguration configuration)
    {
        ConnectionString = configuration.GetConnectionString("DefaultConnection")
                           ?? throw new InvalidOperationException("ConnectionStrings:DefaultConnection is not configured.");
    }

    public string ConnectionString { get; }

    public DbConnection CreateConnection() => new SqlConnection(ConnectionString);

    private sealed class UtcDateTimeHandler : SqlMapper.TypeHandler<DateTime>
    {
        public override DateTime Parse(object value) => DateTime.SpecifyKind((DateTime)value, DateTimeKind.Utc);

        public override void SetValue(IDbDataParameter parameter, DateTime value)
        {
            parameter.DbType = DbType.DateTime2;
            parameter.Value = value.Kind == DateTimeKind.Local ? value.ToUniversalTime() : value;
        }
    }
}
