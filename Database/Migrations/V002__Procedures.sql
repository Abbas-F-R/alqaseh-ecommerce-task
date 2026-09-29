-- ============================================================================
-- V002: Views and stored procedures for users, products, orders, discounts, session, seed and audit trail
-- Eliminates raw SQL strings across the application.
-- ============================================================================

CREATE OR ALTER PROCEDURE UsersGetByUserName
    @UserName NVARCHAR(50)
AS
BEGIN
    SET NOCOUNT ON;
    SELECT Id, FullName, UserName, PasswordHash, Role, CreatedAt
    FROM Users
    WHERE UserName = @UserName;
END
GO

-- Called by the audit triggers: writes one AuditLog row per changed record of an audited and enabled table.
-- The acting user comes from SESSION_CONTEXT('UserId'), which the application (UnitOfWork) or the procedures below set.
CREATE OR ALTER PROCEDURE AuditWrite
    @TableName  VARCHAR(128),
    @ActionType VARCHAR(50),       -- INSERT | UPDATE | DELETE
    @Payload    NVARCHAR(MAX)      -- FOR JSON PATH array of the affected rows
AS
BEGIN
    SET NOCOUNT ON;

    IF @Payload IS NULL
        RETURN;

    DECLARE @TableId INT;

    SELECT @TableId = TableId
    FROM AuditTables
    WHERE TableName = @TableName AND IsAudited = 1 AND IsEnabled = 1;

    IF @TableId IS NULL
        RETURN;                    -- not audited or not enabled: no-op

    DECLARE @UserId BIGINT = COALESCE(TRY_CAST(SESSION_CONTEXT(N'UserId') AS BIGINT), 0);

    INSERT INTO AuditLog (UserId, ActionType, TableId, RecordId, Details)
    SELECT @UserId, @ActionType, @TableId, TRY_CAST(JSON_VALUE(j.[value], '$.Id') AS BIGINT), j.[value]
    FROM OPENJSON(@Payload) AS j;
END
GO

-- Creates (or replaces) the audit trigger of a table that is registered in AuditTables and has an Id column.
CREATE OR ALTER PROCEDURE usp_CreateAuditTrigger
    @TableName SYSNAME
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = @TableName)
    BEGIN
        RAISERROR('usp_CreateAuditTrigger: table %s does not exist.', 16, 1, @TableName);
        RETURN;
    END

    IF NOT EXISTS (SELECT 1 FROM AuditTables WHERE TableName = @TableName AND IsAudited = 1)
    BEGIN
        RAISERROR('usp_CreateAuditTrigger: %s is not registered in AuditTables (IsAudited=1).', 16, 1, @TableName);
        RETURN;
    END

    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(@TableName) AND name = 'Id')
    BEGIN
        RAISERROR('usp_CreateAuditTrigger: %s has no Id column; cannot derive RecordId.', 16, 1, @TableName);
        RETURN;
    END

    DECLARE @sql NVARCHAR(MAX) = N'
CREATE OR ALTER TRIGGER [dbo].[TR_' + @TableName + N'_Audit]
ON [dbo].[' + @TableName + N']
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted)
        RETURN;

    DECLARE @Action  VARCHAR(10);
    DECLARE @Payload NVARCHAR(MAX);

    IF EXISTS (SELECT 1 FROM inserted) AND EXISTS (SELECT 1 FROM deleted)
    BEGIN
        SET @Action  = ''UPDATE'';
        SET @Payload = (SELECT * FROM inserted FOR JSON PATH);
    END
    ELSE IF EXISTS (SELECT 1 FROM inserted)
    BEGIN
        SET @Action  = ''INSERT'';
        SET @Payload = (SELECT * FROM inserted FOR JSON PATH);
    END
    ELSE
    BEGIN
        SET @Action  = ''DELETE'';
        SET @Payload = (SELECT * FROM deleted FOR JSON PATH);
    END

    EXEC dbo.AuditWrite
         @TableName  = N''' + @TableName + N''',
         @ActionType = @Action,
         @Payload    = @Payload;
END';

    EXEC sys.sp_executesql @sql;
END
GO

-- ============================================================================
-- VIEWS
-- ============================================================================

