package ru.hack.aiprojectmanager.telegram.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.hack.aiprojectmanager.telegram.TelegramBot;
import ru.hack.aiprojectmanager.telegram.properties.TelegramBotProperties;

@Configuration
@EnableConfigurationProperties(TelegramBotProperties.class)
public class TelegramBotConfig {

    @Autowired
    public void registerBot(TelegramBotsLongPollingApplication botsApplication,
                            TelegramBot bot,
                            TelegramBotProperties properties) throws TelegramApiException {
        botsApplication.registerBot(properties.getToken(), bot);
    }
}
