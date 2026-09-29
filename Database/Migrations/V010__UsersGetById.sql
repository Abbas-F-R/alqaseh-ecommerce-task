-- ============================================================================
-- V010: user lookup by id, used to check the account behind every authenticated request (UserContextMiddleware)
-- ============================================================================

CREATE OR ALTER PROCEDURE UsersGetById
    @Id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SELECT Id, FullName, UserName, PasswordHash, Role, CreatedAt
    FROM Users
    WHERE Id = @Id;
END
GO
