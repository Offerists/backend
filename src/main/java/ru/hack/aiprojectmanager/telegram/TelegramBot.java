package ru.hack.aiprojectmanager.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBot implements LongPollingUpdateConsumer {

    private final UpdateDispatcher dispatcher;

    @Override
    public void consume(List<Update> list) {
        list.forEach(update -> {
            try {
                dispatcher.dispatch(update);
            } catch (TelegramApiException e) {
                log.error("Telegram API error on update {}: {}", update.getUpdateId(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error on update {}: {}", update.getUpdateId(), e.getMessage(), e);
            }
        });
    }
}
