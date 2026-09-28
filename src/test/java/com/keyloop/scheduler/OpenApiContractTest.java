package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.keyloop.scheduler.support.Api;
import com.keyloop.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class OpenApiContractTest extends IntegrationTest {

    private static final Path COMMITTED_CONTRACT = Path.of("docs/openapi.yaml");
    private static final String PROBLEM_JSON = "application/problem+json";

    private JsonNode paths;

    @BeforeEach
    void loadContract() throws Exception {
        paths = api.get("/v3/api-docs").body().get("paths");
    }

    @Test
    void bookingDocumentsCreatedWithItsHeadersAndEveryError() {
        JsonNode responses = paths.get("/api/v1/appointments").get("post").get("responses");

        assertThat(responses.propertyNames()).containsExactlyInAnyOrder("201", "400", "404", "409", "422", "503");
        JsonNode created = responses.get("201");
        assertThat(created.get("headers").propertyNames()).contains("Location", "Idempotent-Replayed");
        assertThat(created.get("content").has("application/json")).isTrue();
    }

    @Test
    void everyOperationDocumentsErrorsAsProblemDetailsAndOverloadWithRetryAfter() {
        for (JsonNode path : paths) {
            for (JsonNode operation : path) {
                JsonNode responses = operation.get("responses");
                String id = operation.get("operationId").asString();
                assertThat(responses.has("400")).as("%s documents 400", id).isTrue();
                assertThat(responses.get("503").get("headers").has("Retry-After")).as("%s: Retry-After", id).isTrue();
                for (String code : responses.propertyNames()) {
                    if (code.startsWith("4") || code.startsWith("5")) {
                        assertThat(responses.get(code).get("content").propertyNames())
                                .as("%s %s media type", id, code).containsExactly(PROBLEM_JSON);
                    }
                }
            }
        }
    }

    @Test
    void operationsDocumentTheirSpecificErrors() {
        assertThat(responseCodes("/api/v1/appointments/{id}", "get")).containsExactlyInAnyOrder("200", "400", "404", "503");
        assertThat(responseCodes("/api/v1/appointments/{id}/cancel", "post"))
                .containsExactlyInAnyOrder("200", "400", "404", "409", "503");
        assertThat(responseCodes("/api/v1/dealerships/{dealershipId}/appointments", "get"))
                .containsExactlyInAnyOrder("200", "400", "404", "503");
        assertThat(responseCodes("/api/v1/dealerships/{dealershipId}/availability", "get"))
                .containsExactlyInAnyOrder("200", "400", "404", "503");
    }

    @Test
    void theCommittedContractMatchesTheCode() throws Exception {
        String generated = api.getText("/v3/api-docs.yaml");
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(COMMITTED_CONTRACT, generated);
        }

        assertThat(Files.readString(COMMITTED_CONTRACT))
                .as("docs/openapi.yaml is stale; regenerate it with ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true")
                .isEqualTo(generated);
    }

    private List<String> responseCodes(String path, String method) {
        return List.copyOf(paths.get(path).get(method).get("responses").propertyNames());
    }
}