-- Products view with computed StockStatus for customer projections
CREATE OR ALTER VIEW vw_Products AS
SELECT
    p.Id,
    p.Name,
    p.Category,
    p.Price,
    p.Cost,
    p.AvailableQuantity,
    CASE
        WHEN p.AvailableQuantity < 5 THEN 'low'
        WHEN p.AvailableQuantity < 10 THEN 'limited'
        ELSE 'available'
    END AS StockStatus,
    p.CreatedBy,
    p.CreatedAt,
    p.UpdatedBy,
    p.UpdatedAt
FROM Products p;
GO

-- Orders view joined with Users for customer username and profit calculation
CREATE OR ALTER VIEW vw_Orders AS
SELECT
    o.Id,
    o.CustomerId,
    u.UserName AS CustomerUsername,
    o.SubtotalAmount,
    o.DiscountAmount,
    o.TotalAmount,
    o.TotalCost,
    o.TotalAmount - o.TotalCost AS Profit,
    o.DiscountCodeId,
    o.PaymentMethod,
    o.CreatedAt
FROM Orders o
INNER JOIN Users u ON u.Id = o.CustomerId;
GO

-- OrderItems view with calculated Subtotal
CREATE OR ALTER VIEW vw_OrderItems AS
SELECT
    oi.Id,
    oi.OrderId,
    oi.ProductId,
    oi.ProductName,
    oi.UnitPrice,
    oi.UnitCost,
    oi.Quantity,
    oi.UnitPrice * oi.Quantity AS Subtotal
FROM OrderItems oi;
GO

-- DiscountCodes view
CREATE OR ALTER VIEW vw_DiscountCodes AS
SELECT
    d.Id,
    d.Code,
    d.Amount,
    d.MinimumOrderTotal,
    d.ExpiresAt,
    d.Used
FROM DiscountCodes d;
GO

-- ============================================================================
-- PRODUCTS PROCEDURES
-- ============================================================================

CREATE OR ALTER PROCEDURE ProductsGetById
    @Id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SELECT Id, Name, Category, Price, Cost, AvailableQuantity, StockStatus, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM vw_Products
    WHERE Id = @Id;
END
GO

-- Case-insensitive name lookup (used for the friendly duplicate-name error; UQ_Products_Name is the real guarantee).
CREATE OR ALTER PROCEDURE ProductsNameExists
    @Name      NVARCHAR(150),
    @ExcludeId BIGINT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM Products WHERE Name = @Name AND (@ExcludeId IS NULL OR Id <> @ExcludeId))
                     THEN 1 ELSE 0 END AS BIT);
END
GO

-- Check if product exists by Id
CREATE OR ALTER PROCEDURE ProductsCheckExists
    @ProductId BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM Products WHERE Id = @ProductId) THEN 1 ELSE 0 END AS BIT);
END
GO

-- Page of products, filtered by name (contains) and category. Returns two result sets: the total count, then the page.
-- Queries vw_Products.
-- Supports cursor/keyset pagination via @AfterId, or offset pagination via @PageNumber / @PageSize.
-- @Name must already be LIKE-escaped with '\' by the caller.
CREATE OR ALTER PROCEDURE ProductsGetAll
    @PageNumber INT           = 1,
    @PageSize   INT           = 10,
    @Name       NVARCHAR(150) = NULL,
    @Category   NVARCHAR(20)  = NULL,
    @AfterId    BIGINT        = NULL
