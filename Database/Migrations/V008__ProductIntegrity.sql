-- ============================================================================
-- V008: integrity rules of a product that the API already enforces, now also enforced by the database
--
--   * the name has no leading or trailing whitespace (the API stores it trimmed): otherwise " Chair" and "Chair" would both pass
--     the unique index and look like two different products;
--   * the cost never exceeds the price: a product is not sold below what it costs (ProductFormValidator);
--   * the stock is at most 1,000,000 units (ProductFormValidator.MaxQuantity);
--   * "last updated" is complete (who and when together) and never precedes "created".
-- (The price is already greater than 0: CK_Products_Price from V001.)
--
-- When an existing row already breaks a rule, that constraint is added WITH NOCHECK: it is enforced for every new or changed row at once
-- and the old rows stay as they are until they are corrected, after which
--     ALTER TABLE Products WITH CHECK CHECK CONSTRAINT <name>;
-- makes it trusted. On a new database each constraint is added WITH CHECK, so nothing is left untrusted.
-- ============================================================================

IF EXISTS (SELECT 1 FROM Products WHERE DATALENGTH(Name) <> DATALENGTH(LTRIM(RTRIM(Name))))
    ALTER TABLE Products WITH NOCHECK ADD CONSTRAINT CK_Products_Name_Trimmed CHECK (DATALENGTH(Name) = DATALENGTH(LTRIM(RTRIM(Name))));
ELSE
    ALTER TABLE Products WITH CHECK ADD CONSTRAINT CK_Products_Name_Trimmed CHECK (DATALENGTH(Name) = DATALENGTH(LTRIM(RTRIM(Name))));
GO

IF EXISTS (SELECT 1 FROM Products WHERE Cost > Price)
    ALTER TABLE Products WITH NOCHECK ADD CONSTRAINT CK_Products_CostWithinPrice CHECK (Cost <= Price);
ELSE
    ALTER TABLE Products WITH CHECK ADD CONSTRAINT CK_Products_CostWithinPrice CHECK (Cost <= Price);
GO

IF EXISTS (SELECT 1 FROM Products WHERE AvailableQuantity > 1000000)
    ALTER TABLE Products WITH NOCHECK ADD CONSTRAINT CK_Products_AvailableQuantity_Max CHECK (AvailableQuantity <= 1000000);
ELSE
    ALTER TABLE Products WITH CHECK ADD CONSTRAINT CK_Products_AvailableQuantity_Max CHECK (AvailableQuantity <= 1000000);
GO

IF EXISTS (SELECT 1 FROM Products WHERE NOT ((UpdatedBy IS NULL AND UpdatedAt IS NULL) OR (UpdatedBy IS NOT NULL AND UpdatedAt IS NOT NULL AND UpdatedAt >= CreatedAt)))
    ALTER TABLE Products WITH NOCHECK ADD CONSTRAINT CK_Products_UpdateAudit
        CHECK ((UpdatedBy IS NULL AND UpdatedAt IS NULL) OR (UpdatedBy IS NOT NULL AND UpdatedAt IS NOT NULL AND UpdatedAt >= CreatedAt));
ELSE
    ALTER TABLE Products WITH CHECK ADD CONSTRAINT CK_Products_UpdateAudit
        CHECK ((UpdatedBy IS NULL AND UpdatedAt IS NULL) OR (UpdatedBy IS NOT NULL AND UpdatedAt IS NOT NULL AND UpdatedAt >= CreatedAt));
GO
