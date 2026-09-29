-- ============================================================================
-- V004: Keyset pagination index and stored procedure for Products
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Products_Category_Id' AND object_id = OBJECT_ID(N'Products'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_Products_Category_Id
    ON Products (Category, Id)
    INCLUDE (Name, Price, Cost, AvailableQuantity);
END
GO

CREATE OR ALTER PROCEDURE ProductsGetCursor
    @Limit    INT           = 10,
    @AfterId  BIGINT        = NULL,
    @Name     NVARCHAR(150) = NULL,
    @Category NVARCHAR(20)  = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @FetchCount INT = @Limit + 1;

    SELECT TOP (@FetchCount)
        Id, Name, Category, Price, Cost, AvailableQuantity, StockStatus, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM vw_Products
    WHERE (@Name IS NULL OR Name LIKE N'%' + @Name + N'%' ESCAPE N'\')
      AND (@Category IS NULL OR Category = @Category)
      AND (@AfterId IS NULL OR Id > @AfterId)
    ORDER BY Id ASC;
END
GO
