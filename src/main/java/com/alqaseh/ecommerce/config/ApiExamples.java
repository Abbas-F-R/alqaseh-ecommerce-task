package com.alqaseh.ecommerce.config;

/**
 * JSON examples shown in Swagger UI. Every example is a copy of a real response of the running application
 * (only ids, timestamps and the token are shortened). {@code OpenApiExamplesTest} checks that each error example uses an
 * existing {@code ErrorCode} with the HTTP status and English message the application really returns.
 */
public final class ApiExamples {

    private ApiExamples() {
    }

    private static final String TS = "2026-09-29T09:15:30.123456Z";
    private static final String PRODUCT_ID = "01923450-0000-7000-8000-000000000003";
    private static final String ORDER_ID = "01a0e9c2-69b6-775d-ac30-998defcb766b";
    private static final String USER_ID = "01a0e9c1-9b85-7494-81a2-99852935d461";

    // ------------------------------------------------------------------ requests

    public static final String LOGIN_ADMIN = """
            {"username": "admin", "password": "Admin123!"}""";
    public static final String LOGIN_CUSTOMER = """
            {"username": "customer1", "password": "Customer123!"}""";

    public static final String PRODUCT_REQUEST = """
            {"name": "Smart Ultra Watch", "category": "electronics", "price": 250.00, "cost": 140.00, "availableQuantity": 20}""";

    public static final String ORDER_CARD_WITH_DISCOUNT = """
            {
              "items": [{"productId": "01923450-0000-7000-8000-000000000003", "quantity": 2}],
              "discountCode": "WELCOME10",
              "payment": {"method": "CreditCard", "cardNumber": "4111222233334444"}
            }""";
    public static final String ORDER_WALLET = """
            {
              "items": [
                {"productId": "01923450-0000-7000-8000-000000000001", "quantity": 1},
                {"productId": "01923450-0000-7000-8000-000000000004", "quantity": 3}
              ],
              "payment": {"method": "XyzWallet", "phoneNumber": "+9647801234567", "walletPassword": "walletSecret123"}
            }""";
    public static final String ORDER_DECLINED_CARD = """
            {
              "items": [{"productId": "01923450-0000-7000-8000-000000000003", "quantity": 1}],
              "payment": {"method": "CreditCard", "cardNumber": "0000000000000000"}
            }""";

    // ------------------------------------------------------------------ success responses

    public static final String LOGIN_OK = """
            {
              "timestamp": "%s",
              "success": true,
              "data": {"token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc1OTE0MDkzMH0.Xk2...", "username": "admin", "role": "ADMIN"}
            }""".formatted(TS);

    public static final String PRODUCT_ADMIN = """
            {
              "timestamp": "%s",
              "success": true,
              "data": {
                "id": "%s", "name": "Smart Ultra Watch", "category": "electronics", "price": 250.00, "cost": 140.00,
                "availableQuantity": 20, "createdBy": "%s", "createdAt": "%s", "updatedBy": "%s", "updatedAt": "%s"
              }
            }""".formatted(TS, PRODUCT_ID, USER_ID, TS, USER_ID, TS);

    public static final String PRODUCT_PAGE_ADMIN = """
            {
              "data": [{
                "id": "%s", "name": "Smartphone Pro Max", "category": "electronics", "price": 1200.00, "cost": 850.00,
                "availableQuantity": 25, "createdBy": null, "createdAt": "%s", "updatedBy": null, "updatedAt": null
              }],
              "pagesCount": 1,
              "currentPage": 0,
              "totalCount": 1,
              "isLast": true
            }""".formatted(PRODUCT_ID, TS);

    public static final String PRODUCT_PAGE_CUSTOMER = """
            {
              "data": [
                {"id": "%s", "name": "Smartphone Pro Max", "category": "electronics", "price": 1200.00, "stockStatus": "available"},
                {"id": "01923450-0000-7000-8000-000000000002", "name": "Oak Dining Table", "category": "furniture", "price": 450.00, "stockStatus": "low"}
              ],
              "nextCursor": null,
              "hasMore": false
            }""".formatted(PRODUCT_ID);

    public static final String PAGE_EMPTY_PRODUCTS = """
            {
              "data": [], "nextCursor": null, "hasMore": false
            }""";

    public static final String ORDER_CREATED = """
            {
              "timestamp": "%s",
              "success": true,
              "data": {
                "id": "%s", "totalPrice": 2390.00, "paymentMethod": "CreditCard", "purchaseDate": "%s", "discountAmount": 10.00,
                "items": [{"productId": "%s", "productName": "Smartphone Pro Max", "unitPrice": 1200.00, "quantity": 2, "subtotal": 2400.00}]
              }
            }""".formatted(TS, ORDER_ID, TS, PRODUCT_ID);

    public static final String MY_ORDERS_PAGE = """
            {
              "data": [{
                "id": "%s", "totalPrice": 2390.00, "paymentMethod": "CreditCard", "purchaseDate": "%s", "discountAmount": 10.00,
                "items": [{"productId": "%s", "productName": "Smartphone Pro Max", "unitPrice": 1200.00, "quantity": 2, "subtotal": 2400.00}]
              }],
              "pagesCount": 1, "currentPage": 0, "totalCount": 1, "isLast": true
            }""".formatted(ORDER_ID, TS, PRODUCT_ID);

    public static final String PAGE_EMPTY_ORDERS = PAGE_EMPTY_PRODUCTS;

