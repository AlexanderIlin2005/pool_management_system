package ru.sashil.bot;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.messages.MessageAttachment;
import com.vk.api.sdk.objects.docs.Doc;
import com.vk.api.sdk.objects.photos.Photo;
import ru.sashil.common.service.MinIOService;
import ru.sashil.common.util.ConfigLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BotApplication {
    private static final Logger LOGGER = Logger.getLogger(BotApplication.class.getName());

    // Храним ID сообщений, чтобы не отвечать дважды
    private static final Set<Integer> processedMessageIds = Collections.synchronizedSet(new HashSet<>());
    // Пользователи, ждущие файл
    private static final Set<Long> waitingForFileUsers = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        try {
            // 1. Загрузка конфига
            ConfigLoader.load();
            String vkToken = ConfigLoader.get("VK_BOT_TOKEN");

            if (vkToken == null || vkToken.isEmpty()) {
                throw new RuntimeException("❌ VK_BOT_TOKEN не найден");
            }

            long groupId = 239874040L;

            // 2. VK Client
            TransportClient transportClient = new HttpTransportClient();
            VkApiClient vk = new VkApiClient(transportClient);
            GroupActor actor = new GroupActor(groupId, vkToken);
            Random random = new Random();

            // 3. MinIO
            MinIOService minioService = new MinIOService();

            LOGGER.info("🚀 Бот запущен!");

            // 4. Long Poll Loop (как в твоем рабочем эхо-боте)
            Integer ts = vk.messages()
                    .getLongPollServer(actor)
                    .execute()
                    .getTs();

            while (true) {
                try {
                    var response = vk.messages()
                            .getLongPollHistory(actor)
                            .ts(ts)
                            .execute();

                    // В SDK 1.0.16 сообщения лежат здесь
                    List<Message> messages = response.getMessages().getItems();

                    if (messages != null && !messages.isEmpty()) {
                        for (Message message : messages) {
                            processMessage(vk, actor, message, minioService, random);
                        }
                    }

                    // Обновляем ts
                    ts = vk.messages()
                            .getLongPollServer(actor)
                            .execute()
                            .getTs();

                    Thread.sleep(500);

                } catch (ApiException | ClientException | InterruptedException e) {
                    LOGGER.log(Level.SEVERE, "⚠️ Ошибка в цикле: " + e.getMessage(), e);
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Критическая ошибка: " + e.getMessage(), e);
        }
    }

    private static void processMessage(VkApiClient vk, GroupActor actor, Message message, MinIOService minioService, Random random) {
        Long userId = message.getFromId();
        Integer messageId = message.getId();
        String text = message.getText();

        if (processedMessageIds.contains(messageId)) return;
        processedMessageIds.add(messageId);

        LOGGER.info("📩 Сообщение от " + userId + ": " + (text != null ? text : "[Файл]"));

        try {
            // Если ждем файл
            if (waitingForFileUsers.contains(userId)) {
                boolean fileProcessed = false;
                if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                    for (MessageAttachment attachment : message.getAttachments()) {
                        String type = attachment.getType().name();

                        if ("DOC".equals(type) && attachment.getDoc() != null) {
                            Doc doc = attachment.getDoc();
                            fileProcessed = downloadAndUpload(vk, actor, userId, doc.getUrl().toString(), doc.getTitle(), minioService, random);
                            break;
                        } else if ("PHOTO".equals(type) && attachment.getPhoto() != null) {
                            Photo photo = attachment.getPhoto();
                            if (photo.getSizes() != null && !photo.getSizes().isEmpty()) {
                                var sizes = photo.getSizes();
                                var largestSize = sizes.get(sizes.size() - 1);
                                String fileName = "photo_" + System.currentTimeMillis() + ".jpg";
                                fileProcessed = downloadAndUpload(vk, actor, userId, largestSize.getUrl().toString(), fileName, minioService, random);
                                break;
                            }
                        }
                    }
                }

                if (fileProcessed) {
                    waitingForFileUsers.remove(userId);
                    sendMessage(vk, actor, userId, "✅ Файл успешно сохранен!", random);
                } else {
                    sendMessage(vk, actor, userId, "⚠️ Не нашел файл. Прикрепи документ или фото.", random);
                }
                return;
            }

            // Текстовые команды
            if (text != null) {
                if (text.equalsIgnoreCase("Справка")) {
                    waitingForFileUsers.add(userId);
                    sendMessage(vk, actor, userId, "📄 Пришлите файл справки (PDF, JPG).", random);
                } else {
                    sendMessage(vk, actor, userId, "Вы написали: " + text, random);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Ошибка обработки: " + e.getMessage(), e);
            try {
                sendMessage(vk, actor, userId, "❌ Произошла ошибка.", random);
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    private static boolean downloadAndUpload(VkApiClient vk, GroupActor actor, Long userId, String fileUrl, String fileName, MinIOService minioService, Random random) {
        File tempFile = null;
        try {
            LOGGER.info("⬇️ Скачивание: " + fileName);
            tempFile = File.createTempFile("vk_", "_" + fileName);

            try (InputStream in = new URL(fileUrl).openStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            LOGGER.info("⬆️ Загрузка в MinIO...");
            String objectName = UUID.randomUUID() + "_" + fileName;
            String url = minioService.uploadFile(tempFile.getAbsolutePath(), objectName);

            sendMessage(vk, actor, userId, "✅ Справка загружена!\n🔗 URL: " + url, random);
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Ошибка файла: " + e.getMessage(), e);
            try {
                sendMessage(vk, actor, userId, "❌ Ошибка загрузки: " + e.getMessage(), random);
            } catch (Exception ex) {
                // ignore
            }
            return false;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private static void sendMessage(VkApiClient vk, GroupActor actor, Long userId, String text, Random random) throws ApiException, ClientException {
        // Используем sendDeprecated, как в твоем рабочем коде
        // ИСПРАВЛЕНО: userId передается как Long, а не int
        vk.messages().sendDeprecated(actor)
                .userId(userId)
                .message(text)
                .randomId(random.nextInt(100000))
                .execute();
    }
}