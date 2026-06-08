package ru.hack.aiprojectmanager.miniapp;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME = "TelegramAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AIProjectManager Mini App API")
                        .version("1.0.0")
                        .description("""
                                REST API для Telegram Mini App.

                                **Аутентификация** — заголовок `X-Telegram-Init-Data` со строкой \
                                `initData` из `window.Telegram.WebApp.initData` (HMAC-SHA256, подписан ботом).
                                Время жизни initData — 24 часа.

                                В dev-режиме (`MINIAPP_DEV_MODE=true`) вместо initData можно передавать \
                                заголовок `X-Dev-Telegram-User-Id` с числовым Telegram ID пользователя.
                                """)
                        .contact(new Contact().name("AIProjectManager Team"))
                )
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Telegram-Init-Data")
                                .description("Telegram WebApp initData — строка URL-encoded параметров с полем hash. "
                                        + "Получается из window.Telegram.WebApp.initData.")
                        )
                );
    }
}
