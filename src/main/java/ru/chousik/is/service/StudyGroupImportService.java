package ru.chousik.is.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;
import ru.chousik.is.dto.request.CoordinatesAddRequest;
import ru.chousik.is.dto.request.LocationAddRequest;
import ru.chousik.is.dto.request.PersonAddRequest;
import ru.chousik.is.dto.request.StudyGroupAddRequest;
import ru.chousik.is.dto.response.ImportJobResponse;
import ru.chousik.is.entity.Color;
import ru.chousik.is.entity.Country;
import ru.chousik.is.entity.FormOfEducation;
import ru.chousik.is.entity.ImportJob;
import ru.chousik.is.entity.ImportStatus;
import ru.chousik.is.entity.Semester;
import ru.chousik.is.event.EntityChangeNotifier;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.repository.ImportJobRepository;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StudyGroupImportService {

    private static final String ENTITY_TYPE = "STUDY_GROUP";

    private final StudyGroupService studyGroupService;
    private final ImportJobRepository importJobRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityChangeNotifier entityChangeNotifier;

    public StudyGroupImportService(StudyGroupService studyGroupService,
                                   ImportJobRepository importJobRepository,
                                   PlatformTransactionManager transactionManager,
                                   EntityChangeNotifier entityChangeNotifier) {
        this.studyGroupService = studyGroupService;
        this.importJobRepository = importJobRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.entityChangeNotifier = entityChangeNotifier;
    }

    public ImportJobResponse importStudyGroups(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Выберите непустой YAML-файл для импорта");
        }

        ImportJob job = ImportJob.builder()
                .entityType(ENTITY_TYPE)
                .status(ImportStatus.IN_PROGRESS)
                .filename(file.getOriginalFilename() == null ? "import.yaml" : file.getOriginalFilename())
                .build();
        job = importJobRepository.save(job);
        publishJobChange(job);

        RuntimeException failure = null;
        try {
            List<StudyGroupAddRequest> payloads = parsePayload(file);
            if (payloads.isEmpty()) {
                throw new BadRequestException("Файл импорта не содержит записей");
            }
            job.setTotalRecords(payloads.size());
            List<StudyGroupAddRequest> immutablePayloads = List.copyOf(payloads);
            transactionTemplate.executeWithoutResult(status -> {
                for (StudyGroupAddRequest request : immutablePayloads) {
                    studyGroupService.create(request);
                }
            });
            job.setSuccessCount(payloads.size());
            job.setStatus(ImportStatus.COMPLETED);
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
            throw failure;
        }
        return toResponse(job);
    }

    public List<ImportJobResponse> getHistory() {
        List<ImportJob> jobs = importJobRepository.findAllByEntityTypeOrderByCreatedAtDesc(ENTITY_TYPE);
        return jobs.stream().map(this::toResponse).toList();
    }

    private List<StudyGroupAddRequest> parsePayload(MultipartFile file) {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
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
        CoordinatesAddRequest coordinates = new CoordinatesAddRequest(
                getRequiredLong(coordinatesNode, "x"),
                getRequiredFloat(coordinatesNode, "y")
        );
        PersonAddRequest groupAdmin = null;
        if (node.containsKey("groupAdmin") && node.get("groupAdmin") instanceof Map<?, ?> adminNode) {
            groupAdmin = parsePerson((Map<String, Object>) adminNode);
        }

        return new StudyGroupAddRequest(
                null,
                coordinates,
                studentsCount,
                expelled,
                course,
                transferred,
                form,
                shouldBeExpelled,
                average,
                semester,
                null,
                groupAdmin
        );
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
            location = new LocationAddRequest(
                    getRequiredInteger(rawLocation, "x"),
                    getRequiredDouble(rawLocation, "y"),
                    getRequiredDouble(rawLocation, "z"),
                    getRequiredString(rawLocation, "name")
            );
        }

        return new PersonAddRequest(
                name,
                eyeColor,
                hairColor,
                null,
                location,
                height,
                weight,
                nationality
        );
    }

    private ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse(
                job.getId(),
                job.getEntityType(),
                job.getStatus(),
                job.getFilename(),
                job.getTotalRecords(),
                job.getSuccessCount(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getFinishedAt()
        );
    }

    private String getRequiredString(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new BadRequestException("Поле '%s' обязательно".formatted(key));
        }
        return value.toString();
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

    private <E extends Enum<E>> E parseEnum(Object value, Class<E> type, String field, boolean optional) {
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
            throw new BadRequestException("Недопустимое значение '%s' для поля '%s'".formatted(value, field));
        }
    }

    private void publishJobChange(ImportJob job) {
        if (entityChangeNotifier != null) {
            entityChangeNotifier.publish("IMPORT_JOB", "UPDATED", toResponse(job));
        }
    }
}
