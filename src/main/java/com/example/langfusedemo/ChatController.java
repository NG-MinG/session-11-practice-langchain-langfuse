package com.example.langfusedemo;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MIN_ACCEPTABLE_LENGTH = 20;

    private final ChatClient chatClient;
    private final RagService ragService;
    private final LangfuseScoreClient scoreClient;
    private final Tracer tracer;

    public ChatController(ChatClient.Builder builder, RagService ragService,
                           LangfuseScoreClient scoreClient, Tracer tracer) {
        this.chatClient = builder.build();
        this.ragService = ragService;
        this.scoreClient = scoreClient;
        this.tracer = tracer;
    }

    /**
     * Gọi thẳng LLM không qua retrieval — dùng để so sánh chất lượng câu trả lời
     * và quan sát 1 span tracing đơn giản (không nested) trên Langfuse.
     */
    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        String answer = chatClient.prompt()
                .user(request.query())
                .call()
                .content();
        scoreAnswerLength(answer);
        return answer;
    }

    /**
     * Trợ lý hỗ trợ học viên Rikkei Academy: retrieve FAQ liên quan rồi generate câu trả lời.
     * Sinh ra pipeline nhiều bước (rag.pipeline -> rag.retrieve -> rag.generate) để quan sát
     * nested span trên Langfuse.
     */
    @PostMapping("/support")
    public String support(@RequestBody ChatRequest request) {
        String answer = ragService.pipeline(request.query());
        scoreAnswerLength(answer);
        return answer;
    }

    /**
     * Demo tự động chấm điểm: ghi score "auto_length_check" cho trace hiện tại dựa trên
     * độ dài câu trả lời — chỉ là heuristic minh hoạ, không phải đánh giá chất lượng thật.
     */
    private void scoreAnswerLength(String answer) {
        var currentSpan = tracer.currentSpan();
        if (currentSpan == null || answer == null) {
            return;
        }
        String traceId = currentSpan.context().traceId();
        double value = answer.length() >= MIN_ACCEPTABLE_LENGTH ? 1 : 0;
        String comment = "Độ dài câu trả lời: " + answer.length() + " ký tự";
        scoreClient.scoreTrace(traceId, "auto_length_check", value, comment);
    }

    public record ChatRequest(String query) {}
}
