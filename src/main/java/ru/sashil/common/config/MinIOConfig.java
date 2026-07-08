package ru.sashil.common.config;

import io.minio.MinioClient;
import ru.sashil.common.util.ConfigLoader;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class MinIOConfig {
    private static final Logger LOGGER = Logger.getLogger(MinIOConfig.class.getName());
    private static MinioClient instance;
    private static String bucketName;
    private static String docsBucketName;
    private static String endpoint;

    public static synchronized MinioClient getClient() {
        if (instance != null) {
            return instance;
        }
        try {
            String accessKey = ConfigLoader.get("MINIO_ACCESS_KEY");
            String secretKey = ConfigLoader.get("MINIO_SECRET_KEY");
            endpoint = ConfigLoader.get("MINIO_ENDPOINT", "http://localhost:9000");

            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.MINUTES)
                    .writeTimeout(10, TimeUnit.MINUTES)
                    .readTimeout(10, TimeUnit.MINUTES)
                    .build();

            instance = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .httpClient(httpClient)
                    .build();

            bucketName = ConfigLoader.get("MINIO_BUCKET", "medical-certificates");
            docsBucketName = ConfigLoader.get("MINIO_DOCS_BUCKET", "pool-documents");

            LOGGER.info("✅ MinIO клиент инициализирован: " + endpoint);
            return instance;
        } catch (Exception e) {
            LOGGER.severe("❌ Ошибка инициализации MinIO: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static String getBucketName() {
        if (bucketName == null) getClient();
        return bucketName;
    }

    public static String getDocsBucket() {
        if (docsBucketName == null) getClient();
        return docsBucketName;
    }

    public static String getEndpoint() {
        if (endpoint == null) getClient();
        return endpoint;
    }
}