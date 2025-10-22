package ru.chousik.is.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class EntityChangeNotifier {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public void register(WebSocketSession session) {
        sessions.add(session);
        sendSafe(session, new EntityChange("SYSTEM", "CONNECTED", "ready"));
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public void publish(String entity, String action, Object body) {
        EntityChange change = new EntityChange(entity, action, body);
        List<WebSocketSession> closed = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            if (!sendSafe(session, change)) {
                closed.add(session);
            }
        }
        sessions.removeAll(closed);
    }

    private boolean sendSafe(WebSocketSession session, EntityChange change) {
        try {
            if (!session.isOpen()) {
                return false;
            }
            String payload = objectMapper.writeValueAsString(change);
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (JsonProcessingException e) {
            return true;
        } catch (IOException e) {
            try {
                session.close();
            } catch (IOException ignored) {
            }
            return false;
        }
    }
}
