package ru.hack.aiprojectmanager.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.hack.aiprojectmanager.agent.AgentService;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateDispatcher {

    private final AgentService agentService;
    private final BotUserService botUserService;
    private final TelegramClient telegramClient;

    public void dispatch(Update update) throws TelegramApiException {
        if (update.hasMessage()) {
            handleMessage(update.getMessage());
        }
    }

    private void handleMessage(Message message) throws TelegramApiException {
        String text = message.getText();
        if (text == null) return;

        if (text.startsWith("/")) {
            String command = text.split(" ")[0];
            log.info("Command '{}' from userId={}", command, message.getFrom().getId());
            handleCommand(message, command);
        } else {
            Long chatId = message.getChatId();
            Long userId = message.getFrom().getId();
            log.info("Message from userId={}, chatId={}", userId, chatId);
            String reply = agentService.process(chatId, userId, text);
            sendReply(chatId, reply);
        }
    }

    private void handleCommand(Message message, String command) throws TelegramApiException {
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();

        switch (command) {
            case "/start" -> {
                botUserService.findOrRegister(
                        userId, chatId,
                        message.getFrom().getUserName(),
                        message.getFrom().getFirstName()
                );
                sendReply(chatId, "Привет! Я твой ассистент по управлению задачами. Чем могу помочь?");
            }
            case "/help" -> sendReply(chatId, """
                    Доступные команды:
                    /start — начать работу
                    /help — список команд

                    Просто напиши мне что нужно сделать, и я помогу управлять задачами.
                    """);
            default -> {
                log.warn("Unknown command '{}' from userId={}", command, userId);
                sendReply(chatId, "Неизвестная команда. Напиши /help для списка команд.");
            }
        }
    }

    private void sendReply(Long chatId, String text) throws TelegramApiException {
        telegramClient.execute(SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build());
    }
}
