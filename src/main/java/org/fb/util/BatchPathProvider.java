package org.fb.util;

import org.fb.constant.BusinessConstant;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
public class BatchPathProvider {
    private static final String storagePath = BusinessConstant.TEMP_FILE_PATH;

    public List<String> saveFilesToLocalAndReturnPaths(MultipartFile[] files) {
        List<String> paths = new ArrayList<>();
        Path storageDir = Paths.get(storagePath);
        try {
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String originalFileName = file.getOriginalFilename();
                if (originalFileName == null) {
                    continue;
                }
                if (!isValidDocumentType(originalFileName)) {
                    continue;
                }
                Path path = storageDir.resolve(originalFileName);
                file.transferTo(path.toFile());
                paths.add(path.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("批量文件保存失败", e);
        }
        return paths;
    }

    private boolean isValidDocumentType(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase();
        return ext.equals("pdf") || ext.equals("txt") || ext.equals("md") || ext.equals("doc") || ext.equals("docx") || ext.equals("ppt") || ext.equals("pptx") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png");
    }

    private String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(dot + 1);
        }
        return "unknown";
    }
}
