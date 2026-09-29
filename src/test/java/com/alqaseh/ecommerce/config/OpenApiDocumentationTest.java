package com.alqaseh.ecommerce.config;

import com.alqaseh.ecommerce.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The published OpenAPI document must describe exactly the API the assignment asks for, with usable schemas and examples
 * that agree with the real error codes, statuses and messages.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MessageSource messages;

    private JsonNode api;

    @BeforeEach
    void loadDocument() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        api = objectMapper.readTree(body);
    }

    private List<String> operations() {
        List<String> result = new ArrayList<>();
        api.get("paths").fields().forEachRemaining(path -> path.getValue().fieldNames()
                .forEachRemaining(method -> result.add(method.toUpperCase() + " " + path.getKey())));
        return result;
    }

    @Test
    @DisplayName("Only the endpoints required by the assignment are published (in particular no DELETE)")
    void exactEndpointSet() {
        assertThat(operations()).containsExactlyInAnyOrder(
                "POST /api/auth/login",
                "GET /api/admin/products", "GET /api/customer/products", "POST /api/products", "PUT /api/products/{id}",
                "POST /api/orders", "GET /api/orders", "GET /api/orders/my");
        assertThat(operations()).noneMatch(op -> op.startsWith("DELETE") || op.startsWith("PATCH"));
    }

    @Test
    @DisplayName("Each role has its own documentation group with only its endpoints")
    void roleGroups() throws Exception {
        assertThat(groupOperations("1-admin")).containsExactlyInAnyOrder("POST /api/auth/login",
                "GET /api/admin/products", "POST /api/products", "PUT /api/products/{id}", "GET /api/orders");
        assertThat(groupOperations("2-customer")).containsExactlyInAnyOrder("POST /api/auth/login",
                "GET /api/customer/products", "POST /api/orders", "GET /api/orders/my");
    }

    @Test
    @DisplayName("Each role has its own product list endpoint with its own response schema")
    void productListPerRole() throws Exception {
        for (String[] g : new String[][]{{"1-admin", "admin", "PageResponseAdminProductResponse"}, {"2-customer", "customer", "CursorResponseCustomerProductResponse"}}) {
            JsonNode doc = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs/" + g[0])).andReturn().getResponse().getContentAsString());
            JsonNode schema = doc.at("/paths/~1api~1" + g[1] + "~1products/get/responses/200/content/application~1json/schema");
            assertThat(schema.get("$ref").asText()).isEqualTo("#/components/schemas/" + g[2]);
        }
    }

    private List<String> groupOperations(String group) throws Exception {
        JsonNode doc = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs/" + group)).andReturn().getResponse().getContentAsString());
        List<String> result = new ArrayList<>();
        doc.get("paths").fields().forEachRemaining(path -> path.getValue().fieldNames()
                .forEachRemaining(method -> result.add(method.toUpperCase() + " " + path.getKey())));
        return result;
    }

    @Test
    @DisplayName("Security: login is public, every other operation requires the bearer token; the scheme exists")
    void security() {
        assertThat(api.has("security")).as("no global requirement (it would mark login as secured)").isFalse();
        assertThat(api.at("/components/securitySchemes/Bearer Authentication/scheme").asText()).isEqualTo("bearer");
        api.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            boolean login = path.getKey().equals("/api/auth/login");
            assertThat(method.getValue().has("security")).as(method.getKey() + " " + path.getKey()).isEqualTo(!login);
        }));
    }

    @Test
    @DisplayName("Query parameters of the list endpoints are expanded (name, category, limit, cursor, page, size ...), not one opaque object")
    void listParametersAreExpanded() {
        assertThat(parameterNames("/api/admin/products", "get")).containsExactlyInAnyOrder("name", "category", "page", "size");
        assertThat(parameterNames("/api/customer/products", "get")).containsExactlyInAnyOrder("name", "category", "limit", "cursor");
        assertThat(parameterNames("/api/orders", "get")).containsExactlyInAnyOrder("customer", "customerId", "paymentMethod", "page", "size");
        assertThat(parameterNames("/api/orders/my", "get")).containsExactlyInAnyOrder("page", "size");
        assertThat(api.at("/paths/~1api~1orders/get/parameters").findValues("name")).extracting(JsonNode::asText).doesNotContain("filter");
        JsonNode limitParam = java.util.stream.StreamSupport.stream(api.at("/paths/~1api~1customer~1products/get/parameters").spliterator(), false)
                .filter(p -> "limit".equals(p.get("name").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(limitParam.at("/schema/maximum").asInt()).isEqualTo(50);
    }

    private Set<String> parameterNames(String path, String method) {
        Set<String> names = new TreeSet<>();
        api.get("paths").get(path).get(method).get("parameters").forEach(p -> names.add(p.get("name").asText()));
        return names;
    }

    @Test
    @DisplayName("Documented status codes per endpoint (only cases the API really produces)")
    void documentedStatuses() {
        assertThat(statuses("/api/auth/login", "post")).containsExactly("200", "400", "401");
        assertThat(statuses("/api/products", "post")).containsExactly("201", "400", "409");
        assertThat(statuses("/api/products/{id}", "put")).containsExactly("200", "400", "404", "409");
        assertThat(statuses("/api/admin/products", "get")).containsExactly("200", "400");
        assertThat(statuses("/api/customer/products", "get")).containsExactly("200", "400");
        assertThat(statuses("/api/orders", "post")).containsExactly("201", "400", "402", "404", "409");
        assertThat(statuses("/api/orders/my", "get")).containsExactly("200", "400");
        assertThat(statuses("/api/orders", "get")).containsExactly("200", "400");
    }

    private List<String> statuses(String path, String method) {
        List<String> result = new ArrayList<>();
        api.get("paths").get(path).get(method).get("responses").fieldNames().forEachRemaining(result::add);
        return result;
    }

    @Test
    @DisplayName("Every documented response has a typed schema and at least one non-empty example; every request body has examples")
    void schemasAndExamplesArePresent() {
        api.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            String operation = method.getKey().toUpperCase() + " " + path.getKey();
            method.getValue().get("responses").fields().forEachRemaining(response -> {
                JsonNode content = response.getValue().at("/content/application~1json");
                assertThat(content.has("schema")).as(operation + " " + response.getKey() + " schema").isTrue();
                assertThat(content.get("examples")).as(operation + " " + response.getKey() + " examples").isNotNull();
                content.get("examples").fields().forEachRemaining(example ->
                        assertThat(example.getValue().get("value").size()).as(operation + " " + example.getKey()).isGreaterThan(0));
            });
            if (method.getValue().has("requestBody")) {
                assertThat(method.getValue().at("/requestBody/content/application~1json/examples").size()).as(operation + " request examples").isGreaterThan(0);
            }
        }));
        // typed envelopes instead of a bare "object"
        assertThat(api.at("/paths/~1api~1products/post/responses/201/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ApiResponseAdminProductResponse");
        assertThat(api.at("/paths/~1api~1orders/post/responses/201/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ApiResponseCustomerOrderResponse");
        assertThat(api.at("/paths/~1api~1orders/get/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/PageResponseAdminOrderResponse");
        assertThat(api.at("/paths/~1api~1admin~1products/get/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/PageResponseAdminProductResponse");
        assertThat(api.at("/paths/~1api~1customer~1products/get/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/CursorResponseCustomerProductResponse");
    }

    @Test
    @DisplayName("Error examples use existing ErrorCodes with the real HTTP status and the real English message")
    void errorExamplesMatchTheApplication() {
        List<String> checked = new ArrayList<>();
        api.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method ->
                method.getValue().get("responses").fields().forEachRemaining(response -> {
                    int status = Integer.parseInt(response.getKey());
                    if (status < 400) {
                        return;
                    }
                    response.getValue().at("/content/application~1json/examples").fields().forEachRemaining(example -> {
                        JsonNode body = example.getValue().get("value");
                        ErrorCode code = ErrorCode.valueOf(body.get("code").asText());
                        assertThat(code.getHttpStatus().value()).as(example.getKey()).isEqualTo(status).isEqualTo(body.get("status").asInt());
                        String template = messages.getMessage(code.getMessageKey(), null, Locale.ENGLISH);
                        String fixedPart = template.contains("{") ? template.substring(0, template.indexOf('{')) : template;
                        assertThat(body.get("message").asText()).as(example.getKey()).startsWith(fixedPart);
                        assertThat(body.get("path").asText()).startsWith("/api/");
                        if (code == ErrorCode.VALIDATION_ERROR) {
                            assertThat(body.get("validationErrors").size()).isGreaterThan(0);
                        }
                        checked.add(code.name());
                    });
                })));
        assertThat(new TreeSet<>(checked)).contains(
                "VALIDATION_ERROR", "INVALID_CREDENTIALS", "PRODUCT_NOT_FOUND", "PRODUCT_NAME_ALREADY_EXISTS",
                "CONCURRENT_MODIFICATION", "INSUFFICIENT_STOCK", "PAYMENT_FAILED");
    }
}
