package com.invoiceapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.File;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Get absolute path to uploads directory
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize().toString();

        // Ensure the directory exists
        File uploadDirFile = new File(absolutePath);
        if (!uploadDirFile.exists()) {
            uploadDirFile.mkdirs();
        }

        // For cross-platform support, ensure we use the file: protocol correctly
        String resourceLocation;
        if (absolutePath.startsWith("/") || !absolutePath.contains(":")) {
            // Linux/Unix style absolute paths
            resourceLocation = "file:" + absolutePath + "/";
        } else {
            // Windows style paths (C:\...)
            resourceLocation = "file:/" + absolutePath.replace("\\", "/") + "/";
        }

        // Log the resource location for debugging
        System.out.println("Serving static resources from: " + resourceLocation);

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resourceLocation)
                .setCachePeriod(3600); // Cache for 1 hour
    }
}
