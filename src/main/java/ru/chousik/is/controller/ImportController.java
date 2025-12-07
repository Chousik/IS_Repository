package ru.chousik.is.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.chousik.is.api.model.ImportJobResponse;
import ru.chousik.is.service.StudyGroupImportService;
import ru.chousik.is.storage.StoredFile;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private final StudyGroupImportService studyGroupImportService;

    @PostMapping(value = "/study-groups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportJobResponse> importStudyGroups(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(studyGroupImportService.importStudyGroups(file));
    }

    @GetMapping("/study-groups")
    public ResponseEntity<List<ImportJobResponse>> getStudyGroupImportHistory() {
        return ResponseEntity.ok(studyGroupImportService.getHistory());
    }

    @GetMapping("/study-groups/{id}/file")
    public ResponseEntity<Resource> downloadImportFile(@PathVariable("id") UUID id) {
        StoredFile storedFile = studyGroupImportService.downloadImportFile(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(storedFile.filename(), StandardCharsets.UTF_8)
                .build();
        MediaType mediaType = storedFile.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(storedFile.contentType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .contentLength(storedFile.size())
                .body(storedFile.resource());
    }
}