AS
BEGIN
    SET NOCOUNT ON;

    SELECT COUNT(*) AS TotalCount
    FROM vw_Products
    WHERE (@Name IS NULL OR Name LIKE N'%' + @Name + N'%' ESCAPE N'\')
      AND (@Category IS NULL OR Category = @Category);

    IF @AfterId IS NOT NULL
    BEGIN
        SELECT Id, Name, Category, Price, Cost, AvailableQuantity, StockStatus, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
        FROM vw_Products
        WHERE (@Name IS NULL OR Name LIKE N'%' + @Name + N'%' ESCAPE N'\')
          AND (@Category IS NULL OR Category = @Category)
          AND Id > @AfterId
        ORDER BY Id ASC
        OFFSET 0 ROWS FETCH NEXT @PageSize ROWS ONLY;
    END
    ELSE
    BEGIN
        DECLARE @EffectivePage INT = CASE WHEN @PageNumber < 1 THEN 1 ELSE @PageNumber END;
        DECLARE @Offset INT = (@EffectivePage - 1) * @PageSize;

        SELECT Id, Name, Category, Price, Cost, AvailableQuantity, StockStatus, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
        FROM vw_Products
        WHERE (@Name IS NULL OR Name LIKE N'%' + @Name + N'%' ESCAPE N'\')
          AND (@Category IS NULL OR Category = @Category)
        ORDER BY Id ASC
        OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
    END
END
GO

CREATE OR ALTER PROCEDURE ProductsInsert
    @Name              NVARCHAR(150),
    @Category          NVARCHAR(20),
    @Price             DECIMAL(18,2),
    @Cost              DECIMAL(18,2),
    @AvailableQuantity INT,
    @CreatedBy         BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @NewId BIGINT;

    -- Tells the audit trigger who is acting.
    EXEC sys.sp_set_session_context @key = N'UserId', @value = @CreatedBy;

    INSERT INTO Products (Name, Category, Price, Cost, AvailableQuantity, CreatedBy)
    VALUES (@Name, @Category, @Price, @Cost, @AvailableQuantity, @CreatedBy);

    SET @NewId = SCOPE_IDENTITY();

    SELECT Id, Name, Category, Price, Cost, AvailableQuantity, StockStatus, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM vw_Products
    WHERE Id = @NewId;
END
GO

-- Replaces every editable field. Returns no row when the product does not exist.
CREATE OR ALTER PROCEDURE ProductsUpdate
    @Id                BIGINT,
    @Name              NVARCHAR(150),
    @Category          NVARCHAR(20),
    @Price             DECIMAL(18,2),
    @Cost              DECIMAL(18,2),
    @AvailableQuantity INT,
    @UpdatedBy         BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    EXEC sys.sp_set_session_context @key = N'UserId', @value = @UpdatedBy;

    UPDATE Products
    SET Name              = @Name,
        Category          = @Category,
        Price             = @Price,
        Cost              = @Cost,
        AvailableQuantity = @AvailableQuantity,
        UpdatedBy         = @UpdatedBy,
        UpdatedAt         = SYSUTCDATETIME()
    WHERE Id = @Id;

    SELECT Id, Name, Category, Price, Cost, AvailableQuantity, StockStatus, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM vw_Products
    WHERE Id = @Id;
END
GO

-- Atomically reserve product stock if available, returning snapshot fields
CREATE OR ALTER PROCEDURE ProductsReserveStock
    @ProductId BIGINT,
    @Quantity  INT
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @reserved TABLE (Id BIGINT, Name NVARCHAR(150), Price DECIMAL(18,2), Cost DECIMAL(18,2));

    UPDATE Products
    SET AvailableQuantity = AvailableQuantity - @Quantity
    OUTPUT inserted.Id, inserted.Name, inserted.Price, inserted.Cost INTO @reserved
    WHERE Id = @ProductId AND AvailableQuantity >= @Quantity;

    SELECT Id, Name, Price, Cost FROM @reserved;
END
GO

-- ============================================================================
-- DISCOUNT CODES PROCEDURES
-- ============================================================================

-- Lock and get discount code row for update
CREATE OR ALTER PROCEDURE DiscountCodesGetByCodeForUpdate
    @Code NVARCHAR(50)
AS
BEGIN
    SET NOCOUNT ON;
    SELECT Id, Code, Amount, MinimumOrderTotal, ExpiresAt, Used
    FROM DiscountCodes WITH (UPDLOCK, ROWLOCK)
    WHERE Code = @Code;
END
GO

-- Atomically mark discount code as used if not already used
CREATE OR ALTER PROCEDURE DiscountCodesMarkAsUsed
    @Id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE DiscountCodes
    SET Used = 1
    WHERE Id = @Id AND Used = 0;

    SELECT @@ROWCOUNT AS RowsAffected;
END
GO

-- ============================================================================
-- ORDERS & ORDER ITEMS PROCEDURES
-- ============================================================================

-- Insert a new order and return generated Id and CreatedAt
CREATE OR ALTER PROCEDURE OrdersInsert
    @CustomerId     BIGINT,
    @Subtotal       DECIMAL(18,2),
    @Discount       DECIMAL(18,2),
    @Total          DECIMAL(18,2),
    @TotalCost      DECIMAL(18,2),
    @DiscountCodeId BIGINT = NULL,
    @PaymentMethod  NVARCHAR(20)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @inserted TABLE (Id BIGINT, CreatedAt DATETIME2(3));

    INSERT INTO Orders (CustomerId, SubtotalAmount, DiscountAmount, TotalAmount, TotalCost, DiscountCodeId, PaymentMethod)
    OUTPUT inserted.Id, inserted.CreatedAt INTO @inserted
    VALUES (@CustomerId, @Subtotal, @Discount, @Total, @TotalCost, @DiscountCodeId, @PaymentMethod);

    SELECT Id, CreatedAt FROM @inserted;
END
GO

-- Insert a single order item
CREATE OR ALTER PROCEDURE OrderItemsInsert
    @OrderId     BIGINT,
    @ProductId   BIGINT,
    @ProductName NVARCHAR(150),
    @UnitPrice   DECIMAL(18,2),
    @UnitCost    DECIMAL(18,2),
    @Quantity    INT
AS
BEGIN
    SET NOCOUNT ON;
    INSERT INTO OrderItems (OrderId, ProductId, ProductName, UnitPrice, UnitCost, Quantity)
    VALUES (@OrderId, @ProductId, @ProductName, @UnitPrice, @UnitCost, @Quantity);
END
GO

-- Get customer's orders paginated from vw_Orders
CREATE OR ALTER PROCEDURE OrdersGetByCustomer
    @CustomerId BIGINT,
    @PageNumber INT = 0,
    @PageSize   INT = 10
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Offset INT = @PageNumber * @PageSize;

    SELECT COUNT(*) AS TotalCount
    FROM vw_Orders
    WHERE CustomerId = @CustomerId;

    SELECT
        Id,
        TotalAmount AS TotalPrice,
        PaymentMethod,
        CreatedAt AS PurchaseDate,
        DiscountAmount
    FROM vw_Orders
    WHERE CustomerId = @CustomerId
    ORDER BY CreatedAt DESC, Id DESC
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
END
GO

-- Get all orders with optional filters paginated from vw_Orders
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
      AND (@PaymentMethod IS NULL OR PaymentMethod = @PaymentMethod);

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
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
END
GO

-- Get order items for multiple orders given a CSV list of IDs from vw_OrderItems
CREATE OR ALTER PROCEDURE OrderItemsGetByOrderIds
    @OrderIdsCsv NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SELECT
        oi.OrderId,
        oi.ProductId,
        oi.ProductName,
        oi.UnitPrice,
        oi.Quantity,
        oi.Subtotal
    FROM vw_OrderItems oi
    INNER JOIN STRING_SPLIT(@OrderIdsCsv, ',') s ON oi.OrderId = TRY_CONVERT(BIGINT, s.value)
    ORDER BY oi.Id ASC;
END
GO

-- ============================================================================
-- SESSION & SEED PROCEDURES
-- ============================================================================

-- Sets session context for audit triggers
CREATE OR ALTER PROCEDURE SetSessionUserId
    @UserId BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    EXEC sys.sp_set_session_context @key = N'UserId', @value = @UserId;
END
GO

-- Seed helper: insert user if username does not exist
CREATE OR ALTER PROCEDURE SeedUserIfNotExists
    @FullName     NVARCHAR(150),
    @UserName     NVARCHAR(50),
    @PasswordHash NVARCHAR(255),
    @Role         NVARCHAR(20)
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM Users WHERE UserName = @UserName)
        INSERT INTO Users (FullName, UserName, PasswordHash, Role)
        VALUES (@FullName, @UserName, @PasswordHash, @Role);
END
GO

-- Seed helper: insert product if name does not exist
CREATE OR ALTER PROCEDURE SeedProductIfNotExists
    @Name              NVARCHAR(150),
    @Category          NVARCHAR(20),
    @Price             DECIMAL(18,2),
    @Cost              DECIMAL(18,2),
    @AvailableQuantity INT,
    @AdminId           BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM Products WHERE Name = @Name)
        INSERT INTO Products (Name, Category, Price, Cost, AvailableQuantity, CreatedBy)
        VALUES (@Name, @Category, @Price, @Cost, @AvailableQuantity, @AdminId);
END
GO

-- Seed helper: insert discount code if code does not exist
CREATE OR ALTER PROCEDURE SeedDiscountCodeIfNotExists
    @Code              NVARCHAR(50),
    @Amount            DECIMAL(18,2),
    @MinimumOrderTotal DECIMAL(18,2),
    @ExpiresAt         DATETIME2(3),
    @Used              BIT
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM DiscountCodes WHERE Code = @Code)
        INSERT INTO DiscountCodes (Code, Amount, MinimumOrderTotal, ExpiresAt, Used)
        VALUES (@Code, @Amount, @MinimumOrderTotal, @ExpiresAt, @Used);
END
GO
