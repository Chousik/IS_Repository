package ru.chousik.is.storage;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnBean(MinioClient.class)
@RequiredArgsConstructor
@Slf4j
public class MinioFileStorageService implements FileStorageService {

    private static final DateTimeFormatter FILE_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @jakarta.annotation.PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
        } catch (Exception e) {
            throw new StorageException("Не удалось подготовить файловое хранилище", e);
        }
    }

    @Override
    public StagedFile stage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Файл импорта пустой");
        }
        String originalName = file.getOriginalFilename();
        String sanitizedName = sanitizeFilename(originalName);
        String tempKey = "imports/tmp/%s/%s".formatted(UUID.randomUUID(), sanitizedName);
        String contentType = resolveContentType(file.getContentType());
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(tempKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build();
            minioClient.putObject(args);
            return new StagedFile(properties.bucket(), tempKey, sanitizedName, contentType, file.getSize());
        } catch (IOException e) {
            throw new StorageException("Ошибка чтения файла импорта", e);
        } catch (Exception e) {
            throw new StorageException("Не удалось сохранить файл импорта", e);
        }
    }

    @Override
    public InputStream openStream(StagedFile stagedFile) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(stagedFile.bucket())
                    .object(stagedFile.objectKey())
                    .build());
        } catch (Exception e) {
            throw new StorageException("Не удалось прочитать файл импорта", e);
        }
    }

    @Override
    public StorageCommitResult commit(StagedFile stagedFile, UUID jobId) {
        String finalKey = buildFinalObjectKey(jobId, stagedFile.originalFilename());
        try {
            CopySource source = CopySource.builder()
                    .bucket(stagedFile.bucket())
                    .object(stagedFile.objectKey())
                    .build();
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(finalKey)
                    .source(source)
                    .build());
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(stagedFile.bucket())
                    .object(stagedFile.objectKey())
                    .build());
            return new StorageCommitResult(properties.bucket(), finalKey);
        } catch (Exception e) {
            throw new StorageException("Не удалось завершить сохранение файла импорта", e);
        }
    }

    @Override
    public void rollback(StagedFile stagedFile) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(stagedFile.bucket())
                    .object(stagedFile.objectKey())
                    .build());
        } catch (Exception e) {
            log.warn("Не удалось удалить временный файл импорта {}: {}", stagedFile.objectKey(), e.getMessage());
        }
    }

    @Override
    public StoredFile load(String bucket, String objectKey, String filename, String contentType, Long sizeBytes) {
        if (bucket == null || objectKey == null) {
            throw new StorageException("Файл не найден");
        }
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            Resource resource = new InputStreamResource(stream);
            String resolvedContentType = contentType != null ? contentType : stat.contentType();
            long resolvedSize = sizeBytes != null ? sizeBytes : stat.size();
            String downloadName = filename != null && !filename.isBlank() ? filename : objectKey;
            return new StoredFile(resource, downloadName, resolvedContentType, resolvedSize);
        } catch (Exception e) {
            throw new StorageException("Не удалось загрузить файл импорта", e);
        }
    }

    private String buildFinalObjectKey(UUID jobId, String filename) {
        String timestamp = FILE_TS_FORMATTER.format(OffsetDateTime.now(ZoneOffset.UTC));
        return "imports/%s/%s-%s".formatted(jobId, timestamp, sanitizeFilename(filename));
    }

    private String sanitizeFilename(String original) {
        if (!StringUtils.hasText(original)) {
            return "import-%s.yaml".formatted(UUID.randomUUID());
        }
        String cleaned = original.replace("\\", "/");
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < cleaned.length() - 1) {
            cleaned = cleaned.substring(lastSlash + 1);
        }
        return cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String resolveContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }
}
