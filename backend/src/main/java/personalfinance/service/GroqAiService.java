package personalfinance.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class GroqAiService {

    private static final Logger log = LoggerFactory.getLogger(GroqAiService.class);
    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String primaryModel;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiReply {
        private boolean fromGroq;
        private String answer;
        private String model;
        private String errorReason;
    }

    public boolean isConfigured() {
        return groqApiKey != null && !groqApiKey.isBlank();
    }

    public AiReply chat(String systemPrompt, List<Map<String, Object>> history, String userQuestion) {
        if (!isConfigured()) {
            return AiReply.builder()
                    .fromGroq(false)
                    .errorReason("Chưa cấu hình GROQ_API_KEY")
                    .build();
        }

        List<String> modelsToTry = new ArrayList<>();
        if (primaryModel != null && !primaryModel.isBlank()) {
            modelsToTry.add(primaryModel.trim());
        }
        for (String fallback : List.of("openai/gpt-oss-120b", "qwen/qwen3.8-27b", "openai/gpt-oss-20b")) {
            if (!modelsToTry.contains(fallback)) {
                modelsToTry.add(fallback);
            }
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (history != null) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                Map<String, Object> h = history.get(i);
                if (h == null) continue;
                String role = String.valueOf(h.getOrDefault("role", "")).trim().toLowerCase(Locale.ROOT);
                String text = String.valueOf(h.getOrDefault("text", h.getOrDefault("content", ""))).trim();
                if (text.isEmpty()) continue;
                String mappedRole = ("user".equals(role)) ? "user" : "assistant";
                messages.add(Map.of("role", mappedRole, "content", text));
            }
        }
        messages.add(Map.of("role", "user", "content", userQuestion));

        String lastError = null;
        RestClient client = RestClient.create();

        for (String model : modelsToTry) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("model", model);
                payload.put("messages", messages);
                payload.put("temperature", 0.45);
                payload.put("max_tokens", 1600);

                @SuppressWarnings("unchecked")
                Map<String, Object> resp = client.post()
                        .uri(GROQ_CHAT_URL)
                        .header("Authorization", "Bearer " + groqApiKey.trim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(Map.class);

                if (resp != null && resp.get("choices") instanceof List<?> choices && !choices.isEmpty()) {
                    Object first = choices.get(0);
                    if (first instanceof Map<?, ?> choiceMap && choiceMap.get("message") instanceof Map<?, ?> msgMap) {
                        Object content = msgMap.get("content");
                        if (content != null) {
                            String cleaned = String.valueOf(content)
                                    .replaceAll("(?s)<think>.*?</think>", "")
                                    .trim();
                            if (!cleaned.isBlank()) {
                                return AiReply.builder()
                                        .fromGroq(true)
                                        .answer(cleaned)
                                        .model(model)
                                        .build();
                            }
                        }
                    }
                }
                lastError = "Groq trả về phản hồi rỗng";
            } catch (HttpClientErrorException.Unauthorized e) {
                log.warn("[GroqAI] Invalid GROQ_API_KEY (401 Unauthorized)");
                return AiReply.builder()
                        .fromGroq(false)
                        .errorReason("GROQ_API_KEY không hợp lệ hoặc đã bị thu hồi (401)")
                        .build();
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("[GroqAI] Model {} failed: {}", model, lastError);
            }
        }

        return AiReply.builder()
                .fromGroq(false)
                .errorReason(lastError != null ? lastError : "Không thể kết nối Groq API")
                .build();
    }
}
