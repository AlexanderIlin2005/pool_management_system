package ru.sashil.common.config;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;

import ru.sashil.common.util.ConfigLoader;

import okhttp3.OkHttpClient;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
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

            // Принудительно используем IPv4 для localhost
            InetAddress inetAddress = InetAddress.getByName("localhost");
            String host = inetAddress.getHostAddress(); // 127.0.0.1
            String fixedEndpoint = endpoint.replace("localhost", host);
            LOGGER.info("   FIXED ENDPOINT: " + fixedEndpoint);

            // OkHttpClient с большими таймаутами
            OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();

            instance = MinioClient.builder()
                .endpoint(fixedEndpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
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
