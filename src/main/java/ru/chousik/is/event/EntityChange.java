package ru.chousik.is.event;

public record EntityChange(String entity, String action, Object data) {
}
