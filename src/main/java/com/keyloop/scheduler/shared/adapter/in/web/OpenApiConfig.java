package com.keyloop.scheduler.shared.adapter.in.web;

import java.util.Map;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(title = "Unified Service Scheduler API", version = "v1",
                description = "Books a service bay and a qualified technician for a service appointment. "
                        + "Errors are RFC 9457 application/problem+json."),
        servers = @Server(url = "/", description = "Same origin"))
class OpenApiConfig {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM_SCHEMA = "ProblemDetail";

    @Bean
    OpenApiCustomizer commonErrorResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(OpenApiConfig::addErrors));
        };
    }

    private static void addErrors(Operation operation) {
        ApiResponses responses = operation.getResponses();
        responses.putIfAbsent("400", new ApiResponse()
                .description("Malformed request: invalid parameter, body or header"));
        responses.putIfAbsent("503", new ApiResponse()
                .description("Temporarily overloaded; retry after the given delay")
                .headers(Map.of("Retry-After", new Header()
                        .description("Seconds to wait before retrying")
                        .schema(new IntegerSchema()))));
        responses.forEach((code, response) -> {
            if (code.startsWith("4") || code.startsWith("5")) {
                response.content(new Content().addMediaType(PROBLEM_JSON,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
            }
        });
    }

    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"));
    }
}
