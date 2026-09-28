package com.keyloop.scheduler.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class Api {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public Api(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public record Response(int status, HttpHeaders headers, JsonNode body) {

        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    public Response post(String path, Object body) throws IOException, InterruptedException {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build());
    }

    public Response post(String path) throws IOException, InterruptedException {
        return send(request(path).POST(HttpRequest.BodyPublishers.noBody()).build());
    }

    public Response get(String path) throws IOException, InterruptedException {
        return send(request(path).GET().build());
    }

    public String getText(String path) throws IOException, InterruptedException {
        return http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15));
    }

    private Response send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        boolean isJson = response.headers().firstValue("Content-Type").filter(ct -> ct.contains("json")).isPresent();
        JsonNode json = isJson ? JSON.readTree(response.body()) : JSON.missingNode();
        return new Response(response.statusCode(), response.headers(), json);
    }
}
