package ru.chousik.is.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;
import ru.chousik.is.api.model.CoordinatesAddRequest;
import ru.chousik.is.api.model.ImportJobResponse;
import ru.chousik.is.api.model.ImportJobResponse.StatusEnum;
import ru.chousik.is.api.model.LocationAddRequest;
import ru.chousik.is.api.model.PersonAddRequest;
import ru.chousik.is.api.model.StudyGroupAddRequest;
import ru.chousik.is.entity.Color;
import ru.chousik.is.entity.Country;
import ru.chousik.is.entity.FormOfEducation;
import ru.chousik.is.entity.ImportJob;
import ru.chousik.is.entity.ImportStatus;
import ru.chousik.is.entity.Semester;
import ru.chousik.is.event.EntityChangeNotifier;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.exception.NotFoundException;
import ru.chousik.is.repository.ImportJobRepository;
import ru.chousik.is.storage.FileStorageService;
import ru.chousik.is.storage.StagedFile;
import ru.chousik.is.storage.StoredFile;
import ru.chousik.is.storage.StorageCommitResult;

@Service
public class StudyGroupImportService {

    private static final String ENTITY_TYPE = "STUDY_GROUP";

    private final StudyGroupService studyGroupService;
    private final ImportJobRepository importJobRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityChangeNotifier entityChangeNotifier;
    private final FileStorageService fileStorageService;

    public StudyGroupImportService(
            StudyGroupService studyGroupService,
            ImportJobRepository importJobRepository,
            PlatformTransactionManager transactionManager,
            EntityChangeNotifier entityChangeNotifier,
            FileStorageService fileStorageService) {
        this.studyGroupService = studyGroupService;
        this.importJobRepository = importJobRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.entityChangeNotifier = entityChangeNotifier;
        this.fileStorageService = fileStorageService;
    }

    public ImportJobResponse importStudyGroups(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Выберите непустой YAML-файл для импорта");
        }

        StagedFile stagedFile = fileStorageService.stage(file);

        ImportJob job = ImportJob.builder()
                .entityType(ENTITY_TYPE)
                .status(ImportStatus.IN_PROGRESS)
                .filename(
                        file.getOriginalFilename() == null ? "import.yaml" : file.getOriginalFilename())
                .storageContentType(stagedFile.contentType())
                .storageSizeBytes(stagedFile.size())
                .build();
        job = importJobRepository.save(job);
        publishJobChange(job);

        RuntimeException failure = null;
        StorageSync storageSync = new StorageSync();
        try (InputStream stream = fileStorageService.openStream(stagedFile)) {
            List<StudyGroupAddRequest> payloads = parsePayload(stream);
            if (payloads.isEmpty()) {
                throw new BadRequestException("Файл импорта не содержит записей");
            }
            job.setTotalRecords(payloads.size());
            importPayloads(List.copyOf(payloads), job, stagedFile, storageSync);
            job.setSuccessCount(payloads.size());
            job.setStatus(ImportStatus.COMPLETED);
        } catch (IOException ex) {
            failure = new BadRequestException("Не удалось прочитать файл импорта", ex);
            job.setStatus(ImportStatus.FAILED);
            job.setErrorMessage(failure.getMessage());
        } catch (RuntimeException ex) {
            failure = ex;
            job.setStatus(ImportStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
        } finally {
            job.setFinishedAt(LocalDateTime.now());
            job = importJobRepository.save(job);
            publishJobChange(job);
        }

        if (failure != null) {
            if (!storageSync.isCommitted() && !storageSync.isRolledBack()) {
                fileStorageService.rollback(stagedFile);
            }
            throw failure;
        }
        return toResponse(job);
    }

    public List<ImportJobResponse> getHistory() {
        List<ImportJob> jobs = importJobRepository.findAllByEntityTypeOrderByCreatedAtDesc(ENTITY_TYPE);
        return jobs.stream().map(this::toResponse).toList();
    }

