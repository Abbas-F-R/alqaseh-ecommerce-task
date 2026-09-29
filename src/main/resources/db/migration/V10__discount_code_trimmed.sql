-- =============================================================================
-- V10: discount codes are stored trimmed
--
-- The API looks a code up trimmed and upper-case, so a stored code with surrounding whitespace could never be redeemed
-- (V4 already requires it to be upper-case and not blank). Added NOT VALID and validated right away when the existing rows satisfy it,
-- as in V9: a database that already holds such a code keeps starting, and
--     ALTER TABLE discount_codes VALIDATE CONSTRAINT ck_discount_codes_code_trimmed;
-- validates it after the code is corrected.
-- =============================================================================

ALTER TABLE discount_codes ADD CONSTRAINT ck_discount_codes_code_trimmed CHECK (code = btrim(code)) NOT VALID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM discount_codes WHERE code <> btrim(code)) THEN
        ALTER TABLE discount_codes VALIDATE CONSTRAINT ck_discount_codes_code_trimmed;
    END IF;
END $$;
