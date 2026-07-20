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

    private final ConcurrentHashMap<Long, Integer> steps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<String, Object>> tempData = new ConcurrentHashMap<>();

    private final DatabaseService dbService;
    private final MinIOService minioService;

    private VkApiClient vk;
    private GroupActor actor;
    private Random random;

    public CertificateHandler(DatabaseService dbService, MinIOService minioService) {
        this.dbService = dbService;
        this.minioService = minioService;
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
                LOGGER.severe("❌ Ошибка инициализации VK клиента: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    public boolean isUploading(long userId) {
        return steps.containsKey(userId);
    }

    public void startUpload(long userId) throws SQLException {
        ensureVkClient();
        List<Map<String, Object>> children = dbService.getChildrenByParentVkId(userId);
        if (children.isEmpty()) {
            sendMessage(userId, "У вас нет привязанных детей.");
            return;
        }
        steps.put(userId, 1);
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("children", children);
        tempData.put(userId, data);

        StringBuilder sb = new StringBuilder("Выберите ребенка (введите номер):\n");
        for (int i = 0; i < children.size(); i++) {
            String name = children.get(i).get("firstName") + " " + children.get(i).get("lastName");
            sb.append((i + 1)).append(". ").append(name).append("\n");
        }
        sendMessage(userId, sb.toString());
    }

    public String processStep(long userId, String text, String rawJsonMessage) {
        ensureVkClient();
        Integer step = steps.get(userId);
        if (step == null) return null;
        Map<String, Object> data = tempData.get(userId);
        if (data == null) return null;

        switch (step) {
            case 1:
                try {
                    int num = Integer.parseInt(text);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) data.get("children");
                    if (num < 1 || num > children.size()) return "Неверный номер.";

                    long childId = ((Number) children.get(num - 1).get("id")).longValue();
                    data.put("childId", childId);
                    steps.put(userId, 2);
                    return "Пришлите справку (фото или PDF файл).";
                } catch (NumberFormatException e) {
                    return "Введите номер цифрой.";
                }

            case 2:
                if (rawJsonMessage == null || !rawJsonMessage.contains("\"attachments\"")) {
                    return "Я не вижу вложения. Пришлите файл.";
                }
                try {
                    // Получаем URL и расширение файла
                    String[] fileInfo = getFileUrlAndExt(rawJsonMessage);
                    if (fileInfo == null) {
                        cancel(userId);
                        return "Не удалось найти файл. Попробуйте снова.";
                    }

                    String fileUrl = fileInfo[0];
                    String extension = fileInfo[1]; // .pdf, .jpg, .png

                    LOGGER.info("Найден файл URL: " + fileUrl + " (Расширение: " + extension + ")");

                    File file = downloadFile(fileUrl);
                    if (file == null) {
                        cancel(userId);
                        return "Ошибка скачивания.";
                    }

                    String url = minioService.uploadFile(file.getAbsolutePath(), "certificate" + extension);
                    file.delete();

                    long childId = ((Number) data.get("childId")).longValue();
                    dbService.saveCertificate(userId, childId, url);
                    cancel(userId);
                    return "✅ Справка загружена!";
                } catch (Exception e) {
                    LOGGER.severe("Ошибка: " + e.getMessage());
                    e.printStackTrace();
                    cancel(userId);
                    return "❌ Ошибка загрузки.";
                }
            default:
                return null;
        }
    }

    /**
     * Возвращает массив [URL, Расширение]
     */
    private String[] getFileUrlAndExt(String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);
            if (!root.has("message")) return null;
            JSONObject msgObj = root.getJSONObject("message");
            if (!msgObj.has("attachments")) return null;

            JSONArray attachments = msgObj.getJSONArray("attachments");
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.getJSONObject(i);
                String type = attachment.getString("type");

                if ("photo".equals(type)) {
                    JSONObject photoObj = attachment.getJSONObject("photo");
                    JSONArray sizes = photoObj.getJSONArray("sizes");
                    if (sizes.length() > 0) {
                        String url = sizes.getJSONObject(sizes.length() - 1).getString("url");
                        return new String[]{url, ".jpg"}; // Фото всегда сохраняем как jpg для единообразия
                    }
                } else if ("doc".equals(type)) {
                    JSONObject docObj = attachment.getJSONObject("doc");
                    if (docObj.has("url")) {
                        String url = docObj.getString("url");
                        // Берем расширение из поля ext, добавляем точку если её нет
                        String ext = docObj.optString("ext", "");
                        if (!ext.startsWith(".") && !ext.isEmpty()) {
                            ext = "." + ext;
                        }
                        if (ext.isEmpty()) ext = ".doc"; // Дефолт
                        return new String[]{url, ext};
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Ошибка получения URL файла: " + e.getMessage());
        }
        return null;
    }

    private File downloadFile(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            InputStream inputStream = connection.getInputStream();

            File tempFile = File.createTempFile("cert_", ".tmp");
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
            LOGGER.severe("Ошибка скачивания: " + e.getMessage());
            return null;
        }
    }

    private void sendMessage(long userId, String text) {
        if (vk == null) return;
        try {
            vk.messages().sendDeprecated(actor)
                    .message(text)
                    .userId(userId)
                    .randomId(random.nextInt(Integer.MAX_VALUE))
                    .execute();
        } catch (ApiException | ClientException e) {
            LOGGER.severe("Ошибка отправки: " + e.getMessage());
        }
    }

    public void cancel(long userId) {
        steps.remove(userId);
        tempData.remove(userId);
    }
}