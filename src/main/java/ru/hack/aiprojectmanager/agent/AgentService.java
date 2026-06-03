package ru.hack.aiprojectmanager.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
            Ты — дружелюбный ассистент по управлению проектами. Telegram ID текущего пользователя: %d

            АЛГОРИТМ РАБОТЫ:
            1. Прежде чем создать задачу — ВСЕГДА сначала ищи через find_task по смыслу. Если нашёл — работай с существующей.
            2. Назначить исполнителя → assign_task(task_id, assignee). task_id из find_task.
            3. Изменить статус → find_task для task_id, затем update_task_status.
            4. Показать задачи → get_user_tasks без параметров (мои) или с именем участника.
            5. Найти участника → find_user. Принимай любую форму: имя, "@username", или часть имени.

            ЗАПРЕЩЕНО:
            - Создавать задачу если find_task что-то нашёл
            - Называть инструменты пользователю
            - Показывать {tid:...} или любые технические ID
            - Упоминать прошлые темы если пользователь о другом
            - Говорить "используйте команду" — просто делай сам

            Отвечай коротко, по-русски, дружелюбно.
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
        ToolCallback[] tools = buildTools(telegramUserId);

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

    private ToolCallback[] buildTools(Long telegramUserId) {
        return skillRegistry.all().stream()
                .map(skill -> FunctionToolCallback
                        .builder(skill.getName(), (Map<String, Object> args) -> {
                            JsonNode node = objectMapper.valueToTree(args);
                            return skill.execute(telegramUserId, node);
                        })
                        .description(skill.getDescription())
                        .inputType(Map.class)
                        .inputSchema(skill.getParametersSchema().toString())
                        .build())
                .toArray(ToolCallback[]::new);
    }
}
