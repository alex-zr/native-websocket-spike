package com.yaypay.config;

import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PeriodicSenderHandler extends TextWebSocketHandler {

    Map<WebSocketSession, Thread> threads = new ConcurrentHashMap<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage textMessage)
            throws InterruptedException, IOException {
        Map<String, String> value = new Gson().fromJson(textMessage.getPayload(), Map.class);

        String message = value.get("message");
        if ("terminated".equals(message)) {
            Thread thread = threads.get(session);
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Thread thread = threads.get(session);
        if (thread != null) {
            thread.interrupt();
            threads.remove(session);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Thread worker = new Thread(() -> {
            for (int i = 0; session.isOpen() && !Thread.currentThread().isInterrupted(); i++) {
                try {
                    Thread.sleep(500);
                    session.sendMessage(new TextMessage(session.getId() + ", " + i));
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        });
        Thread thread = threads.putIfAbsent(session, worker);
        if (thread == null) {
            worker.start();
        } else {
            thread.interrupt();
        }
    }
}