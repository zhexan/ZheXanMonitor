package com.example.websocket;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 异常告警 WebSocket 服务端点
 * 用于向前端推送实时异常告警信息
 * @author zhexan
 * @since 2026-03-03
 */
@Slf4j
@Component
@ServerEndpoint("/alarm/{userId}")
public class AlarmWebSocket {
    
    /**
     * 存储所有用户的 Session
     * key: userId, value: Session 列表 (一个用户可能有多个连接)
     */
    private static final Map<Integer, CopyOnWriteArrayList<Session>> userSessionMap = new ConcurrentHashMap<>();
    
    /**
     * 当前在线连接数
     */
    private static int onlineCount = 0;
    
    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Integer userId) {
        // 将用户的 Session 添加到 Map 中
        userSessionMap.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
        
        // 在线数加 1
        addOnlineCount();
        
        log.info("用户 {} 的异常告警 WebSocket 连接已建立，当前在线连接数：{}", userId, onlineCount);
        
        // 发送欢迎消息
        try {
            sendMessage(session, "{\"type\":\"connected\",\"message\":\"告警服务已连接\"}");
        } catch (IOException e) {
            log.error("发送连接成功消息失败", e);
        }
    }
    
    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session, @PathParam("userId") Integer userId) {
        // 从 Map 中删除该 Session
        CopyOnWriteArrayList<Session> sessions = userSessionMap.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessionMap.remove(userId);
            }
        }
        
        // 在线数减 1
        subOnlineCount();
        
        log.info("用户 {} 的异常告警 WebSocket 连接已关闭，当前在线连接数：{}", userId, onlineCount);
    }
    
    /**
     * 收到客户端消息后调用的方法
     */
    @OnError
    public void onError(Session session, Throwable error, @PathParam("userId") Integer userId) {
        log.error("用户 {} 的异常告警 WebSocket 连接出现错误：{}", userId, error.getMessage());
        error.printStackTrace();
    }
    
    /**
     * 实现服务器主动推送消息给指定用户
     * @param userId 用户 ID
     * @param message 要推送的消息
     */
    public void sendMessageToUser(Integer userId, String message) throws IOException {
        CopyOnWriteArrayList<Session> sessions = userSessionMap.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.warn("用户 {} 没有在线的 WebSocket 连接，无法推送告警消息", userId);
            return;
        }
        
        // 遍历该用户的所有 Session 并发送消息
        for (Session session : sessions) {
            sendMessage(session, message);
        }
    }
    
    /**
     * 实现服务器主动推送消息给所有用户
     * @param message 要推送的消息
     */
    public void sendMessageToAll(String message) throws IOException {
        for (Map.Entry<Integer, CopyOnWriteArrayList<Session>> entry : userSessionMap.entrySet()) {
            for (Session session : entry.getValue()) {
                sendMessage(session, message);
            }
        }
    }
    
    /**
     * 向指定的 Session 发送消息
     */
    private void sendMessage(Session session, String message) throws IOException {
        session.getBasicRemote().sendText(message);
    }
    
    /**
     * 获取某个用户的所有 Session
     */
    public static CopyOnWriteArrayList<Session> getSessionsByUserId(Integer userId) {
        return userSessionMap.get(userId);
    }
    
    /**
     * 判断某个用户是否在线
     */
    public static boolean isUserOnline(Integer userId) {
        CopyOnWriteArrayList<Session> sessions = userSessionMap.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
    
    /**
     * 获取在线连接数
     */
    public static synchronized int getOnlineCount() {
        return onlineCount;
    }
    
    /**
     * 在线数加 1
     */
    private static synchronized void addOnlineCount() {
        onlineCount++;
    }
    
    /**
     * 在线数减 1
     */
    private static synchronized void subOnlineCount() {
        onlineCount--;
    }
}
