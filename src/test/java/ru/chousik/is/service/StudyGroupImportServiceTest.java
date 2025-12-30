package ru.chousik.is.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ru.chousik.is.api.model.StudyGroupAddRequest;
import ru.chousik.is.api.model.StudyGroupResponse;
import ru.chousik.is.entity.ImportJob;
import ru.chousik.is.entity.ImportStatus;
import ru.chousik.is.event.EntityChangeNotifier;
import ru.chousik.is.repository.ImportJobRepository;
import ru.chousik.is.storage.FileStorageService;
import ru.chousik.is.storage.StagedFile;
import ru.chousik.is.storage.StorageException;

@ExtendWith(MockitoExtension.class)
class StudyGroupImportServiceTest {

    @Mock
    private StudyGroupService studyGroupService;

    @Mock
    private ImportJobRepository importJobRepository;

    @Mock
    private EntityChangeNotifier entityChangeNotifier;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MultipartFile multipartFile;

    private StudyGroupImportService service;

    private StagedFile stagedFile;

    private final AtomicReference<ImportJob> lastSavedJob = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        PlatformTransactionManager txManager = new NoOpTransactionManager();
        service = new StudyGroupImportService(
                studyGroupService, importJobRepository, txManager, entityChangeNotifier, fileStorageService);

        stagedFile = new StagedFile("bucket", "tmp/object", "payload.yaml", "application/x-yaml", 42);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("payload.yaml");

