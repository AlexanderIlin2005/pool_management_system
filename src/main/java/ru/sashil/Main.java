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
        System.out.println("║   3. Выйти                            ║");
        System.out.println("╚═══════════════════════════════════════╝");
        System.out.print("Выберите действие: ");

        try (Scanner scanner = new Scanner(System.in)) {
            int choice = scanner.nextInt();

            switch (choice) {
                case 1 -> {
                    System.out.println("Запуск VK Бота...");
                    BotApplication.main(args);
                }
                case 2 -> {
                    System.out.println("Запуск Админ-панели...");
                    System.out.println("Инициализация Spring Boot...");
                    // Отключаем логи Spring для чистоты, если нужно, или оставляем как есть
                    AdminApplication.run(args);
                }
                case 3 -> {
                    System.out.println("Выход...");
                    System.exit(0);
                }
                default -> System.out.println("Неверный выбор");
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}