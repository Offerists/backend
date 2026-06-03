package ru.hack.aiprojectmanager.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import ru.hack.aiprojectmanager.agent.skill.Skill;
import ru.hack.aiprojectmanager.agent.skill.SkillRegistry;
import ru.hack.aiprojectmanager.storage.MessageHistory;
import ru.hack.aiprojectmanager.storage.MessageHistoryRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = """
            Ты — ассистент по управлению проектами. Помогай пользователю управлять задачами: создавать, менять статус, просматривать списки.
            Отвечай кратко и по делу на языке пользователя. При необходимости используй инструменты.
            Telegram ID текущего пользователя: %d

            ВАЖНЫЕ ПРАВИЛА:
            - Никогда не показывай пользователю UUID, task_id и любые технические идентификаторы.
            - [task_id:...] в ответах инструментов — только для твоего внутреннего использования при вызове update_task_status.
            - Если нужно обновить статус задачи — сначала вызови get_user_tasks чтобы получить task_id, затем update_task_status.
            - Не упоминай /start — это техническая команда, пользователь уже настроен.
            """;

    private final ChatClient chatClient;
    private final SkillRegistry skillRegistry;
    private final MessageHistoryRepository historyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentService(ChatClient.Builder chatClientBuilder,
                        SkillRegistry skillRegistry,
                        MessageHistoryRepository historyRepository) {
        this.chatClient = chatClientBuilder.build();
        this.skillRegistry = skillRegistry;
        this.historyRepository = historyRepository;
    }

    public String process(Long chatId, Long telegramUserId, String userMessage) {
        List<Message> history = loadHistory(chatId);
        ToolCallback[] tools = buildTools(chatId);

        String reply;
        try {
            reply = chatClient.prompt()
                    .system(SYSTEM_PROMPT.formatted(telegramUserId))
                    .messages(history)
                    .user(userMessage)
                    .toolCallbacks(tools)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM error: {}", e.getMessage(), e);
            reply = "Не смог обработать запрос. Попробуй переформулировать.";
        }

        if (reply == null) reply = "Не удалось получить ответ.";
        saveHistory(chatId, telegramUserId, userMessage, reply);
        return reply;
    }

    private List<Message> loadHistory(Long chatId) {
        List<MessageHistory> recent = historyRepository.findTop20ByChatIdOrderByCreatedAtDesc(chatId);
        Collections.reverse(recent);
        return recent.stream()
                .map(h -> (Message) switch (h.getRole()) {
                    case "user" -> new UserMessage(h.getContent());
                    case "assistant" -> new AssistantMessage(h.getContent());
                    default -> null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private void saveHistory(Long chatId, Long telegramUserId, String userMessage, String reply) {
        historyRepository.save(MessageHistory.builder()
                .chatId(chatId).telegramUserId(telegramUserId)
                .role("user").content(userMessage).build());
        historyRepository.save(MessageHistory.builder()
                .chatId(chatId).role("assistant").content(reply).build());
    }

    private ToolCallback[] buildTools(Long chatId) {
        return skillRegistry.all().stream()
                .map(skill -> FunctionToolCallback
                        .builder(skill.getName(), (Map<String, Object> args) -> {
                            JsonNode node = objectMapper.valueToTree(args);
                            return skill.execute(chatId, node);
                        })
                        .description(skill.getDescription())
                        .inputType(Map.class)
                        .inputSchema(skill.getParametersSchema().toString())
                        .build())
                .toArray(ToolCallback[]::new);
    }
}
