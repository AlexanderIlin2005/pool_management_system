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
import java.io.FileInputStream;  // ДОБАВИТЬ
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PaymentReceiptHandler {
    private static final Logger LOGGER = Logger.getLogger(PaymentReceiptHandler.class.getName());

    private final ConcurrentHashMap<Long, Integer> steps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<String, Object>> tempData = new ConcurrentHashMap<>();

    private final DatabaseService dbService;
    private final MinIOService minioService;

    private VkApiClient vk;
    private GroupActor actor;
    private Random random;

    public PaymentReceiptHandler(DatabaseService dbService, MinIOService minioService) {
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
                LOGGER.info("✅ PaymentReceiptHandler VK Client initialized");
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

        StringBuilder sb = new StringBuilder("📄 Загрузка квитанции об оплате\n\n");
        sb.append("Выберите ребенка (введите номер):\n");
        for (int i = 0; i < children.size(); i++) {
            String name = children.get(i).get("firstName") + " " + children.get(i).get("lastName");
            sb.append((i + 1)).append(". ").append(name).append("\n");
        }
        sb.append("\nДля отмены напишите 'Отмена'");
        sendMessage(userId, sb.toString());
    }

    public String processStep(long userId, String text, String rawJsonMessage) {
        ensureVkClient();
        Integer step = steps.get(userId);
        if (step == null) return null;
        Map<String, Object> data = tempData.get(userId);
        if (data == null) return null;

        // Проверка на отмену
        String cmd = text != null ? text.trim().toLowerCase() : "";
        if (cmd.equals("отмена")) {
            cancel(userId);
            return "❌ Загрузка квитанции отменена.";
        }

        switch (step) {
            case 1:
                try {
                    int num = Integer.parseInt(text.trim());
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) data.get("children");
                    if (num < 1 || num > children.size()) return "❌ Неверный номер. Попробуйте снова.";

                    long childId = ((Number) children.get(num - 1).get("id")).longValue();
                    data.put("childId", childId);
                    steps.put(userId, 2);
                    return "📅 За какой месяц оплата?\n\nВведите в формате: ММ.ГГГГ (например, 09.2026)\n\nДля отмены напишите 'Отмена'";
                } catch (NumberFormatException e) {
                    return "❌ Введите номер ребенка цифрой.";
                }

            case 2:
                try {
                    String normalized = text.trim();
                    String[] parts = normalized.split("\\.");
                    if (parts.length != 2) {
                        return "❌ Неверный формат. Используйте ММ.ГГГГ (например, 09.2026)";
                    }
                    int month = Integer.parseInt(parts[0]);
                    int year = Integer.parseInt(parts[1]);
                    if (month < 1 || month > 12) {
                        return "❌ Месяц должен быть от 1 до 12.";
                    }
                    LocalDate monthYear = LocalDate.of(year, month, 1);

                    // Проверка: не слишком ли далеко в будущее (максимум на 2 месяца вперед)
                    LocalDate now = LocalDate.now();
                    if (monthYear.isAfter(now.plusMonths(2))) {
                        return "❌ Нельзя оплачивать более чем за 2 месяца вперед.";
                    }

                    data.put("monthYear", monthYear);
                    steps.put(userId, 3);
                    return "📎 Пришлите фото квитанции (изображение или PDF файл).\n\nДля отмены напишите 'Отмена'";

                } catch (Exception e) {
                    return "❌ Неверный формат. Используйте ММ.ГГГГ (например, 09.2026)";
                }

            case 3:
                if (rawJsonMessage == null || !rawJsonMessage.contains("\"attachments\"")) {
                    return "❌ Я не вижу вложения. Пришлите файл с квитанцией.";
                }
                try {
                    String[] fileInfo = getFileUrlAndExt(rawJsonMessage);
                    if (fileInfo == null) {
                        cancel(userId);
                        return "❌ Не удалось найти файл. Попробуйте снова.";
                    }

                    String fileUrl = fileInfo[0];
                    String extension = fileInfo[1];
                    String originalName = fileInfo.length > 2 ? fileInfo[2] : "квитанция" + extension;

                    LOGGER.info("Найден файл квитанции URL: " + fileUrl + " (Расширение: " + extension + ")");

                    File file = downloadFile(fileUrl);
                    if (file == null) {
                        cancel(userId);
                        return "❌ Ошибка скачивания файла. Попробуйте снова.";
                    }

                    long childId = (long) data.get("childId");
                    LocalDate monthYear = (LocalDate) data.get("monthYear");

                    // ИСПРАВЛЕНО: используем FileInputStream для передачи InputStream
                    String objectName = "receipts/" + UUID.randomUUID().toString() + extension;
                    try (FileInputStream fis = new FileInputStream(file)) {
                        String url = minioService.uploadFileToDocsBucket(fis, objectName, file.length());
                        file.delete();

                        // Сохраняем в БД через DatabaseService
                        dbService.savePaymentReceipt(userId, childId, monthYear, url, originalName);

                        cancel(userId);
                        return "✅ Квитанция загружена!\n\nБухгалтер проверит её в ближайшее время.\nСтатус оплаты обновится после проверки.";
                    }
                } catch (Exception e) {
                    LOGGER.severe("Ошибка загрузки квитанции: " + e.getMessage());
                    e.printStackTrace();
                    cancel(userId);
                    return "❌ Ошибка загрузки квитанции: " + e.getMessage();
                }
            default:
                return null;
        }
    }

    /**
     * Возвращает массив [URL, Расширение, Имя_файла]
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
                        return new String[]{url, ".jpg", "фото_квитанции.jpg"};
                    }
                } else if ("doc".equals(type)) {
                    JSONObject docObj = attachment.getJSONObject("doc");
                    if (docObj.has("url")) {
                        String url = docObj.getString("url");
                        String ext = docObj.optString("ext", "");
                        if (!ext.startsWith(".") && !ext.isEmpty()) {
                            ext = "." + ext;
                        }
                        if (ext.isEmpty()) ext = ".pdf";
                        String fileName = docObj.optString("title", "квитанция" + ext);
                        return new String[]{url, ext, fileName};
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
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            try (InputStream inputStream = connection.getInputStream()) {
                File tempFile = File.createTempFile("receipt_", ".tmp");
                try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                return tempFile;
            } finally {
                connection.disconnect();
            }
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
            LOGGER.severe("Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    public void cancel(long userId) {
        steps.remove(userId);
        tempData.remove(userId);
    }
}