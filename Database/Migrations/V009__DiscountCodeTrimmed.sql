-- ============================================================================
-- V009: discount codes are stored trimmed
--
-- The API looks a code up trimmed, so a stored code with surrounding whitespace could never be redeemed (V001 already requires it not
-- to be blank). Added WITH NOCHECK when an existing code breaks the rule and WITH CHECK otherwise, as in V008.
-- ============================================================================

IF EXISTS (SELECT 1 FROM DiscountCodes WHERE DATALENGTH(Code) <> DATALENGTH(LTRIM(RTRIM(Code))))
    ALTER TABLE DiscountCodes WITH NOCHECK ADD CONSTRAINT CK_DiscountCodes_Code_Trimmed CHECK (DATALENGTH(Code) = DATALENGTH(LTRIM(RTRIM(Code))));
ELSE
    ALTER TABLE DiscountCodes WITH CHECK ADD CONSTRAINT CK_DiscountCodes_Code_Trimmed CHECK (DATALENGTH(Code) = DATALENGTH(LTRIM(RTRIM(Code))));
GO
