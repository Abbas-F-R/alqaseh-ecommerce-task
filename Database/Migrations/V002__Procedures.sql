-- ============================================================================
-- V002: stored procedures for users, products and the audit trail
-- Order, discount and stock statements are parameterized Dapper queries in the repositories
-- because they run inside the checkout transaction opened by the application.
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

CREATE OR ALTER PROCEDURE ProductsGetById
    @Id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SELECT Id, Name, Category, Price, Cost, AvailableQuantity, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM Products
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

-- Page of products, filtered by name (contains) and category. Returns two result sets: the total count, then the page.
-- @Name must already be LIKE-escaped with '\' by the caller.
CREATE OR ALTER PROCEDURE ProductsGetAll
    @PageNumber INT           = 1,
    @PageSize   INT           = 10,
    @Name       NVARCHAR(150) = NULL,
    @Category   NVARCHAR(20)  = NULL
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @Offset INT = (@PageNumber - 1) * @PageSize;

    SELECT COUNT(*) AS TotalCount
    FROM Products
    WHERE (@Name IS NULL OR Name LIKE N'%' + @Name + N'%' ESCAPE N'\')
      AND (@Category IS NULL OR Category = @Category);

    SELECT Id, Name, Category, Price, Cost, AvailableQuantity, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM Products
    WHERE (@Name IS NULL OR Name LIKE N'%' + @Name + N'%' ESCAPE N'\')
      AND (@Category IS NULL OR Category = @Category)
    ORDER BY Id
    OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;
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

    SELECT Id, Name, Category, Price, Cost, AvailableQuantity, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM Products
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

    SELECT Id, Name, Category, Price, Cost, AvailableQuantity, CreatedBy, CreatedAt, UpdatedBy, UpdatedAt
    FROM Products
    WHERE Id = @Id;
END
GO
