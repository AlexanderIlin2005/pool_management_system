package ru.sashil.bot.handlers;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.json.JSONArray;
import org.json.JSONObject;
import ru.sashil.common.service.DatabaseService;
import ru.sashil.common.service.MinIOService;
import ru.sashil.common.util.ConfigLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CertificateHandler {
    private static final Logger LOGGER = Logger.getLogger(CertificateHandler.class.getName());

    // Состояние загрузки: userId -> шаг (1: выбор ребенка, 2: ожидание фото)
    private final ConcurrentHashMap<Long, Integer> steps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<String, Object>> tempData = new ConcurrentHashMap<>();

    private final DatabaseService dbService;
    private final MinIOService minioService;

    // Ленивая инициализация VK клиента
    private VkApiClient vk;
    private GroupActor actor;
    private Random random;

    public CertificateHandler(DatabaseService dbService, MinIOService minioService) {
        this.dbService = dbService;
        this.minioService = minioService;
        // Не инициализируем VK клиент здесь, делаем это лениво
    }

    private void ensureVkClient() {
        if (vk == null) {
            try {
                String token = ConfigLoader.get("VK_BOT_TOKEN");
                long groupId = Long.parseLong(ConfigLoader.get("GROUP_ID"));

                TransportClient transportClient = new HttpTransportClient();
                this.vk = new VkApiClient(transportClient);
                this.actor = new GroupActor(groupId, token);
                this.random = new Random();

                LOGGER.info("✅ CertificateHandler VK Client initialized lazily");
            } catch (Exception e) {
                LOGGER.severe("❌ Ошибка инициализации VK клиента в CertificateHandler: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    public boolean isUploading(long userId) {
        return steps.containsKey(userId);
    }

    public void startUpload(long userId) throws SQLException {
        ensureVkClient(); // Инициализируем клиент при первом использовании

        List<Map<String, Object>> children = dbService.getChildrenByParentVkId(userId); // Исправлен метод
        if (children.isEmpty()) {
            sendMessage(userId, "У вас нет привязанных детей. Сначала добавьте ребенка.");
            return;
        }

        steps.put(userId, 1);
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("children", children);
        tempData.put(userId, data);

        StringBuilder sb = new StringBuilder("Выберите ребенка, для которого загружаете справку (введите номер):\n");
        for (int i = 0; i < children.size(); i++) {
            // В базе данных поле name может отсутствовать, используем firstName + lastName
            String name = (String) children.get(i).get("firstName") + " " + (String) children.get(i).get("lastName");
            sb.append((i + 1)).append(". ").append(name).append("\n");
        }
        sendMessage(userId, sb.toString());
    }

    /**
     * Этот метод вызывается из Kotlin-бота при каждом новом сообщении от пользователя,
     * который находится в режиме загрузки.
     */
    public String processStep(long userId, String text, String rawJsonMessage) {
        ensureVkClient(); // Инициализируем клиент при первом использовании

        Integer step = steps.get(userId);
        if (step == null) return null;

        Map<String, Object> data = tempData.get(userId);
        if (data == null) return null;

        switch (step) {
            case 1:
                // Выбор ребенка
                try {
                    int num = Integer.parseInt(text);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) data.get("children");

                    if (num < 1 || num > children.size()) {
                        return "Неверный номер. Попробуйте снова.";
                    }

                    long childId = ((Number) children.get(num - 1).get("id")).longValue();
                    data.put("childId", childId);
                    steps.put(userId, 2);
                    return "Теперь пришлите фото справки одним сообщением (без текста).";

                } catch (NumberFormatException e) {
                    return "Пожалуйста, введите номер ребенка цифрой.";
                }

            case 2:
                // Загрузка файла
                // Проверяем наличие вложений в JSON
                if (rawJsonMessage == null || !rawJsonMessage.contains("\"attachments\"")) {
                    return "Я не вижу вложения. Пожалуйста, пришлите именно фото файла.";
                }

                try {
                    // Парсим JSON и ищем URL фото
                    String photoUrl = extractPhotoUrl(rawJsonMessage);
                    if (photoUrl == null) {
                        cancel(userId);
                        return "Не удалось найти изображение во вложении. Попробуйте отправить другое фото.";
                    }

                    LOGGER.info("Найден URL фото: " + photoUrl);

                    // Скачиваем файл
                    File file = downloadFile(photoUrl);
                    if (file == null || !file.exists()) {
                        cancel(userId);
                        return "Ошибка при скачивании файла.";
                    }

                    // Загружаем в MinIO
                    String url = minioService.uploadFile(file.getAbsolutePath(), "certificate.jpg");
                    file.delete();

                    // Сохраняем в БД
                    long childId = ((Number) data.get("childId")).longValue();
                    dbService.saveCertificate(userId, childId, url);

                    cancel(userId);
                    return "✅ Справка успешно загружена! Администратор рассмотрит её в ближайшее время.";

                } catch (Exception e) {
                    LOGGER.severe("Ошибка загрузки справки: " + e.getMessage());
                    e.printStackTrace();
                    cancel(userId);
                    return "❌ Произошла ошибка при загрузке. Попробуйте позже.";
                }

            default:
                return null;
        }
    }

    private String extractPhotoUrl(String jsonString) {
        try {
            JSONObject msgObj = new JSONObject(jsonString);
            if (!msgObj.has("attachments")) return null;

            JSONArray attachments = msgObj.getJSONArray("attachments");
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.getJSONObject(i);
                if ("photo".equals(attachment.getString("type"))) {
                    JSONObject photoObj = attachment.getJSONObject("photo");
                    JSONArray sizes = photoObj.getJSONArray("sizes");
                    if (sizes.length() > 0) {
                        // Берем последний размер (обычно самый большой)
                        JSONObject largestSize = sizes.getJSONObject(sizes.length() - 1);
                        return largestSize.getString("url");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Ошибка парсинга JSON вложений: " + e.getMessage());
        }
        return null;
    }

    private File downloadFile(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            InputStream inputStream = connection.getInputStream();
            File tempFile = File.createTempFile("cert_", ".jpg");

            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            } finally {
                inputStream.close();
                connection.disconnect();
            }
            return tempFile;
        } catch (Exception e) {
            LOGGER.severe("Ошибка скачивания файла: " + e.getMessage());
            return null;
        }
    }

    private void sendMessage(long userId, String text) {
        if (vk == null) return;
        try {
            vk.messages()
                    .sendDeprecated(actor) // Используем send вместо sendDeprecated
                    .message(text)
                    .userId(userId)
                    .randomId(random.nextInt(Integer.MAX_VALUE))
                    .execute();
        } catch (ApiException | ClientException e) {
            LOGGER.severe("Ошибка отправки сообщения из CertificateHandler: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.severe("Неизвестная ошибка при отправке: " + e.getMessage());
        }
    }

    public void cancel(long userId) {
        steps.remove(userId);
        tempData.remove(userId);
    }
}