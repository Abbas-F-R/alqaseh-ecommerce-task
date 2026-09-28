-- ============================================================================
-- V003: audit the tables that hold business data
-- Register a table here (IsAudited = 1, IsEnabled = 1) and call usp_CreateAuditTrigger to audit it.
-- Users is deliberately not audited: its rows contain password hashes.
-- ============================================================================

INSERT INTO AuditTables (TableId, TableName, IsAudited, IsEnabled)
VALUES (1, 'Products', 1, 1),
       (2, 'DiscountCodes', 1, 1),
       (3, 'Orders', 1, 1),
       (4, 'OrderItems', 1, 1);
GO

EXEC usp_CreateAuditTrigger @TableName = 'Products';
EXEC usp_CreateAuditTrigger @TableName = 'DiscountCodes';
EXEC usp_CreateAuditTrigger @TableName = 'Orders';
EXEC usp_CreateAuditTrigger @TableName = 'OrderItems';
GO
