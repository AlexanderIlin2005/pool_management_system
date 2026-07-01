package ru.sashil.bot;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.queries.messages.MessagesGetLongPollHistoryQuery;

import ru.sashil.common.service.MinIOService;
import ru.sashil.common.util.ConfigLoader;
import ru.sashil.common.config.MinIOConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;
import java.util.logging.Logger;

public class BotApplication {
    private static final Logger LOGGER = Logger.getLogger(BotApplication.class.getName());
    private static final Long GROUP_ID = 239874040L;
    private static MinIOService minioService;

    public static void main(String[] args) {
        // 1. Загружаем конфиг
        ConfigLoader.load();

        String accessToken = ConfigLoader.get("VK_BOT_TOKEN");

        if (accessToken == null || accessToken.isEmpty()) {
            LOGGER.severe("❌ Ошибка: VK_BOT_TOKEN не найден в application.properties");
            System.exit(1);
        }

        LOGGER.info("✅ VK_BOT_TOKEN загружен");

        // 2. Инициализируем MinIO при старте
        try {
            LOGGER.info("🔧 Инициализация MinIO...");
            minioService = new MinIOService();
            if (minioService.isClientNull()) {
                LOGGER.warning("⚠️ MinIO клиент не инициализирован. Проверьте application.properties");
            } else {
                LOGGER.info("✅ MinIO готов к работе");
            }
        } catch (Exception e) {
            LOGGER.warning("⚠️ Ошибка инициализации MinIO: " + e.getMessage());
        }

        try {
            TransportClient transportClient = new HttpTransportClient();
            VkApiClient vk = new VkApiClient(transportClient);
            Random random = new Random();

            GroupActor actor = new GroupActor(GROUP_ID, accessToken);

            Integer ts = vk.messages()
                    .getLongPollServer(actor)
                    .execute()
                    .getTs();

            LOGGER.info("🚀 Бот запущен. Начинаем прослушивание...");

            while (true) {
                try {
                    MessagesGetLongPollHistoryQuery historyQuery = vk.messages()
                            .getLongPollHistory(actor)
                            .ts(ts);

                    var messages = historyQuery.execute().getMessages().getItems();

                    if (!messages.isEmpty()) {
                        messages.forEach(message -> {
                            LOGGER.info("📩 Сообщение от " + message.getFromId() + ": " + message.getText());
                            try {
                                String text = message.getText();
                                Long userId = message.getFromId();

                                if (text != null && text.equalsIgnoreCase("Справка")) {
                                    try {
                                        LOGGER.info("📄 Обработка команды 'Справка' для пользователя " + userId);

                                        if (minioService == null || minioService.isClientNull()) {
                                            LOGGER.warning("⚠️ MinIO не инициализирован, пробую переподключиться...");
                                            minioService = new MinIOService();
                                            if (minioService.isClientNull()) {
                                                throw new RuntimeException("MinIO клиент не инициализирован. Проверьте application.properties");
                                            }
                                        }

                                        String fileName = "certificate_" + userId + "_" + System.currentTimeMillis() + ".txt";
                                        File testFile = new File(fileName);
                                        try (FileOutputStream fos = new FileOutputStream(testFile)) {
                                            fos.write(("Тестовая справка для пользователя " + userId + "\n" +
                                                      "Дата: " + new java.util.Date()).getBytes());
                                        }

                                        String url = minioService.uploadFile(testFile.getAbsolutePath(), fileName);
                                        testFile.delete();

                                        vk.messages()
                                            .sendDeprecated(actor)
                                            .message("📄 Ваша справка загружена!\nURL: " + url)
                                            .userId(userId)
                                            .randomId(random.nextInt(10000))
                                            .execute();
                                        LOGGER.info("✅ Справка загружена");

                                    } catch (Exception e) {
                                        LOGGER.severe("❌ Ошибка загрузки справки: " + e.getMessage());
                                        e.printStackTrace();
                                        vk.messages()
                                            .sendDeprecated(actor)
                                            .message("❌ Ошибка загрузки справки: " + e.getMessage())
                                            .userId(userId)
                                            .randomId(random.nextInt(10000))
                                            .execute();
                                    }
                                } else {
                                    vk.messages()
                                        .sendDeprecated(actor)
                                        .message("Ты написал: " + text)
                                        .userId(userId)
                                        .randomId(random.nextInt(10000))
                                        .execute();
                                }
                                LOGGER.info("✅ Ответ отправлен");
                            } catch (ApiException | ClientException e) {
                                LOGGER.severe("❌ Ошибка отправки: " + e.getMessage());
                            }
                        });
                    }

                    ts = vk.messages()
                            .getLongPollServer(actor)
                            .execute()
                            .getTs();

                    Thread.sleep(500);

                } catch (ApiException | ClientException | InterruptedException e) {
                    LOGGER.severe("⚠️ Ошибка в цикле: " + e.getMessage());
                    Thread.sleep(2000);
                }
            }

        } catch (Exception e) {
            LOGGER.severe("❌ Ошибка инициализации: " + e.getMessage());
            System.exit(1);
        }
    }
}
