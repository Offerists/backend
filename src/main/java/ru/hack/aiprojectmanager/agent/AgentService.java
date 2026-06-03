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

import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

            Текущие дата и время: %s (таймзона %s).
            Относительные сроки («сегодня», «завтра», «через 2 часа») вычисляй от текущего времени
            и передавай в инструменты в формате ISO-8601 (yyyy-MM-ddTHH:mm).

            ВАЖНЫЕ ПРАВИЛА:
            - Никогда не показывай пользователю UUID, task_id и любые технические идентификаторы.
            - [task_id:...] в ответах инструментов — только для твоего внутреннего использования при вызове update_task_status.
            - Если нужно обновить статус задачи — сначала вызови get_user_tasks чтобы получить task_id, затем update_task_status.
            - Чтобы назначить задачу на человека по имени — сначала найди его через find_user и передай полученный telegram_id в поле assignee_telegram_id.
            - Не упоминай /start — это техническая команда, пользователь уже настроен.
            """;

    private static final DateTimeFormatter PROMPT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final ChatClient chatClient;
    private final SkillRegistry skillRegistry;
    private final MessageHistoryRepository historyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ZoneId zone;

    public AgentService(ChatClient.Builder chatClientBuilder,
                        SkillRegistry skillRegistry,
                        MessageHistoryRepository historyRepository,
                        @Value("${app.timezone}") String timezone) {
        this.chatClient = chatClientBuilder.build();
        this.skillRegistry = skillRegistry;
        this.historyRepository = historyRepository;
        this.zone = ZoneId.of(timezone);
    }

    public String process(Long chatId, Long telegramUserId, String userMessage) {
        List<Message> history = loadHistory(chatId);
        ToolCallback[] tools = buildTools(chatId);

        String reply;
        try {
            String now = LocalDateTime.now(zone).format(PROMPT_TIME_FORMAT);
            reply = chatClient.prompt()
                    .system(SYSTEM_PROMPT.formatted(telegramUserId, now, zone.getId()))
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
                            return invokeSkill(skill, chatId, node);
                        })
                        .description(skill.getDescription())
                        .inputType(Map.class)
                        .inputSchema(skill.getParametersSchema().toString())
                        .build())
                .toArray(ToolCallback[]::new);
    }

    /**
     * Выполняет скилл и превращает любой сбой в строку-наблюдение для модели,
     * а не в исключение: так LLM может переспросить или объяснить ошибку,
     * а не упасть в общий обработчик с немым «не смог обработать».
     */
    private String invokeSkill(Skill skill, Long chatId, JsonNode args) {
        log.info("Tool call '{}' chatId={} args={}", skill.getName(), chatId, args);
        try {
            return skill.execute(chatId, args);
        } catch (IllegalArgumentException e) {
            log.warn("Tool '{}' rejected args: {}", skill.getName(), e.getMessage());
            return "Ошибка: " + e.getMessage();
        } catch (Exception e) {
            log.error("Tool '{}' failed: {}", skill.getName(), e.getMessage(), e);
            return "Не удалось выполнить «" + skill.getName() + "»: " + e.getMessage();
        }
    }
}