    public static final String ADMIN_ORDERS_PAGE = """
            {
              "data": [{
                "id": "%s", "customerId": "%s", "customerUsername": "customer1",
                "subtotalAmount": 2400.00, "discountAmount": 10.00, "totalAmount": 2390.00, "totalCost": 1700.00, "profit": 690.00,
                "paymentMethod": "CreditCard", "purchaseDate": "%s",
                "items": [{"productId": "%s", "productName": "Smartphone Pro Max", "unitPrice": 1200.00, "quantity": 2, "subtotal": 2400.00}]
              }],
              "pagesCount": 1, "currentPage": 0, "totalCount": 1, "isLast": true
            }""".formatted(ORDER_ID, USER_ID, TS, PRODUCT_ID);

    // ------------------------------------------------------------------ error responses (ApiErrorResponse)

    public static String error(int status, String code, String message, String path) {
        return """
                {"timestamp": "%s", "status": %d, "code": "%s", "message": "%s", "path": "%s"}""".formatted(TS, status, code, message, path);
    }

    public static final String VALIDATION_LOGIN = """
            {
              "timestamp": "%s", "status": 400, "code": "VALIDATION_ERROR", "message": "Validation failed for one or more fields",
              "path": "/api/auth/login",
              "validationErrors": {"username": "Username cannot be blank", "password": "Password cannot be blank"}
            }""".formatted(TS);

    public static final String VALIDATION_PRODUCT = """
            {
              "timestamp": "%s", "status": 400, "code": "VALIDATION_ERROR", "message": "Validation failed for one or more fields",
              "path": "/api/products",
              "validationErrors": {
                "name": "Product name is required",
                "category": "Product category is required",
                "price": "Price must be greater than or equal to 0",
                "cost": "Cost is required",
                "availableQuantity": "Available quantity must be greater than or equal to 0"
              }
            }""".formatted(TS);

    public static String validationPagination(String path) {
        return """
                {
                  "timestamp": "%s", "status": 400, "code": "VALIDATION_ERROR", "message": "Validation failed for one or more fields",
                  "path": "%s",
                  "validationErrors": {"size": "Page size cannot exceed 50", "page": "Page number cannot be negative"}
                }""".formatted(TS, path);
    }

    public static String validationCursor(String path) {
        return """
                {
                  "timestamp": "%s", "status": 400, "code": "VALIDATION_ERROR", "message": "Validation failed for one or more fields",
                  "path": "%s",
                  "validationErrors": {"limit": "Limit cannot exceed 50"}
                }""".formatted(TS, path);
    }

    public static final String VALIDATION_ORDER = """
            {
              "timestamp": "%s", "status": 400, "code": "VALIDATION_ERROR", "message": "Validation failed for one or more fields",
              "path": "/api/orders",
              "validationErrors": {
                "items[0].quantity": "Quantity must be at least 1",
                "payment.cardNumberProvided": "Card number is required for CreditCard payments"
              }
            }""".formatted(TS);

    /** A query parameter that cannot be converted (unknown enum constant, malformed UUID, text instead of a number). */
    public static String validationInvalidValue(String path, String field) {
        return """
                {
                  "timestamp": "%s", "status": 400, "code": "VALIDATION_ERROR", "message": "Validation failed for one or more fields",
                  "path": "%s",
                  "validationErrors": {"%s": "Invalid value"}
                }""".formatted(TS, path, field);
    }

    public static String badRequest(String path) {
        return error(400, "BAD_REQUEST", "Invalid request parameters", path);
    }

    public static final String INVALID_CREDENTIALS = error(401, "INVALID_CREDENTIALS", "Invalid username or password", "/api/auth/login");
    public static final String UNAUTHORIZED = error(401, "UNAUTHORIZED", "Full authentication is required to access this resource", "/api/products");
    public static final String FORBIDDEN = error(403, "FORBIDDEN", "Access is denied. You do not have permission to perform this action", "/api/products");

    public static final String PRODUCT_NOT_FOUND_UPDATE = error(404, "PRODUCT_NOT_FOUND", "Product not found", "/api/products/01923450-0000-7000-8000-0000000000ff");
    public static final String PRODUCT_NOT_FOUND_ORDER = error(404, "PRODUCT_NOT_FOUND", "Product not found", "/api/orders");
    public static final String PRODUCT_NAME_EXISTS = error(409, "PRODUCT_NAME_ALREADY_EXISTS", "Product with this name already exists", "/api/products");
    public static final String CONCURRENT_MODIFICATION = error(409, "CONCURRENT_MODIFICATION", "The data was changed by another request. Please try again", "/api/orders");
    public static final String CONCURRENT_MODIFICATION_PRODUCT = error(409, "CONCURRENT_MODIFICATION", "The data was changed by another request. Please try again", "/api/products/01923450-0000-7000-8000-000000000003");

    public static final String INSUFFICIENT_STOCK = error(400, "INSUFFICIENT_STOCK", "Insufficient stock for product: Smartphone Pro Max", "/api/orders");
    public static final String DISCOUNT_NOT_FOUND = error(400, "DISCOUNT_NOT_FOUND", "Discount code not found", "/api/orders");
    public static final String DISCOUNT_EXPIRED = error(400, "DISCOUNT_EXPIRED", "Discount code is expired", "/api/orders");
    public static final String DISCOUNT_ALREADY_USED = error(400, "DISCOUNT_ALREADY_USED", "Discount code has already been used", "/api/orders");
    public static final String MINIMUM_ORDER_TOTAL_NOT_MET = error(400, "MINIMUM_ORDER_TOTAL_NOT_MET", "Order total does not meet the minimum required for this discount", "/api/orders");
    public static final String DISCOUNT_EXCEEDS_TOTAL = error(400, "DISCOUNT_EXCEEDS_TOTAL", "Discount amount cannot exceed the order total", "/api/orders");
    public static final String PAYMENT_FAILED = error(402, "PAYMENT_FAILED", "Payment processing failed: Transaction declined by card issuer", "/api/orders");

    public static final String INTERNAL_ERROR = error(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please contact system administrator", "/api/products");
}
