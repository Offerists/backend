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
import ru.hack.aiprojectmanager.telegram.onboarding.OnboardingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateDispatcher {

    private final AgentService agentService;
    private final BotUserService botUserService;
    private final OnboardingService onboardingService;
    private final TelegramClient telegramClient;

    public void dispatch(Update update) throws TelegramApiException {
        if (update.hasMessage()) {
            handleMessage(update.getMessage());
        }
    }

    private void handleMessage(Message message) throws TelegramApiException {
        String text = message.getText();
        if (text == null) return;

        Long chatId = message.getChatId();
        try {
            if (text.startsWith("/")) {
                handleCommand(message, text.split(" ")[0]);
            } else {
                handleText(message);
            }
        } catch (TelegramApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in chatId={}: {}", chatId, e.getMessage(), e);
            sendReply(chatId, "Произошла ошибка. Попробуйте позже.");
        }
    }

    private void handleCommand(Message message, String command) throws TelegramApiException {
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();

        switch (command) {
            case "/start" -> {
                log.info("Command '/start' from userId={}", userId);
                botUserService.findOrRegister(userId, chatId,
                        message.getFrom().getUserName(), message.getFrom().getFirstName());
                if (onboardingService.needsOnboarding(chatId)) {
                    sendReply(chatId, onboardingService.start(chatId, userId));
                } else {
                    sendReply(chatId, "С возвращением! Чем могу помочь?");
                }
            }
            case "/help" -> sendReply(chatId, """
                    Доступные команды:
                    /start — начать работу / настроить интеграцию
                    /help — список команд

                    Просто напиши что нужно — например: "Создай задачу написать тесты".
                    """);
            default -> {
                log.warn("Unknown command '{}' from userId={}", command, userId);
                sendReply(chatId, "Неизвестная команда. Напиши /help для справки.");
            }
        }
    }

    private void handleText(Message message) throws TelegramApiException {
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();
        String text = message.getText();
        log.info("Message from userId={}, chatId={}", userId, chatId);

        if (onboardingService.isInProgress(chatId)) {
            sendReply(chatId, onboardingService.handle(chatId, text));
            return;
        }

        if (onboardingService.needsOnboarding(chatId)) {
            sendReply(chatId, "Сначала нужно настроить интеграцию. Введи /start");
            return;
        }

        sendReply(chatId, agentService.process(chatId, userId, text));
    }

    private void sendReply(Long chatId, String text) throws TelegramApiException {
        telegramClient.execute(SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build());
    }
}
