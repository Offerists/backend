package ru.hack.aiprojectmanager.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.hack.aiprojectmanager.agent.AgentService;
import ru.hack.aiprojectmanager.agent.GroupContextService;
import ru.hack.aiprojectmanager.notification.DigestScheduler;
import ru.hack.aiprojectmanager.stt.SttProvider;
import ru.hack.aiprojectmanager.telemost.RecordingService;
import ru.hack.aiprojectmanager.telegram.onboarding.OnboardingService;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateDispatcher {

    private static final Pattern TELEMOST_URL =
            Pattern.compile("https://telemost\\.yandex\\.ru/j/[\\w%-]+");

    private static final Pattern TASK_EVENT_PATTERN = Pattern.compile(
            "(?i)(готово|сделал[аи]?|завершил[аи]?|выполнил[аи]?|закончил[аи]?|беру|взял[аи]?|возьму|выполнено|завершено)");

    private final AgentService agentService;
    private final GroupContextService groupContextService;
    private final BotUserService botUserService;
    private final OnboardingService onboardingService;
    private final AppUserRepository appUserRepository;
    private final TelegramClient telegramClient;
    private final SttProvider sttProvider;
    private final RecordingService recordingService;
    private final DigestScheduler digestScheduler;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

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

    private void handleMessage(Message message) throws TelegramApiException {
        String text = message.getText();

        if (text == null && message.hasVoice()) {
            text = transcribeVoice(message);
            if (text == null || text.isBlank()) return;
        }

        if (text == null) return;

        Long chatId = message.getChatId();
        boolean isGroupChat = chatId < 0;

        // Автодетект ссылки на Telemost — работает и в личке, и в группе
        Matcher telemost = TELEMOST_URL.matcher(text);
        if (telemost.find()) {
            recordingService.startRecording(chatId, telemost.group());
            return;
        }

        if (isGroupChat) {
            // Молча сохраняем все сообщения группы для rolling summary
            String senderName = senderName(message.getFrom());
            groupContextService.saveMessage(chatId, message.getFrom().getId(), senderName, text);

            if (!isAddressedToBot(text)) {
                Long senderId = message.getFrom().getId();
                if (TASK_EVENT_PATTERN.matcher(text).find()
                        && appUserRepository.findFirstByTelegramId(senderId).isPresent()) {
                    String autonomousReply = agentService.processGroupAutonomous(chatId, senderId, text);
                    if (autonomousReply != null && !autonomousReply.startsWith("SKIP")) {
                        sendReply(chatId, "🤖 " + autonomousReply);
                    }
                }
                return;
            }
        }

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

    private String transcribeVoice(Message message) {
        try {
            String fileId = message.getVoice().getFileId();
            org.telegram.telegrambots.meta.api.objects.File fileInfo = telegramClient.execute(new GetFile(fileId));
            String downloadUrl = "https://api.telegram.org/file/bot" + botToken + "/" + fileInfo.getFilePath();
            byte[] audio = org.springframework.web.client.RestClient.create()
                    .get().uri(downloadUrl).retrieve().body(byte[].class);
            if (audio == null) return null;
            return sttProvider.transcribe(audio, "voice.ogg");
        } catch (Exception e) {
            log.warn("Voice transcription failed for chatId={}: {}", message.getChatId(), e.getMessage());
            return null;
        }
    }

    private String senderName(User from) {
        if (from.getFirstName() != null && from.getLastName() != null) {
            return from.getFirstName() + " " + from.getLastName();
        }
        if (from.getFirstName() != null) return from.getFirstName();
        if (from.getUserName() != null) return "@" + from.getUserName();
        return "Участник";
    }

    private boolean isAddressedToBot(String text) {
        if (text.startsWith("/")) return true;
        return text.toLowerCase().contains("@" + botUsername.toLowerCase());
    }

    private String removeMyMention(String text) {
        return text.replaceAll("(?i)@" + botUsername, "").trim();
    }

    private void sendGroupWelcome(Long chatId) throws TelegramApiException {
        sendReply(chatId, """
                Привет! Я бот для управления задачами YouGile 👋

                Чтобы начать работу, каждый участник должен зарегистрироваться:
                1. Найдите меня в Telegram: @""" + botUsername + """

                2. Напишите мне /start в личных сообщениях
                3. Введите логин и пароль от YouGile

                После регистрации вы сможете управлять задачами прямо из этого чата — просто напишите @""" + botUsername + " и ваш запрос.");
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
                    /morning — вручную запустить утреннее уведомление
                    /evening — вручную запустить вечерний дайджест

                    Просто напиши что нужно — например: "Создай задачу написать тесты".
                    """);
            case "/morning" -> {
                log.info("Manual morning reminder triggered by userId={}", userId);
                sendReply(chatId, "⏰ Запускаю утреннее уведомление...");
                digestScheduler.sendMorningReminderFor(userId);
            }
            case "/evening" -> {
                log.info("Manual evening digest triggered by userId={}", userId);
                sendReply(chatId, "📋 Запускаю вечерний дайджест...");
                digestScheduler.sendEveningDigestFor(userId);
            }
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

        sendReply(chatId, agentService.process(chatId, userId, text));
    }

    private void sendReply(Long chatId, String text) throws TelegramApiException {
        telegramClient.execute(SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build());
    }
}
