package ru.sashil.common.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;
import ru.sashil.common.config.MinIOConfig;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

@Service // <-- ВАЖНО: Делаем этот класс видимым для Spring
public class MinIOService {
    private static final Logger LOGGER = Logger.getLogger(MinIOService.class.getName());

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
        MinioClient client = MinIOConfig.getClient();
        String bucket = MinIOConfig.getBucketName();

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
        String contentType = getContentType(originalName);

        client.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(Files.newInputStream(file.toPath()), file.length(), -1)
                        .contentType(contentType)
                        .build()
        );

        return MinIOConfig.getEndpoint() + "/" + bucket + "/" + objectName;
    }

    public String uploadFile(InputStream stream, String originalName, long size) throws Exception {
        MinioClient client = MinIOConfig.getClient();
        String bucket = MinIOConfig.getBucketName();

        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex);
        }

        String objectName = "certificates/" + UUID.randomUUID().toString() + extension;
        String contentType = getContentType(originalName);

        client.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build()
        );

        return MinIOConfig.getEndpoint() + "/" + bucket + "/" + objectName;
    }

    public String uploadFileToDocsBucket(InputStream stream, String objectName, long size) throws Exception {
        MinioClient client = MinIOConfig.getClient();
        String bucket = MinIOConfig.getDocsBucket();
        String contentType = "application/pdf";

        client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(stream, size, -1)
                .contentType(contentType)
                .build());

        LOGGER.info("✅ Файл загружен в бакет документов: " + objectName);
        return MinIOConfig.getEndpoint() + "/" + bucket + "/" + objectName;
    }
}