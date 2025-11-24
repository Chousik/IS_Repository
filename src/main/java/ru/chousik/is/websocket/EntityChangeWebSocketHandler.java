package ru.chousik.is.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.chousik.is.event.EntityChangeNotifier;

@Component
@RequiredArgsConstructor
public class EntityChangeWebSocketHandler extends TextWebSocketHandler {

    private final EntityChangeNotifier entityChangeNotifier;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        entityChangeNotifier.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        entityChangeNotifier.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        entityChangeNotifier.unregister(session);
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // read-only channel; ignore client messages
    }
}
