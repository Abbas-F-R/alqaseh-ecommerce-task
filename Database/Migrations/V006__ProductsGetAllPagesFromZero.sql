-- ============================================================================
-- V006: the admin product list pages from 0, like the order lists and the API (V002 counted from 1 and carried a
-- cursor branch that ProductsGetCursor (V004) replaced). No behavior other than the page numbering changes.
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
      AND (@Category IS NULL OR Category = @Category);

    SELECT Id, Name, Category, Price, Cost, AvailableQuantity, StockStatus, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM vw_Products
    WHERE (@Name IS NULL OR Name LIKE N'%' + @Name + N'%' ESCAPE N'\')
      AND (@Category IS NULL OR Category = @Category)
    ORDER BY Id ASC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
END
GO
