package com.cyberflow.admin.newsite.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageGenerationClientTest {
    @Test
    void appendsTheImageGenerationPathToAnApiBaseUrl() {
        assertEquals("https://api.openai.com/v1/images/generations",
                ImageGenerationClient.generationsUrl("https://api.openai.com/v1/"));
        assertEquals("https://images.example.test/images/generations",
                ImageGenerationClient.generationsUrl("https://images.example.test/images/generations"));
    }

    @Test
    void rejectsAnEmptyBaseUrlBeforeMakingARequest() {
        assertThrows(IllegalStateException.class, () -> ImageGenerationClient.generationsUrl(" "));
    }

    @Test
    void sendsAnImageApiRequestAndDecodesTheBase64Result() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/images/generations", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("{\"data\":[{\"b64_json\":\""
                    + Base64.getEncoder().encodeToString("png-bytes".getBytes(StandardCharsets.UTF_8))
                    + "\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var image = new ImageGenerationClient().generate(new ImageGenerationClient.Request(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "secret", "image-model",
                    "medium", "logo prompt", "1024x1024", true));
            assertArrayEquals("png-bytes".getBytes(StandardCharsets.UTF_8), image.bytes());
            assertEquals("Bearer secret", authorization.get());
            assertTrue(body.get().contains("\"background\":\"transparent\""));
            assertTrue(body.get().contains("\"size\":\"1024x1024\""));
        } finally {
            server.stop(0);
        }
    }
}
