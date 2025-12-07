package ru.chousik.is.storage;

public record StagedFile(String bucket, String objectKey, String originalFilename, String contentType, long size) {}
