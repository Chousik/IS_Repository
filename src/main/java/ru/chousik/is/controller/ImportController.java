package ru.chousik.is.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.chousik.is.api.model.ImportJobResponse;
import ru.chousik.is.service.StudyGroupImportService;

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
}
