package ru.hack.aiprojectmanager.telegram.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
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

    @Bean
    public TelegramClient telegramClient(TelegramBotProperties properties) {
        return new OkHttpTelegramClient(properties.getToken());
    }
}
