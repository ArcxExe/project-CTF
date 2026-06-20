package com.arcx.ctfplatform.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.storage.upload-dir:/app/uploads}")
    private String uploadDir;

    public String storeFile(MultipartFile file) {
        try {
            Path dir = Paths.get(uploadDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path targetLocation = dir.resolve(filename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return targetLocation.toString();
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file", ex);
        }
    }
}
