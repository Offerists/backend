package ru.hack.aiprojectmanager.telegram.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.hack.aiprojectmanager.telegram.properties.TelegramBotProperties;

@Configuration
public class TelegramClientConfig {
    @Bean
    public TelegramClient telegramClient(TelegramBotProperties properties) {
        return new OkHttpTelegramClient(properties.getToken());
    }
}
