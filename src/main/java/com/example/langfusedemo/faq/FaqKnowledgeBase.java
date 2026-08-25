package com.example.langfusedemo.faq;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Bộ tri thức FAQ tĩnh cho trợ lý hỗ trợ học viên của "Rikkei Academy".
 * Đóng vai trò tầng "retrieve" của pipeline RAG — thay cho một vector store thật
 * (Qdrant/PGVector/...) để bài tập tập trung vào tracing/monitoring thay vì hạ tầng RAG.
 */
@Component
public class FaqKnowledgeBase {

    private final List<FaqEntry> entries = List.of(
            new FaqEntry(
                    "enroll-01", "enrollment",
                    List.of("đăng ký", "ghi danh", "khóa học", "tham gia"),
                    "Làm sao để đăng ký một khóa học trên Rikkei Academy?",
                    "Học viên đăng nhập vào rikkei-academy.vn, chọn khóa học mong muốn, bấm 'Đăng ký' " +
                            "và hoàn tất thanh toán. Sau khi thanh toán thành công, khóa học sẽ xuất hiện " +
                            "trong mục 'Khóa học của tôi' trong vòng tối đa 5 phút."
            ),
            new FaqEntry(
                    "payment-01", "payment",
                    List.of("thanh toán", "học phí", "trả góp", "giá", "chi phí"),
                    "Rikkei Academy hỗ trợ những hình thức thanh toán nào?",
                    "Chúng tôi hỗ trợ thanh toán qua thẻ ngân hàng nội địa/quốc tế, ví điện tử (MoMo, ZaloPay) " +
                            "và chuyển khoản trực tiếp. Với các khóa học từ 5 triệu đồng trở lên, học viên có thể " +
                            "chọn trả góp 0% qua thẻ tín dụng trong 3-6 tháng."
            ),
            new FaqEntry(
                    "refund-01", "refund",
                    List.of("hoàn tiền", "hủy", "refund", "đổi trả"),
                    "Chính sách hoàn tiền của Rikkei Academy như thế nào?",
                    "Học viên được hoàn 100% học phí nếu yêu cầu trong vòng 7 ngày kể từ ngày đăng ký và " +
                            "đã học chưa quá 20% nội dung khóa học. Sau mốc này, học phí không được hoàn lại " +
                            "trừ trường hợp khóa học bị hủy bởi Rikkei Academy."
            ),
            new FaqEntry(
                    "certificate-01", "certificate",
                    List.of("chứng chỉ", "certificate", "hoàn thành", "cấp bằng"),
                    "Điều kiện để nhận chứng chỉ hoàn thành khóa học là gì?",
                    "Học viên cần hoàn thành tối thiểu 90% bài giảng và đạt điểm trung bình từ 70% trở lên " +
                            "ở các bài kiểm tra cuối chương. Chứng chỉ điện tử sẽ được cấp tự động trong " +
                            "mục 'Chứng chỉ của tôi' trong vòng 24 giờ sau khi đủ điều kiện."
            ),
            new FaqEntry(
                    "deadline-01", "deadline",
                    List.of("gia hạn", "hạn nộp bài", "deadline", "thời hạn"),
                    "Có thể xin gia hạn thời gian truy cập khóa học không?",
                    "Học viên có thể xin gia hạn quyền truy cập thêm tối đa 30 ngày, miễn phí 1 lần cho mỗi " +
                            "khóa học, bằng cách gửi yêu cầu qua mục 'Hỗ trợ' trước khi khóa học hết hạn 3 ngày."
            ),
            new FaqEntry(
                    "support-01", "support",
                    List.of("hỗ trợ", "liên hệ", "giảng viên", "mentor"),
                    "Khi gặp khó khăn trong bài học, học viên liên hệ ai để được hỗ trợ?",
                    "Học viên có thể đặt câu hỏi trực tiếp trong phần 'Thảo luận' của từng bài giảng để giảng " +
                            "viên/mentor phản hồi trong vòng 24 giờ làm việc, hoặc liên hệ tổng đài hỗ trợ " +
                            "1900-xxxx trong giờ hành chính."
            )
    );

    public List<FaqEntry> all() {
        return entries;
    }

    /**
     * Tìm các FAQ liên quan nhất theo số từ khóa trùng khớp với câu hỏi của học viên.
     * Đây là bước "retrieve" thật (không phải chuỗi giả lập) — đơn giản hoá thay cho
     * similarity search trên vector store.
     */
    public List<FaqEntry> search(String query, int topK) {
        String normalizedQuery = normalize(query);
        return entries.stream()
                .map(entry -> new ScoredEntry(entry, score(entry, normalizedQuery)))
                .filter(scored -> scored.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .limit(topK)
                .map(ScoredEntry::entry)
                .toList();
    }

    private int score(FaqEntry entry, String normalizedQuery) {
        int score = 0;
        for (String keyword : entry.keywords()) {
            if (normalizedQuery.contains(normalize(keyword))) {
                score++;
            }
        }
        return score;
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.forLanguageTag("vi-VN"));
    }

    private record ScoredEntry(FaqEntry entry, int score) {
    }
}
