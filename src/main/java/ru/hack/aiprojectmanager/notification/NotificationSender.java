package ru.hack.aiprojectmanager.notification;

public interface NotificationSender {

    void send(Long chatId, String text);
}
