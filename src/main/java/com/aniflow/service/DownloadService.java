package com.aniflow.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public CompletableFuture<Path> downloadEpisode(String sourceUrl, Path destination, ProgressListener progressListener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return download(sourceUrl, destination, progressListener);
            } catch (IOException e) {
                throw new RuntimeException("Download failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private Path download(String sourceUrl, Path destination, ProgressListener progressListener) throws IOException {
        URL url = new URL(sourceUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);

        long totalBytes = connection.getContentLengthLong();
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(destination,
                 StandardOpenOption.CREATE,
                 StandardOpenOption.TRUNCATE_EXISTING,
                 StandardOpenOption.WRITE)) {

            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (progressListener != null && totalBytes > 0) {
                    double progress = (double) downloaded / totalBytes;
                    progressListener.onProgress(Math.min(1.0, progress));
                }
            }

            if (progressListener != null) {
                progressListener.onProgress(1.0);
            }

            return destination;
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(double value);
    }
}
