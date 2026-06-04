package ru.hack.aiprojectmanager.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.hack.aiprojectmanager.agent.AgentService;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;
import ru.hack.aiprojectmanager.telegram.onboarding.OnboardingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateDispatcher {

    private final AgentService agentService;
    private final BotUserService botUserService;
    private final OnboardingService onboardingService;
    private final AppUserRepository appUserRepository;
    private final TelegramClient telegramClient;

    @Value("${telegram.bot.username}")
    private String botUsername;

    public void dispatch(Update update) throws TelegramApiException {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (message.getNewChatMembers() != null &&
                    message.getNewChatMembers().stream()
                            .anyMatch(u -> botUsername.equalsIgnoreCase(u.getUserName()))) {
                sendGroupWelcome(message.getChatId());
                return;
            }
            handleMessage(message);
        }
    }

    private void sendGroupWelcome(Long chatId) throws TelegramApiException {
        sendReply(chatId, """
                Привет! Я бот для управления задачами YouGile 👋

                Чтобы начать работу, каждый участник должен зарегистрироваться:
                1. Найдите меня в Telegram: @""" + botUsername + """

                2. Напишите мне /start в личных сообщениях
                3. Введите логин и пароль от YouGile

                После регистрации вы сможете управлять задачами прямо из этого чата — просто напишите мне @""" + botUsername + " и ваш запрос.");
    }

    private void handleMessage(Message message) throws TelegramApiException {
        String text = message.getText();
        if (text == null) return;

        Long chatId = message.getChatId();
        boolean isGroupChat = chatId < 0;

        // В группе реагируем только на команды и @упоминания
        if (isGroupChat && !isAddressedToBot(text)) {
            return;
        }

        // В группе убираем @username из текста перед обработкой
        String cleanText = isGroupChat ? removeMyMention(text) : text;

        try {
            if (cleanText.startsWith("/")) {
                handleCommand(message, cleanText.split(" ")[0], chatId);
            } else {
                handleText(message, chatId, cleanText);
            }
        } catch (TelegramApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in chatId={}: {}", chatId, e.getMessage(), e);
            sendReply(chatId, "Произошла ошибка. Попробуйте позже.");
        }
    }

    private boolean isAddressedToBot(String text) {
        if (text.startsWith("/")) return true;
        return text.toLowerCase().contains("@" + botUsername.toLowerCase());
    }

    private String removeMyMention(String text) {
        return text.replaceAll("(?i)@" + botUsername, "").trim();
    }

    private void handleCommand(Message message, String command, Long chatId) throws TelegramApiException {
        Long userId = message.getFrom().getId();

        switch (command) {
            case "/start" -> {
                log.info("Command '/start' from userId={}", userId);
                botUserService.findOrRegister(userId, chatId,
                        message.getFrom().getUserName(), message.getFrom().getFirstName());
                if (onboardingService.needsOnboarding(userId)) {
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

    private void handleText(Message message, Long chatId, String text) throws TelegramApiException {
        Long userId = message.getFrom().getId();
        log.info("Message from userId={}, chatId={}", userId, chatId);

        if (onboardingService.isInProgress(userId)) {
            sendReply(chatId, onboardingService.handle(userId, text));
            return;
        }

        if (onboardingService.needsOnboarding(userId)) {
            sendReply(chatId, "Сначала нужно настроить интеграцию — напиши мне в личку /start");
            return;
        }

        if (chatId < 0) {
            AppUser user = appUserRepository.findFirstByTelegramId(userId).orElse(null);
            if (user == null || !"LEAD".equals(user.getYougileRole())) {
                sendReply(chatId, "Управление задачами в группе доступно только лиду команды. "
                        + "Пиши мне в личку: @" + botUsername);
                return;
            }
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
