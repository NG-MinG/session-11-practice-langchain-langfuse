package com.example.langfusedemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Service
public class LangfuseScoreClient {

    private static final Logger log = LoggerFactory.getLogger(LangfuseScoreClient.class);

    private final WebClient webClient;

    public LangfuseScoreClient(
            @Value("${LANGFUSE_HOST:}") String host,
            @Value("${LANGFUSE_PUBLIC_KEY:}") String publicKey,
            @Value("${LANGFUSE_SECRET_KEY:}") String secretKey) {

        String auth = Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes());

        this.webClient = WebClient.builder()
                .baseUrl(host)
                .defaultHeader("Authorization", "Basic " + auth)
                .build();
    }

    public void scoreTrace(String traceId, String name, double value, String comment) {
        if (traceId == null || traceId.isBlank()) {
            log.warn("Bỏ qua ghi score '{}': không có traceId hợp lệ trong context hiện tại", name);
            return;
        }
        webClient.post()
                .uri("/api/public/scores")
                .bodyValue(Map.of(
                        "traceId", traceId,
                        "name", name,
                        "value", value,
                        "comment", comment
                ))
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class).defaultIfEmpty("")
                                .doOnNext(body -> log.warn("Không ghi được score '{}' cho trace {}: HTTP {} - {}",
                                        name, traceId, response.statusCode().value(), body))
                                .then(Mono.empty());
                    }
                    return response.releaseBody();
                })
                .onErrorResume(e -> {
                    log.warn("Không ghi được score '{}' cho trace {}: {}", name, traceId, e.getMessage());
                    return Mono.empty();
                })
                .block();
    }
}
