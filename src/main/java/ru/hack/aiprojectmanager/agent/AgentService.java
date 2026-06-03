package ru.hack.aiprojectmanager.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.hack.aiprojectmanager.agent.groq.GroqLLMClient;
import ru.hack.aiprojectmanager.agent.groq.dto.ChatMessage;
import ru.hack.aiprojectmanager.agent.groq.dto.ChatRequest;
import ru.hack.aiprojectmanager.agent.groq.dto.ChatResponse;
import ru.hack.aiprojectmanager.agent.groq.dto.Tool;
import ru.hack.aiprojectmanager.agent.groq.dto.ToolCall;
import ru.hack.aiprojectmanager.agent.skill.SkillRegistry;
import ru.hack.aiprojectmanager.storage.MessageHistory;
import ru.hack.aiprojectmanager.storage.MessageHistoryRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final int MAX_ITERATIONS = 5;
    //private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Ты — ассистент по управлению проектами. Помогай пользователю управлять задачами: создавать, менять статус, просматривать списки.
            Отвечай кратко и по делу на языке пользователя. При необходимости используй инструменты.
            Telegram ID текущего пользователя: %d
            """;

    private final GroqLLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final SkillRegistry skillRegistry;
    private final MessageHistoryRepository historyRepository;

    public String process(Long chatId, Long telegramUserId, String userMessage) {
        List<Tool> tools = buildTools();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM_PROMPT_TEMPLATE.formatted(telegramUserId)));
        messages.addAll(loadHistory(chatId));
        messages.add(ChatMessage.user(userMessage));

        String reply = "Не удалось завершить обработку запроса";

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            ChatResponse response = llmClient.chat(
                    new ChatRequest(llmClient.getModel(), messages, tools));

            ChatResponse.Choice choice = response.choices().getFirst();
            ChatMessage assistantMsg = choice.message();
            messages.add(assistantMsg);

            if ("stop".equals(choice.finishReason())) {
                reply = assistantMsg.getContent();
                break;
            }

            if ("tool_calls".equals(choice.finishReason()) && assistantMsg.getToolCalls() != null) {
                for (ToolCall toolCall : assistantMsg.getToolCalls()) {
                    String result = dispatch(chatId, toolCall);
                    messages.add(ChatMessage.toolResult(toolCall.id(), result));
                }
            }
        }

        saveHistory(chatId, telegramUserId, userMessage, reply);
        return reply;
    }

    private List<ChatMessage> loadHistory(Long chatId) {
        List<MessageHistory> recent = historyRepository.findTop20ByChatIdOrderByCreatedAtDesc(chatId);
        Collections.reverse(recent);
        return recent.stream()
                .map(h -> ChatMessage.builder().role(h.getRole()).content(h.getContent()).build())
                .toList();
    }

    private void saveHistory(Long chatId, Long telegramUserId, String userMessage, String reply) {
        historyRepository.save(MessageHistory.builder()
                .chatId(chatId)
                .telegramUserId(telegramUserId)
                .role("user")
                .content(userMessage)
                .build());
        historyRepository.save(MessageHistory.builder()
                .chatId(chatId)
                .role("assistant")
                .content(reply)
                .build());
    }

    private String dispatch(Long chatId, ToolCall toolCall) {
        return skillRegistry.find(toolCall.function().name())
                .map(skill -> {
                    try {
                        JsonNode args = objectMapper.readTree(toolCall.function().arguments());
                        return skill.execute(chatId, args);
                    } catch (JsonProcessingException e) {
                        return "Ошибка разбора аргументов: " + e.getMessage();
                    }
                })
                .orElse("Инструмент не найден: " + toolCall.function().name());
    }

    private List<Tool> buildTools() {
        return skillRegistry.all().stream()
                .map(s -> Tool.of(s.getName(), s.getDescription(), s.getParametersSchema()))
                .toList();
    }
}
