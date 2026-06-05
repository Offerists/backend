package ru.hack.aiprojectmanager.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.hack.aiprojectmanager.notification.NotificationSender;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramNotificationSender implements NotificationSender {

    private final TelegramClient telegramClient;

    @Override
    public void send(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to send notification to chatId={}: {}", chatId, e.getMessage());
        }
    }
}
