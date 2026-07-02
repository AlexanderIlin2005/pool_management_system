package ru.sashil.common.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import ru.sashil.common.config.MinIOConfig;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

public class MinIOService {
    private static final Logger LOGGER = Logger.getLogger(MinIOService.class.getName());
    private final MinioClient client;
    private final String bucket;

    public MinIOService() {
        LOGGER.info("🔧 Инициализация MinIOService...");
        this.client = MinIOConfig.getClient();
        this.bucket = MinIOConfig.getBucketName();
        LOGGER.info("✅ MinIOService инициализирован. Client: " + (client != null ? "OK" : "NULL"));
    }

    public boolean isClientNull() {
        return client == null;
    }

    // Вспомогательный метод для определения типа
    private String getContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) return "application/pdf";
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    public String uploadFile(String filePath, String originalName) throws Exception {
        if (client == null) {
            throw new IllegalStateException("MinIO клиент не инициализирован");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Файл не найден: " + filePath);
        }

        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex);
        }

        String objectName = "certificates/" + UUID.randomUUID().toString() + extension;

        // Используем жесткое определение типа вместо Files.probeContentType
        String contentType = getContentType(originalName);

        client.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(Files.newInputStream(file.toPath()), file.length(), -1)
                        .contentType(contentType)
                        .build()
        );

        String endpoint = MinIOConfig.getEndpoint();
        String url = endpoint + "/" + bucket + "/" + objectName;
        LOGGER.info("✅ Файл загружен в MinIO: " + url);
        return url;
    }

    public String uploadFile(InputStream stream, String originalName, long size) throws Exception {
        if (client == null) {
            throw new IllegalStateException("MinIO клиент не инициализирован");
        }

        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex);
        }

        String objectName = "certificates/" + UUID.randomUUID().toString() + extension;

        // Используем жесткое определение типа
        String contentType = getContentType(originalName);

        client.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build()
        );

        String endpoint = MinIOConfig.getEndpoint();
        String url = endpoint + "/" + bucket + "/" + objectName;
        LOGGER.info("✅ Файл загружен в MinIO: " + url);
        return url;
    }
}