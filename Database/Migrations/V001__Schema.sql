-- ============================================================================
-- V001: tables, constraints and indexes
-- Applied once by DatabaseMigrator (see SchemaMigrations). Timestamps are UTC.
-- ============================================================================

CREATE TABLE Users (
    Id           BIGINT IDENTITY(1,1) NOT NULL,
    FullName     NVARCHAR(150) NOT NULL,
    UserName     NVARCHAR(50)  NOT NULL,
    PasswordHash NVARCHAR(255) NOT NULL,
    Role         NVARCHAR(20)  NOT NULL,
    CreatedAt    DATETIME2(3)  NOT NULL CONSTRAINT DF_Users_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Users PRIMARY KEY (Id),
    CONSTRAINT UQ_Users_UserName UNIQUE (UserName),
    CONSTRAINT CK_Users_Role CHECK (Role IN ('Admin', 'Customer'))
);
GO

CREATE TABLE Products (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    Name              NVARCHAR(150) NOT NULL,
    Category          NVARCHAR(20)  NOT NULL,
    Price             DECIMAL(18,2) NOT NULL,
    Cost              DECIMAL(18,2) NOT NULL,
    AvailableQuantity INT           NOT NULL,
    CreatedBy         BIGINT        NOT NULL,
    CreatedAt         DATETIME2(3)  NOT NULL CONSTRAINT DF_Products_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedBy         BIGINT        NULL,
    UpdatedAt         DATETIME2(3)  NULL,
    CONSTRAINT PK_Products PRIMARY KEY (Id),
    -- Product names are unique (case-insensitive, the database collation is case-insensitive).
    CONSTRAINT UQ_Products_Name UNIQUE (Name),
    CONSTRAINT FK_Products_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES Users(Id),
    CONSTRAINT FK_Products_UpdatedBy FOREIGN KEY (UpdatedBy) REFERENCES Users(Id),
    -- Categories are fixed.
    CONSTRAINT CK_Products_Category CHECK (Category IN ('furniture', 'electronics', 'beauty', 'garden')),
    CONSTRAINT CK_Products_Price CHECK (Price > 0),
    CONSTRAINT CK_Products_Cost CHECK (Cost >= 0),
    CONSTRAINT CK_Products_AvailableQuantity CHECK (AvailableQuantity >= 0)
);
GO

CREATE INDEX IX_Products_Category ON Products (Category) INCLUDE (Name);
GO

CREATE TABLE DiscountCodes (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    Code              NVARCHAR(50)  NOT NULL,
    Amount            DECIMAL(18,2) NOT NULL,
    MinimumOrderTotal DECIMAL(18,2) NOT NULL,
    ExpiresAt         DATETIME2(3)  NOT NULL,
    Used              BIT           NOT NULL CONSTRAINT DF_DiscountCodes_Used DEFAULT 0,
    CONSTRAINT PK_DiscountCodes PRIMARY KEY (Id),
    CONSTRAINT UQ_DiscountCodes_Code UNIQUE (Code),
    CONSTRAINT CK_DiscountCodes_Amount CHECK (Amount > 0),
    CONSTRAINT CK_DiscountCodes_MinimumOrderTotal CHECK (MinimumOrderTotal >= 0)
);
GO

CREATE TABLE Orders (
    Id             BIGINT IDENTITY(1,1) NOT NULL,
    CustomerId     BIGINT        NOT NULL,
    SubtotalAmount DECIMAL(18,2) NOT NULL,
    DiscountAmount DECIMAL(18,2) NOT NULL CONSTRAINT DF_Orders_DiscountAmount DEFAULT 0,
    TotalAmount    DECIMAL(18,2) NOT NULL,
    TotalCost      DECIMAL(18,2) NOT NULL,
    DiscountCodeId BIGINT        NULL,
    PaymentMethod  NVARCHAR(20)  NOT NULL,
    CreatedAt      DATETIME2(3)  NOT NULL CONSTRAINT DF_Orders_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Orders PRIMARY KEY (Id),
    CONSTRAINT FK_Orders_Customer FOREIGN KEY (CustomerId) REFERENCES Users(Id),
    CONSTRAINT FK_Orders_DiscountCode FOREIGN KEY (DiscountCodeId) REFERENCES DiscountCodes(Id),
    CONSTRAINT CK_Orders_PaymentMethod CHECK (PaymentMethod IN ('CreditCard', 'XyzWallet')),
    CONSTRAINT CK_Orders_Amounts CHECK (SubtotalAmount >= 0 AND DiscountAmount >= 0 AND TotalCost >= 0
                                        AND TotalAmount = SubtotalAmount - DiscountAmount AND TotalAmount >= 0)
);
GO

