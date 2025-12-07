package ru.chousik.is.storage;

import java.io.InputStream;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StagedFile stage(MultipartFile file);

    InputStream openStream(StagedFile stagedFile);

    StorageCommitResult commit(StagedFile stagedFile, UUID jobId);

    void rollback(StagedFile stagedFile);

    StoredFile load(String bucket, String objectKey, String filename, String contentType, Long sizeBytes);
}
