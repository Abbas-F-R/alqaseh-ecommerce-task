-- ============================================================================
-- V005: integrity rules that the application already checks, now also enforced by the database
-- (so that a bug, a race or a direct write cannot store invalid data)
-- ============================================================================

-- Names, user names and codes are never blank.
ALTER TABLE Users         ADD CONSTRAINT CK_Users_FullName_NotBlank     CHECK (LEN(LTRIM(RTRIM(FullName))) > 0);
ALTER TABLE Users         ADD CONSTRAINT CK_Users_UserName_NotBlank     CHECK (LEN(LTRIM(RTRIM(UserName))) > 0);
ALTER TABLE Products      ADD CONSTRAINT CK_Products_Name_NotBlank      CHECK (LEN(LTRIM(RTRIM(Name))) > 0);
ALTER TABLE DiscountCodes ADD CONSTRAINT CK_DiscountCodes_Code_NotBlank CHECK (LEN(LTRIM(RTRIM(Code))) > 0);
GO

-- A product appears once per order (the service merges repeated lines); this index also serves lookups by OrderId,
-- so the single-column index from V001 is redundant.
CREATE UNIQUE INDEX UQ_OrderItems_Order_Product ON OrderItems (OrderId, ProductId);
DROP INDEX IX_OrderItems_OrderId ON OrderItems;
GO

-- The discount amount and the discount code go together: no code means no discount, a code means a positive discount.
ALTER TABLE Orders ADD CONSTRAINT CK_Orders_DiscountConsistency CHECK (
    (DiscountCodeId IS NULL AND DiscountAmount = 0) OR (DiscountCodeId IS NOT NULL AND DiscountAmount > 0));
GO
