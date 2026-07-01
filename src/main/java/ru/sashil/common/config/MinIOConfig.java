package ru.sashil.common.config;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;

import ru.sashil.common.util.ConfigLoader;

import java.util.logging.Logger;

public class MinIOConfig {
    private static final Logger LOGGER = Logger.getLogger(MinIOConfig.class.getName());
    private static MinioClient instance;
    private static String bucketName;
    private static String endpoint;

    public static MinioClient getClient() {
        if (instance != null) {
            return instance;
        }

        try {
            endpoint = ConfigLoader.get("MINIO_ENDPOINT", "http://localhost:9000");
            String accessKey = ConfigLoader.get("MINIO_ACCESS_KEY", "minioadmin");
            String secretKey = ConfigLoader.get("MINIO_SECRET_KEY", "minioadmin123");
            bucketName = ConfigLoader.get("MINIO_BUCKET", "medical-certificates");

            LOGGER.info("🔗 Подключение к MinIO:");
            LOGGER.info("   ENDPOINT: " + endpoint);
            LOGGER.info("   ACCESS_KEY: " + accessKey);
            LOGGER.info("   BUCKET: " + bucketName);

            instance = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

            boolean exists = instance.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                LOGGER.warning("⚠️ Bucket '" + bucketName + "' не существует. Запустите minio_setup.sh");
            } else {
                LOGGER.info("✅ Подключен к MinIO, bucket: " + bucketName);
            }

            return instance;

        } catch (Exception e) {
            LOGGER.severe("❌ Ошибка подключения к MinIO: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static String getBucketName() {
        return bucketName;
    }

    public static String getEndpoint() {
        return endpoint;
    }
}
