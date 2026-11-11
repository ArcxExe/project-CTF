package com.arcx.ctfplatform.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".zip", ".txt");

    @Value("${app.storage.upload-dir:/app/uploads}")
    private String uploadDir;

    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        String sanitizedBaseName = Paths.get(originalFilename).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._-]", "_");

        String lowerName = sanitizedBaseName.toLowerCase(Locale.ROOT);
        boolean validExt = ALLOWED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
        if (!validExt) {
            throw new IllegalArgumentException("Unsupported file extension. Allowed extensions: .pdf, .zip, .txt");
        }

        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String filename = UUID.randomUUID().toString() + "_" + sanitizedBaseName;
            Path targetLocation = dir.resolve(filename).normalize();
            if (!targetLocation.startsWith(dir)) {
                throw new IllegalArgumentException("Invalid file path");
            }

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return targetLocation.toString();
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file", ex);
        }
    }

    public Path resolveFile(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank() || storedFileName.contains("..")) {
            throw new IllegalArgumentException("Invalid file path");
        }
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        String baseName = Paths.get(storedFileName).getFileName().toString();
        Path resolved = dir.resolve(baseName).normalize();
        if (!resolved.startsWith(dir)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return resolved;
    }
}
