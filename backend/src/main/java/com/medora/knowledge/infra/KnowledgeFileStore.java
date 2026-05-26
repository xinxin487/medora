package com.medora.knowledge.infra;

import com.medora.ai.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Component
public class KnowledgeFileStore {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFileStore.class);

    private final MinioClient minioClient;
    private final String bucket;

    public KnowledgeFileStore(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.bucket = properties.getBucket();
    }

    public void store(String objectPath, byte[] content, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
            log.debug("Stored file in MinIO: {}/{}", bucket, objectPath);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to store file in MinIO: " + objectPath, ex);
        }
    }

    public byte[] load(String objectPath) {
        try {
            try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .build())) {
                return stream.readAllBytes();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load file from MinIO: " + objectPath, ex);
        }
    }

    public void delete(String objectPath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .build());
            log.debug("Deleted file from MinIO: {}/{}", bucket, objectPath);
        } catch (Exception ex) {
            log.warn("Failed to delete file from MinIO: {}", objectPath, ex);
        }
    }
}