    public StoredFile downloadImportFile(UUID jobId) {
        ImportJob job = importJobRepository
                .findById(jobId)
                .orElseThrow(() -> new NotFoundException("Задача импорта %s не найдена".formatted(jobId)));
        if (job.getStorageBucket() == null || job.getStorageObjectKey() == null) {
            throw new NotFoundException("Файл для задачи импорта %s недоступен".formatted(jobId));
        }
        return fileStorageService.load(
                job.getStorageBucket(),
                job.getStorageObjectKey(),
                job.getFilename(),
                job.getStorageContentType(),
                job.getStorageSizeBytes());
    }

    private void importPayloads(
            List<StudyGroupAddRequest> payloads, ImportJob job, StagedFile stagedFile, StorageSync storageSync) {
        transactionTemplate.executeWithoutResult(status -> {
            registerStorageSynchronization(job, stagedFile, storageSync);
            for (StudyGroupAddRequest request : payloads) {
                studyGroupService.create(request);
            }
        });
    }

    private void registerStorageSynchronization(ImportJob job, StagedFile stagedFile, StorageSync storageSync) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                StorageCommitResult result = fileStorageService.commit(stagedFile, job.getId());
                job.setStorageBucket(result.bucket());
                job.setStorageObjectKey(result.objectKey());
                storageSync.markCommitted();
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    fileStorageService.rollback(stagedFile);
                    storageSync.markRolledBack();
                }
            }
        });
    }

    private List<StudyGroupAddRequest> parsePayload(InputStream inputStream) {
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(reader);
            if (!(loaded instanceof Map<?, ?> root)) {
                throw new BadRequestException("Некорректный формат YAML: ожидается объект с ключом groups");
            }
            Object groupsNode = root.get("groups");
            if (!(groupsNode instanceof List<?> groups)) {
                throw new BadRequestException("В YAML-файле отсутствует список 'groups'");
            }
            List<StudyGroupAddRequest> requests = new ArrayList<>();
            for (Object node : groups) {
                if (!(node instanceof Map<?, ?> map)) {
                    continue;
                }
                requests.add(parseGroup((Map<String, Object>) map));
            }
            return requests;
        } catch (IOException e) {
            throw new BadRequestException("Не удалось прочитать файл импорта", e);
        }
    }

    @SuppressWarnings("unchecked")
    private StudyGroupAddRequest parseGroup(Map<String, Object> node) {
        Integer course = getRequiredInteger(node, "course");
        FormOfEducation form = parseEnum(node.get("formOfEducation"), FormOfEducation.class, "formOfEducation");
        Semester semester = parseEnum(node.get("semesterEnum"), Semester.class, "semesterEnum");
        Long studentsCount = getRequiredLong(node, "studentsCount");
        long expelled = getRequiredLong(node, "expelledStudents");
        long transferred = getRequiredLong(node, "transferredStudents");
        long shouldBeExpelled = getRequiredLong(node, "shouldBeExpelled");
        Integer average = getOptionalInteger(node, "averageMark");
        Map<String, Object> coordinatesNode = getRequiredMap(node, "coordinates");
        CoordinatesAddRequest coordinates = new CoordinatesAddRequest()
                .x(getRequiredLong(coordinatesNode, "x"))
                .y(getRequiredFloat(coordinatesNode, "y"));
        PersonAddRequest groupAdmin = null;
        if (node.containsKey("groupAdmin") && node.get("groupAdmin") instanceof Map<?, ?> adminNode) {
            groupAdmin = parsePerson((Map<String, Object>) adminNode);
        }

        return new StudyGroupAddRequest()
                .coordinates(coordinates)
                .studentsCount(studentsCount)
                .expelledStudents(expelled)
                .course(course)
                .transferredStudents(transferred)
                .formOfEducation(form)
                .shouldBeExpelled(shouldBeExpelled)
                .averageMark(average)
                .semesterEnum(semester)
                .groupAdmin(groupAdmin);
    }

    @SuppressWarnings("unchecked")
    private PersonAddRequest parsePerson(Map<String, Object> node) {
        String name = getRequiredString(node, "name");
        Color hairColor = parseEnum(node.get("hairColor"), Color.class, "hairColor");
        Color eyeColor = parseEnum(node.get("eyeColor"), Color.class, "eyeColor", true);
        Long height = getRequiredLong(node, "height");
        Float weight = getRequiredFloat(node, "weight");
        Country nationality = parseEnum(node.get("nationality"), Country.class, "nationality", true);
        LocationAddRequest location = null;
        if (node.containsKey("location") && node.get("location") instanceof Map<?, ?> locationNode) {
            Map<String, Object> rawLocation = (Map<String, Object>) locationNode;
            location = new LocationAddRequest()
                    .x(getRequiredInteger(rawLocation, "x"))
                    .y(getRequiredDouble(rawLocation, "y"))
                    .z(getRequiredDouble(rawLocation, "z"))
                    .name(getRequiredString(rawLocation, "name"));
        }

        return new PersonAddRequest()
                .name(name)
                .eyeColor(eyeColor)
                .hairColor(hairColor)
                .location(location)
                .height(height)
                .weight(weight)
                .nationality(nationality);
    }

    private ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse()
                .id(job.getId())
                .entityType(job.getEntityType())
                .status(StatusEnum.valueOf(job.getStatus().name()))
                .filename(job.getFilename())
                .contentType(job.getStorageContentType())
                .fileSize(job.getStorageSizeBytes())
                .downloadUrl(resolveDownloadUrl(job))
                .totalRecords(job.getTotalRecords())
                .successCount(job.getSuccessCount())
                .errorMessage(job.getErrorMessage())
                .createdAt(
                        job.getCreatedAt() == null
                                ? null
                                : job.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
                .finishedAt(
                        job.getFinishedAt() == null
                                ? null
                                : job.getFinishedAt().atOffset(java.time.ZoneOffset.UTC));
    }

    private String resolveDownloadUrl(ImportJob job) {
        if (job.getId() == null || job.getStorageObjectKey() == null) {
            return null;
        }
        return "/api/v1/imports/study-groups/%s/file".formatted(job.getId());
    }

    private String getRequiredString(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new BadRequestException("Поле '%s' обязательно".formatted(key));
        }
        return value.toString();
    }

    private static final class StorageSync {
        private final AtomicBoolean committed = new AtomicBoolean(false);
        private final AtomicBoolean rolledBack = new AtomicBoolean(false);

        void markCommitted() {
            committed.set(true);
        }

        void markRolledBack() {
            rolledBack.set(true);
        }

        boolean isCommitted() {
            return committed.get();
        }

        boolean isRolledBack() {
            return rolledBack.get();
        }
    }

    private Map<String, Object> getRequiredMap(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new BadRequestException("Ожидался объект '%s'".formatted(key));
        }
        return (Map<String, Object>) map;
    }

    private Long getRequiredLong(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("Поле '%s' должно быть числом".formatted(key));
        }
        long result = number.longValue();
        if (result <= 0) {
            throw new BadRequestException("Поле '%s' должно быть больше 0".formatted(key));
        }
        return result;
    }

    private Integer getRequiredInteger(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("Поле '%s' должно быть целым числом".formatted(key));
        }
        return number.intValue();
    }

    private Integer getOptionalInteger(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new BadRequestException("Поле '%s' должно быть числом".formatted(key));
        }
        return number.intValue();
    }

    private Float getRequiredFloat(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("Поле '%s' должно быть числом".formatted(key));
        }
        return number.floatValue();
    }

    private Double getRequiredDouble(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("Поле '%s' должно быть числом".formatted(key));
        }
        return number.doubleValue();
    }

    private <E extends Enum<E>> E parseEnum(Object value, Class<E> type, String field) {
        return parseEnum(value, type, field, false);
    }

    private <E extends Enum<E>> E parseEnum(
            Object value, Class<E> type, String field, boolean optional) {
        if (value == null) {
            if (optional) {
                return null;
            }
            throw new BadRequestException("Поле '%s' обязательно".formatted(field));
        }
        String normalized = value.toString().trim();
        if (normalized.isEmpty()) {
            if (optional) {
                return null;
            }
            throw new BadRequestException("Поле '%s' не может быть пустым".formatted(field));
        }
        try {
            return Enum.valueOf(type, normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "Недопустимое значение '%s' для поля '%s'".formatted(value, field));
        }
    }

    private void publishJobChange(ImportJob job) {
        if (entityChangeNotifier != null) {
            entityChangeNotifier.publish("IMPORT_JOB", "UPDATED", toResponse(job));
        }
    }
}
