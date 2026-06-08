package ru.hack.aiprojectmanager.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupContextService {

    private static final int SUMMARY_EVERY = 15;
    private static final int RECENT_LINES = 5;

    private final GroupContextRepository groupContextRepository;
    private final MessageHistoryRepository historyRepository;
    private final ChatClient chatClient;

    @Transactional
    public void saveMessage(Long chatId, Long telegramUserId, String senderName, String text) {
        historyRepository.save(MessageHistory.builder()
                .chatId(chatId)
                .telegramUserId(telegramUserId)
                .role("user")
                .content(senderName + ": " + text)
                .build());

        GroupContext ctx = groupContextRepository.findById(chatId)
                .orElse(GroupContext.builder().chatId(chatId).build());
        ctx.setTotalMessages(ctx.getTotalMessages() + 1);
        groupContextRepository.save(ctx);

        if (ctx.getTotalMessages() % SUMMARY_EVERY == 0) {
            regenerateSummary(ctx);
        }
    }

    public String buildContextBlock(Long chatId) {
        GroupContext ctx = groupContextRepository.findById(chatId).orElse(null);

        List<MessageHistory> recent = historyRepository.findTop20ByChatIdOrderByCreatedAtDesc(chatId);
        Collections.reverse(recent);
        List<String> lines = recent.stream()
                .map(MessageHistory::getContent)
                .collect(Collectors.toList());

        if (lines.size() > RECENT_LINES) {
            lines = lines.subList(lines.size() - RECENT_LINES, lines.size());
        }

        StringBuilder sb = new StringBuilder("\n\n════════════════════════════════\nКОНТЕКСТ ГРУППОВОГО ЧАТА\n════════════════════════════════\n");

        if (ctx != null && ctx.getSummary() != null) {
            sb.append("Саммари беседы: ").append(ctx.getSummary()).append("\n\n");
        }

        if (!lines.isEmpty()) {
            sb.append("Последние сообщения:\n");
            lines.forEach(l -> sb.append(l).append("\n"));
        }

        return sb.toString();
    }

    private void regenerateSummary(GroupContext ctx) {
        List<MessageHistory> messages = historyRepository.findTop20ByChatIdOrderByCreatedAtDesc(ctx.getChatId());
        if (messages.isEmpty()) return;

        Collections.reverse(messages);
        String messagesText = messages.stream()
                .map(MessageHistory::getContent)
                .collect(Collectors.joining("\n"));

        String prev = ctx.getSummary() != null ? "Предыдущий контекст: " + ctx.getSummary() + "\n\n" : "";
        String prompt = prev + "Сожми следующий групповой чат команды в 2-3 предложения. "
                + "Сохрани: кто о каких задачах говорил, какие решения приняли, кто что взял. "
                + "Без приветствий, только суть.\n\n" + messagesText;

        try {
            String summary = chatClient.prompt().user(prompt).call().content();
            ctx.setSummary(summary);
            groupContextRepository.save(ctx);
            log.info("Group summary regenerated for chatId={}", ctx.getChatId());
        } catch (Exception e) {
            log.warn("Failed to regenerate group summary for chatId={}: {}", ctx.getChatId(), e.getMessage());
        }
    }
}
