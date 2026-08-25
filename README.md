# Langfuse Demo — Trợ lý hỗ trợ học viên Rikkei Academy

Ứng dụng Spring Boot minh hoạ tracing + monitoring bằng Langfuse cho một use case cụ thể:
**trợ lý trả lời câu hỏi thường gặp (FAQ) cho học viên** của nền tảng học trực tuyến Rikkei
Academy (đăng ký khoá học, học phí, hoàn tiền, chứng chỉ, gia hạn, hỗ trợ...).

Pipeline RAG thật (không giả lập): `RagService` tra cứu bộ FAQ nội bộ
([`FaqKnowledgeBase`](src/main/java/com/example/langfusedemo/faq/FaqKnowledgeBase.java)) theo
từ khoá trong câu hỏi (bước *retrieve*), rồi đưa các FAQ khớp nhất làm ngữ cảnh cho LLM diễn
giải lại thành câu trả lời tự nhiên (bước *generate*).

## Hai môi trường (Spring Profiles)

| Profile | LLM Provider | Dùng khi | Cần API key? |
|---|---|---|---|
| `local` | **Ollama** chạy trên máy | Phát triển/offline, không tốn phí | Không |
| `dev`   | **OpenRouter** (client tương thích OpenAI) | Test với model cloud thật | Có (`OPENROUTER_API_KEY`) |

Chọn provider bằng `spring.ai.model.chat` (`ollama` hoặc `openai`) trong từng file profile —
đây là property chính thức của Spring AI để giải quyết xung đột khi có nhiều model starter
cùng nằm trên classpath.

- [`application.yml`](src/main/resources/application.yml) — cấu hình chung (tracing, logging).
- [`application-local.yml`](src/main/resources/application-local.yml) — Ollama, loại bỏ toàn bộ
  auto-config của OpenAI (chat/embedding/image/audio/moderation) vì không dùng tới.
- [`application-dev.yml`](src/main/resources/application-dev.yml) — OpenRouter qua
  `spring.ai.openai.base-url=https://openrouter.ai/api`.

## Chuẩn bị

### 1. Local — cần Ollama chạy sẵn

```bash
brew install ollama        # nếu chưa có
ollama serve                # nếu chưa chạy như service
ollama pull llama3.2
```

### 2. Dev — cần API key OpenRouter

Lấy key thật tại https://openrouter.ai/keys (có gói miễn phí cho một số model).

### 3. Cả hai — cần project Langfuse thật để export trace

Tạo tài khoản/project miễn phí tại https://cloud.langfuse.com (không cần Docker), vào
**Settings → API Keys** để lấy `Public Key` và `Secret Key`.

```bash
export LANGFUSE_HOST=https://cloud.langfuse.com
export LANGFUSE_PUBLIC_KEY=pk-lf-xxxx
export LANGFUSE_SECRET_KEY=sk-lf-xxxx
export LANGFUSE_BASIC_AUTH=$(echo -n "$LANGFUSE_PUBLIC_KEY:$LANGFUSE_SECRET_KEY" | base64)
```

Xem [.env.local.example](.env.local.example) và [.env.dev.example](.env.dev.example) để biết
đầy đủ biến môi trường cho từng profile.

## Chạy ứng dụng

```bash
# Local (Ollama)
export LANGFUSE_HOST=... LANGFUSE_BASIC_AUTH=... LANGFUSE_PUBLIC_KEY=... LANGFUSE_SECRET_KEY=...
./gradlew bootRun --args='--spring.profiles.active=local'

# Dev (OpenRouter)
export OPENROUTER_API_KEY=... LANGFUSE_HOST=... LANGFUSE_BASIC_AUTH=... LANGFUSE_PUBLIC_KEY=... LANGFUSE_SECRET_KEY=...
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Có thể đặt `SPRING_PROFILES_ACTIVE=local|dev` thay cho `--spring.profiles.active` nếu chạy từ
jar đã build (`./gradlew bootJar`).

## API

| Endpoint | Mô tả | Tracing |
|---|---|---|
| `POST /api/chat` | Gọi thẳng LLM, không retrieval | 1 span đơn |
| `POST /api/chat/support` | Trợ lý FAQ Rikkei Academy (retrieve → generate) | nested span: `rag.pipeline` → `rag.retrieve` / `rag.generate` |
| `GET /actuator/health` | Health check | — |

Ví dụ:

```bash
curl -X POST http://localhost:8080/api/chat/support \
  -H "Content-Type: application/json" \
  -d '{"query": "Tôi muốn hủy khóa học và xin hoàn tiền thì làm thế nào?"}'
```

**Đã kiểm thử thật với Ollama (`llama3.2`) chạy local** — pipeline retrieve đúng FAQ
`refund-01` (hoàn tiền), LLM sinh câu trả lời dựa trên ngữ cảnh đó. Case lỗi (query rỗng) trả
về HTTP 500 kèm log mức `ERROR` thật (không mock).

## Đối chiếu log ↔ trace trên Langfuse

1. Gọi API, lấy `traceId` từ log JSON (Logback in ra `traceId`/`spanId` trong mọi dòng log
   nhờ Micrometer Tracing tự đưa vào MDC).
2. Vào Langfuse UI → **Traces** → search theo `traceId` đó.
3. Với `/api/chat/support`, sẽ thấy 3 span lồng nhau: `rag-pipeline` → `retrieve-context`,
   `generate-answer` (khai báo qua `@Observed` trong [RagService.java](src/main/java/com/example/langfusedemo/RagService.java)).

## Chấm điểm (score) và cảnh báo (alert) qua Langfuse REST API

- [`LangfuseScoreClient`](src/main/java/com/example/langfusedemo/LangfuseScoreClient.java) —
  gọi `POST /api/public/scores` để gắn điểm đánh giá (vd. `accuracy`) cho một trace cụ thể.
- [`AlertScheduler`](src/main/java/com/example/langfusedemo/AlertScheduler.java) — job
  `@Scheduled` mỗi 15 phút kiểm tra tỉ lệ trace có tag `error` trên 100 trace gần nhất, log
  `WARN` nếu vượt ngưỡng 5%.

Cả hai đều cần `LANGFUSE_HOST` + Basic Auth thật để hoạt động — không có logic giả lập response.

## Checklist bài tập

- [x] Spring Boot + Gradle, dependency Micrometer Tracing (OTel bridge) + OTLP exporter
- [x] Export trace qua OTLP tới Langfuse (`management.otlp.tracing.endpoint`)
- [x] Pipeline nhiều bước (`@Observed`) cho nested span — use case FAQ hỗ trợ học viên thật
- [x] Log JSON (Logback) chứa `traceId`/`spanId`
- [x] Case lỗi trả đúng HTTP 500 + log `ERROR` (đã test thật, không mock)
- [x] 2 profile `local` (Ollama) / `dev` (OpenRouter) — cấu hình đầy đủ, đã test compile + boot
- [x] Đã chạy thật `POST /api/chat/support` bằng Ollama local, xác nhận retrieval + generate đúng
- [ ] Kiểm tra trace/score/alert trên Langfuse UI thật — **cần bạn tự tạo project Langfuse Cloud**
      (bước tạo tài khoản là hành động cá nhân, không thể thực hiện thay bạn) rồi điền key vào
      `.env.local.example` / `.env.dev.example`
- [ ] Test end-to-end với OpenRouter thật — **cần `OPENROUTER_API_KEY` của bạn**
