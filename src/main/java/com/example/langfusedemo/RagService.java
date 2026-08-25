package com.example.langfusedemo;

import com.example.langfusedemo.faq.FaqEntry;
import com.example.langfusedemo.faq.FaqKnowledgeBase;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Trợ lý hỗ trợ học viên Rikkei Academy: trả lời câu hỏi về đăng ký khóa học,
 * học phí, hoàn tiền, chứng chỉ... dựa trên bộ FAQ nội bộ (retrieve) rồi để
 * LLM diễn giải lại thành câu trả lời tự nhiên (generate).
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 3;

    private final ChatClient chatClient;
    private final FaqKnowledgeBase faqKnowledgeBase;

    public RagService(ChatClient.Builder builder, FaqKnowledgeBase faqKnowledgeBase) {
        this.chatClient = builder.build();
        this.faqKnowledgeBase = faqKnowledgeBase;
    }

    @Observed(name = "rag.retrieve", contextualName = "retrieve-context")
    public String retrieveContext(String query) {
        List<FaqEntry> matches = faqKnowledgeBase.search(query, TOP_K);
        if (matches.isEmpty()) {
            log.info("Không tìm thấy FAQ khớp với câu hỏi='{}'", query);
            return "Không có FAQ nào khớp trực tiếp với câu hỏi này.";
        }
        log.info("Tìm thấy {} FAQ khớp với câu hỏi='{}': {}", matches.size(), query,
                matches.stream().map(FaqEntry::id).collect(Collectors.joining(", ")));
        return matches.stream()
                .map(entry -> "- Q: %s\n  A: %s".formatted(entry.question(), entry.answer()))
                .collect(Collectors.joining("\n"));
    }

    @Observed(name = "rag.generate", contextualName = "generate-answer")
    public String generateAnswer(String query, String context) {
        log.info("Gọi LLM với query='{}'", query);
        try {
            String answer = chatClient.prompt()
                    .system("""
                            Bạn là trợ lý hỗ trợ học viên của Rikkei Academy. Chỉ trả lời dựa trên các FAQ
                            được cung cấp dưới đây, diễn đạt lại tự nhiên, ngắn gọn, đúng trọng tâm câu hỏi.
                            Nếu FAQ không đề cập tới nội dung được hỏi, trả lời rằng bạn chưa có thông tin
                            và hướng dẫn học viên liên hệ tổng đài hỗ trợ 1900-xxxx.

                            FAQ liên quan:
                            %s
                            """.formatted(context))
                    .user(query)
                    .call()
                    .content();
            log.info("LLM trả kết quả thành công, độ dài={}", answer.length());
            return answer;
        } catch (Exception e) {
            log.error("Lỗi khi gọi LLM: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Observed(name = "rag.pipeline", contextualName = "rag-pipeline")
    public String pipeline(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query không được để trống");
        }
        String context = retrieveContext(query);
        return generateAnswer(query, context);
    }
}
