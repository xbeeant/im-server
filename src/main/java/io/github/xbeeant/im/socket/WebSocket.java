package io.github.xbeeant.im.socket;

import com.alibaba.fastjson2.JSON;
import io.github.xbeeant.im.util.SequenceGenerator;
import io.github.xbeeant.im.vo.ChatMessage;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;


@Component
@Slf4j
@ServerEndpoint("/websocket/{token}")
public class WebSocket {

    private static final SequenceGenerator sequenceGenerator = new SequenceGenerator(1);
    //concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
    //虽然@Component默认是单例模式的，但springboot还是会为每个websocket连接初始化一个bean，所以可以用一个静态set保存起来。
    //  注：底下WebSocket是当前类名
    private static final CopyOnWriteArraySet<WebSocket> webSockets = new CopyOnWriteArraySet<>();
    // 用来存在线连接用户信息
    private static final ConcurrentHashMap<String, Map<String, Session>> sessionPool = new ConcurrentHashMap<>();
    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;
    /**
     * 用户会话
     */
    private String token;

    /**
     * 链接成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "token") String token) {
        try {
            this.session = session;
            this.token = token;
            webSockets.add(this);
            String uid = tokenToUid(token);
            Map<String, Session> userSession = sessionPool.get(uid);
            if (null == userSession) {
                userSession = new HashMap<>();
            }
            userSession.put(token, session);
            sessionPool.put(token, userSession);
            log.info("【websocket消息】有新的连接，总数为:" + webSockets.size());
        } catch (Exception e) {
            log.error("", e);
        }
    }

    /**
     * 链接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        try {
            webSockets.remove(this);
            sessionPool.remove(this.token);
            log.info("【websocket消息】连接断开，总数为:" + webSockets.size());
        } catch (Exception e) {
            log.error("", e);
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message
     */
    @OnMessage
    public void onMessage(String message) {
        log.info("【websocket消息】收到客户端消息:" + message);
        ChatMessage chatMessage = ChatMessage.builder()
                .id(sequenceGenerator.nextId())
                .content(message)
                .createAt(new Date())
                .build();
        sendAllMessage(chatMessage);
    }

    /**
     * 发送错误时的处理
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("用户错误,原因:" + error.getMessage());
        log.error("", error);
    }


    // 此为广播消息
    public void sendAllMessage(ChatMessage message) {
        log.info("【websocket消息】广播消息:" + message);
        ByteBuffer bf;
        for (WebSocket webSocket : webSockets) {
            try {
                if (webSocket.session.isOpen()) {
                    bf = ByteBuffer.wrap(JSON.toJSONString(message).getBytes(StandardCharsets.UTF_8));
                    webSocket.session.getAsyncRemote().sendBinary(bf);
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    // 此为单点消息(多人)
    public void sendMoreMessage(String[] tokens, String message) {
        for (String token : tokens) {
            sendMessage(message, token);
        }
    }

    // 此为单点消息
    private void sendMessage(String message, String uid) {
        Map<String, Session> sessions = sessionPool.get(uid);
        for (Map.Entry<String, Session> sessionEntry : sessions.entrySet()) {
            Session value = sessionEntry.getValue();
            if (value != null && value.isOpen()) {
                try {
                    log.info("【websocket消息】 单点消息:" + message);
                    value.getAsyncRemote().sendText(message);
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        }
    }

    private String tokenToUid(String token) {
        // todo
        return token;
    }
}
