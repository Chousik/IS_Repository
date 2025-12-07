package ru.chousik.is.storage;

import org.springframework.core.io.Resource;

public record StoredFile(Resource resource, String filename, String contentType, long size) {
}