        when(importJobRepository.save(any())).thenAnswer(invocation -> {
            ImportJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(UUID.randomUUID());
            }
            lastSavedJob.set(snapshot(job));
            return job;
        });
    }

    @Test
    void importFailsWhenStorageUnavailableButRepositoryRemainsUntouched() {
        when(fileStorageService.stage(multipartFile)).thenReturn(stagedFile);
        when(fileStorageService.openStream(stagedFile)).thenThrow(new StorageException("storage offline"));

        StorageException ex = assertThrows(StorageException.class, () -> service.importStudyGroups(multipartFile));
        assertEquals("storage offline", ex.getMessage());

        verifyNoInteractions(studyGroupService);
        verify(fileStorageService, never()).commit(any(), any());
        verify(fileStorageService).rollback(stagedFile);
        assertEquals(ImportStatus.FAILED, lastSavedJob.get().getStatus());
        assertEquals("storage offline", lastSavedJob.get().getErrorMessage());
    }

    @Test
    void databaseFailureRollsBackFileStorage() {
        when(fileStorageService.stage(multipartFile)).thenReturn(stagedFile);
        when(fileStorageService.openStream(stagedFile)).thenReturn(yamlInput(singleGroupYaml()));
        when(studyGroupService.create(any(StudyGroupAddRequest.class)))
                .thenThrow(new IllegalStateException("db down"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> service.importStudyGroups(multipartFile));
        assertEquals("db down", ex.getMessage());

        verify(studyGroupService, times(1)).create(any(StudyGroupAddRequest.class));
        verify(fileStorageService, never()).commit(any(), any());
        verify(fileStorageService).rollback(stagedFile);
        assertEquals(ImportStatus.FAILED, lastSavedJob.get().getStatus());
        assertEquals("db down", lastSavedJob.get().getErrorMessage());
    }

    @Test
    void businessLogicErrorBetweenResourcesTriggersRollback() {
        when(fileStorageService.stage(multipartFile)).thenReturn(stagedFile);
        when(fileStorageService.openStream(stagedFile)).thenReturn(yamlInput(twoGroupsYaml()));
        Answer<StudyGroupResponse> failingAnswer = new Answer<>() {
            private int counter = 0;

            @Override
            public StudyGroupResponse answer(InvocationOnMock invocation) {
                if (counter++ == 0) {
                    return new StudyGroupResponse();
                }
                throw new RuntimeException("business rule violated");
            }
        };
        when(studyGroupService.create(any(StudyGroupAddRequest.class))).thenAnswer(failingAnswer);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.importStudyGroups(multipartFile));
        assertEquals("business rule violated", ex.getMessage());

        verify(studyGroupService, times(2)).create(any(StudyGroupAddRequest.class));
        verify(fileStorageService, never()).commit(any(), any());
        verify(fileStorageService).rollback(stagedFile);
        assertEquals(ImportStatus.FAILED, lastSavedJob.get().getStatus());
        assertEquals("business rule violated", lastSavedJob.get().getErrorMessage());
    }

    private InputStream yamlInput(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private String singleGroupYaml() {
        return "groups:\n"
                + "  - course: 1\n"
                + "    formOfEducation: FULL_TIME_EDUCATION\n"
                + "    semesterEnum: FIRST\n"
                + "    studentsCount: 10\n"
                + "    expelledStudents: 1\n"
                + "    transferredStudents: 1\n"
                + "    shouldBeExpelled: 1\n"
                + "    averageMark: 5\n"
                + "    coordinates:\n"
                + "      x: 1\n"
                + "      y: 1.0\n";
    }

    private String twoGroupsYaml() {
        return singleGroupYaml()
                + "  - course: 2\n"
                + "    formOfEducation: EVENING_CLASSES\n"
                + "    semesterEnum: SECOND\n"
                + "    studentsCount: 12\n"
                + "    expelledStudents: 1\n"
                + "    transferredStudents: 1\n"
                + "    shouldBeExpelled: 1\n"
                + "    averageMark: 4\n"
                + "    coordinates:\n"
                + "      x: 2\n"
                + "      y: 2.0\n";
    }

    private ImportJob snapshot(ImportJob job) {
        return ImportJob.builder()
                .id(job.getId())
                .entityType(job.getEntityType())
                .status(job.getStatus())
                .filename(job.getFilename())
                .totalRecords(job.getTotalRecords())
                .successCount(job.getSuccessCount())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .finishedAt(job.getFinishedAt())
                .storageBucket(job.getStorageBucket())
                .storageObjectKey(job.getStorageObjectKey())
                .storageContentType(job.getStorageContentType())
                .storageSizeBytes(job.getStorageSizeBytes())
                .build();
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            TransactionSynchronizationManager.initSynchronization();
            return new SimpleStatus(definition != null && definition.isReadOnly());
        }

        @Override
        public void commit(TransactionStatus status) {
            SimpleStatus simple = (SimpleStatus) status;
            try {
                triggerBeforeCommit(simple.isReadOnly());
                triggerBeforeCompletion();
                triggerAfterCommit();
                triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            } finally {
                simple.markCompleted();
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Override
        public void rollback(TransactionStatus status) {
            SimpleStatus simple = (SimpleStatus) status;
            try {
                triggerBeforeCompletion();
                triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            } finally {
                simple.markCompleted();
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        private void triggerBeforeCommit(boolean readOnly) {
            for (TransactionSynchronization synchronization : getSynchronizations()) {
                synchronization.beforeCommit(readOnly);
            }
        }

        private void triggerBeforeCompletion() {
            for (TransactionSynchronization synchronization : getSynchronizations()) {
                synchronization.beforeCompletion();
            }
        }

        private void triggerAfterCommit() {
            for (TransactionSynchronization synchronization : getSynchronizations()) {
                synchronization.afterCommit();
            }
        }

        private void triggerAfterCompletion(int status) {
            for (TransactionSynchronization synchronization : getSynchronizations()) {
                synchronization.afterCompletion(status);
            }
        }

        private List<TransactionSynchronization> getSynchronizations() {
            return TransactionSynchronizationManager.getSynchronizations();
        }
    }

    private static final class SimpleStatus implements TransactionStatus {
        private final boolean readOnly;
        private boolean rollbackOnly;
        private boolean completed;

        SimpleStatus(boolean readOnly) {
            this.readOnly = readOnly;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        void markCompleted() {
            this.completed = true;
        }

        @Override
        public boolean isNewTransaction() {
            return true;
        }

        @Override
        public boolean hasSavepoint() {
            return false;
        }

        @Override
        public void setRollbackOnly() {
            this.rollbackOnly = true;
        }

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        @Override
        public void flush() { }

        @Override
        public boolean isCompleted() {
            return completed;
        }

        @Override
        public Object createSavepoint() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseSavepoint(Object savepoint) {
            throw new UnsupportedOperationException();
        }
    }
}
