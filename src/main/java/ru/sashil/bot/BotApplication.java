package ru.sashil.bot;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.queries.messages.MessagesGetLongPollHistoryQuery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class BotApplication {
    private static final Logger LOGGER = Logger.getLogger(BotApplication.class.getName());
    private static final long GROUP_ID = 239874040L;

    private static Map<String, String> loadEnvFile() {
        Map<String, String> env = new HashMap<>();
        Path envPath = Paths.get(".env");

        if (!Files.exists(envPath)) {
            LOGGER.warning("⚠️ .env файл не найден в корне проекта");
            return env;
        }

        try (Stream<String> lines = Files.lines(envPath)) {
            lines.filter(line -> !line.trim().startsWith("#") && line.contains("="))
                    .forEach(line -> {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            env.put(parts[0].trim(), parts[1].trim());
                        }
                    });
            LOGGER.info("✅ .env файл загружен, найдено " + env.size() + " переменных");
        } catch (IOException e) {
            LOGGER.severe("❌ Ошибка чтения .env: " + e.getMessage());
        }

        return env;
    }

    private static String getEnv(String key, Map<String, String> envMap) {
        if (envMap.containsKey(key)) {
            return envMap.get(key);
        }
        return System.getenv(key);
    }

    public static void main(String[] args) {
        Map<String, String> envMap = loadEnvFile();

        String accessToken = getEnv("VK_BOT_TOKEN", envMap);

        if (accessToken == null || accessToken.isEmpty()) {
            LOGGER.severe("❌ Ошибка: VK_BOT_TOKEN не найден ни в .env, ни в системных переменных");
            System.exit(1);
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
                                vk.messages()
                                        .sendDeprecated(actor)
                                        .message(message.getText())
                                        .userId(message.getFromId())
                                        .randomId(random.nextInt(10000))
                                        .execute();
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
