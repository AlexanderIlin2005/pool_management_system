package ru.sashil;

import ru.sashil.admin.AdminApplication;
import ru.sashil.bot.BotApplication;

public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 Запуск полной системы (Бот + Web)...");

        // Запускаем Spring Boot в отдельном потоке
        Thread springThread = new Thread(() -> {
            System.out.println("[Spring] Инициализация Web-сервера...");
            AdminApplication.run(args);
        }, "Spring-Boot-Thread");
        springThread.setDaemon(false);
        springThread.start();

        // Даем Spring время на инициализацию
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Запускаем VK Bot
        Thread botThread = new Thread(() -> {
            System.out.println("[Bot] Инициализация VK LongPoll...");
            BotApplication.main(args);
        }, "VK-Bot-Thread");
        botThread.setDaemon(false);
        botThread.start();
    }
}