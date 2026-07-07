package ru.sashil.admin.util;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class FileUtils {

    public static void sendTxtFile(HttpServletResponse response, String fileNamePrefix, String content) throws IOException {
        String fileName = fileNamePrefix + ".txt";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName);

        try (OutputStream out = response.getOutputStream()) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}