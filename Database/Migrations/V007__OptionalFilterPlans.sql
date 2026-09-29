-- ============================================================================
-- V007: optional-filter procedures get a plan for the values of each call, and a redundant index goes away
--
-- The list procedures share one statement for every combination of filters:
--     WHERE (@Name IS NULL OR ...) AND (@Category IS NULL OR ...) AND (@AfterId IS NULL OR Id > @AfterId)
-- SQL Server compiles such a statement once, for the first call, and reuses that plan. The plan of a first page
-- (no cursor, no filter) reads the table from its start, so a deep cursor page then read the whole table
-- (2,436 pages for 200,000 products instead of 3) and an order list filtered by customer read every order
-- (8,304 pages instead of 3). OPTION (RECOMPILE) lets the optimizer see the real values, remove the unused
-- filters and seek: the cursor page and the customer filter cost 3 page reads again. The compile costs about a
-- millisecond, small next to the scan it avoids.
-- ============================================================================

-- @Name must already be LIKE-escaped with '\' by the caller.
CREATE OR ALTER PROCEDURE ProductsGetAll
    @PageNumber INT           = 0,
    @PageSize   INT           = 10,
    @Name       NVARCHAR(150) = NULL,
    @Category   NVARCHAR(20)  = NULL
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Offset INT = @PageNumber * @PageSize;

    SELECT COUNT(*) AS TotalCount
    FROM vw_Products
    WHERE (@Name IS NULL OR Name LIKE N'%' + @Name + N'%' ESCAPE N'\')
      AND (@Category IS NULL OR Category = @Category)
    OPTION (RECOMPILE);

    SELECT Id, Name, Category, Price, Cost, AvailableQuantity, StockStatus, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM vw_Products
    WHERE (@Name IS NULL OR Name LIKE N'%' + @Name + N'%' ESCAPE N'\')
      AND (@Category IS NULL OR Category = @Category)
    ORDER BY Id ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY
    OPTION (RECOMPILE);
END
GO

-- Keyset page: the rows after @AfterId in Id order, one more than @Limit so that the caller can tell whether there is a next page.
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
    ORDER BY Id ASC
    OPTION (RECOMPILE);
END
GO

CREATE OR ALTER PROCEDURE OrdersGetAll
    @Customer      NVARCHAR(50) = NULL,
    @CustomerId    BIGINT       = NULL,
    @PaymentMethod NVARCHAR(20) = NULL,
    @PageNumber    INT          = 0,
    @PageSize      INT          = 10
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Offset INT = @PageNumber * @PageSize;

    SELECT COUNT(*) AS TotalCount
    FROM vw_Orders
    WHERE (@Customer IS NULL OR CustomerUsername LIKE N'%' + @Customer + N'%' ESCAPE N'\')
      AND (@CustomerId IS NULL OR CustomerId = @CustomerId)
      AND (@PaymentMethod IS NULL OR PaymentMethod = @PaymentMethod)
    OPTION (RECOMPILE);

    SELECT
        Id,
        CustomerId,
        CustomerUsername,
        SubtotalAmount,
        DiscountAmount,
        TotalAmount,
        TotalCost,
        Profit,
        PaymentMethod,
        CreatedAt AS PurchaseDate
    FROM vw_Orders
    WHERE (@Customer IS NULL OR CustomerUsername LIKE N'%' + @Customer + N'%' ESCAPE N'\')
      AND (@CustomerId IS NULL OR CustomerId = @CustomerId)
      AND (@PaymentMethod IS NULL OR PaymentMethod = @PaymentMethod)
    ORDER BY CreatedAt DESC, Id DESC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY
    OPTION (RECOMPILE);
END
GO

-- IX_Products_Category (Category) INCLUDE (Name) from V001 is a prefix of IX_Products_Category_Id (Category, Id) INCLUDE (Name, ...)
-- from V004, which serves every query that used it; keeping both only made each product write update two indexes.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Products_Category' AND object_id = OBJECT_ID(N'Products'))
    DROP INDEX IX_Products_Category ON Products;
GO
