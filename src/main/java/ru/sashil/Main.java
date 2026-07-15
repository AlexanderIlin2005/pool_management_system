package ru.sashil;

import ru.sashil.admin.AdminApplication;
import ru.sashil.bot.BotApplication;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║   Бассейн - Система управления        ║");
        System.out.println("║   1. Запустить VK Бота                ║");
        System.out.println("║   2. Запустить Админ-панель (Spring)  ║");
        System.out.println("║   3. Запустить ВСЁ сразу (Бот + Web)  ║");
        System.out.println("║   4. Выйти                            ║");
        System.out.println("╚═══════════════════════════════════════╝");
        System.out.print("Выберите действие: ");

        try (Scanner scanner = new Scanner(System.in)) {
            int choice = scanner.nextInt();

            switch (choice) {
                case 1 -> {
                    System.out.println("🚀 Запуск только VK Бота...");
                    BotApplication.main(args);
                }
                case 2 -> {
                    System.out.println("🚀 Запуск только Админ-панели...");
                    AdminApplication.run(args);
                }
                case 3 -> {
                    System.out.println("🚀 Запуск полной системы (Бот + Web)...");

                    // Поток для Spring Boot
                    Thread springThread = new Thread(() -> {
                        System.out.println("[Spring] Инициализация Web-сервера...");
                        AdminApplication.run(args);
                    }, "Spring-Boot-Thread");
                    springThread.setDaemon(false); // Не демон, чтобы JVM не закрылась, пока Spring жив
                    springThread.start();

                    // Небольшая пауза, чтобы Spring успел поднять контекст (опционально)
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}

                    // Поток для VK Бота
                    Thread botThread = new Thread(() -> {
                        System.out.println("[Bot] Инициализация VK LongPoll...");
                        BotApplication.main(args);
                    }, "VK-Bot-Thread");
                    botThread.setDaemon(false);
                    botThread.start();
                }
                case 4 -> {
                    System.out.println("👋 Выход...");
                    System.exit(0);
                }
                default -> System.out.println("❌ Неверный выбор");
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}