-- A discount code can be used by at most one order (database-level guarantee of "single use").
CREATE UNIQUE INDEX UQ_Orders_DiscountCodeId ON Orders (DiscountCodeId) WHERE DiscountCodeId IS NOT NULL;
-- Customer's own orders, newest first.
CREATE INDEX IX_Orders_Customer_CreatedAt ON Orders (CustomerId, CreatedAt DESC, Id DESC);
-- Admin list filtered by payment method, newest first.
CREATE INDEX IX_Orders_PaymentMethod_CreatedAt ON Orders (PaymentMethod, CreatedAt DESC, Id DESC);
-- Admin list without filters, newest first.
CREATE INDEX IX_Orders_CreatedAt ON Orders (CreatedAt DESC, Id DESC);
GO

CREATE TABLE OrderItems (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    OrderId     BIGINT        NOT NULL,
    ProductId   BIGINT        NOT NULL,
    ProductName NVARCHAR(150) NOT NULL,
    UnitPrice   DECIMAL(18,2) NOT NULL,
    UnitCost    DECIMAL(18,2) NOT NULL,
    Quantity    INT           NOT NULL,
    CONSTRAINT PK_OrderItems PRIMARY KEY (Id),
    CONSTRAINT FK_OrderItems_Order FOREIGN KEY (OrderId) REFERENCES Orders(Id),
    CONSTRAINT FK_OrderItems_Product FOREIGN KEY (ProductId) REFERENCES Products(Id),
    CONSTRAINT CK_OrderItems_Quantity CHECK (Quantity > 0),
    CONSTRAINT CK_OrderItems_UnitPrice CHECK (UnitPrice >= 0),
    CONSTRAINT CK_OrderItems_UnitCost CHECK (UnitCost >= 0)
);
GO

CREATE INDEX IX_OrderItems_OrderId ON OrderItems (OrderId);
CREATE INDEX IX_OrderItems_ProductId ON OrderItems (ProductId);
GO

-- Audit trail, written by database triggers (see V002 / V003), not by the application. Not exposed through the API.
-- Same design as the Official Correspondence System: AuditTables registers the audited tables, AuditLog holds one row per changed record.
CREATE TABLE AuditTables (
    TableId   INT          NOT NULL,
    TableName VARCHAR(128) NOT NULL,
    IsAudited BIT          NOT NULL CONSTRAINT DF_AuditTables_IsAudited DEFAULT 1,
    IsEnabled BIT          NOT NULL CONSTRAINT DF_AuditTables_IsEnabled DEFAULT 0,
    CreatedAt DATETIME2(3) NOT NULL CONSTRAINT DF_AuditTables_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_AuditTables PRIMARY KEY (TableId),
    CONSTRAINT UQ_AuditTables_TableName UNIQUE (TableName)
);
GO

CREATE TABLE AuditLog (
    Id         BIGINT IDENTITY(1,1) NOT NULL,
    UserId     BIGINT        NOT NULL,   -- 0 when no user is known
    ActionType VARCHAR(50)   NOT NULL,   -- INSERT | UPDATE | DELETE
    TableId    INT           NOT NULL,
    RecordId   BIGINT        NOT NULL,
    Details    NVARCHAR(MAX) NULL,       -- the affected row as JSON
    CreatedAt  DATETIME2(3)  NOT NULL CONSTRAINT DF_AuditLog_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_AuditLog PRIMARY KEY (Id)
);
GO

CREATE INDEX IX_AuditLog_CreatedAt ON AuditLog (CreatedAt) INCLUDE (TableId, ActionType, UserId, RecordId);
CREATE INDEX IX_AuditLog_TableId_RecordId ON AuditLog (TableId, RecordId);
CREATE INDEX IX_AuditLog_UserId_CreatedAt ON AuditLog (UserId, CreatedAt DESC) INCLUDE (TableId, ActionType, RecordId);
GO
