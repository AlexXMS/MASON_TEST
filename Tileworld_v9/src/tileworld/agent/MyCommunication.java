package tileworld.agent;

import tileworld.environment.TWEntity;
import tileworld.environment.TWFuelStation;
import tileworld.environment.TWHole;
import tileworld.environment.TWTile;

/**
 * MyCommunication
 * 
 * 扩展Message类，实现智能体之间的通信
 * 包含以下信息：
 * 1. 发送者ID
 * 2. 发送者位置
 * 3. 发送者角色
 * 4. 发现的实体信息（瓦片、洞、加油站等）
 */
public class MyCommunication extends Message {
    private String senderId;
    private int senderX;
    private int senderY;
    private String senderRole;
    private TWEntity discoveredEntity;
    private int fuelLevel;
    private int carriedTiles;
    private boolean isProcessed; // 标记消息是否已被处理
    private long timestamp; // 消息的时间戳
    private String messageContent; // 添加新的消息内容字段

    public MyCommunication(String from, String to, String message, TWEntity discoveredEntity) {
        super(from, to, message);
        this.discoveredEntity = discoveredEntity;
        this.senderId = from;
        this.isProcessed = false;
        this.timestamp = System.currentTimeMillis();
        this.messageContent = message;
    }

    public void setMessage(String message) {
        this.messageContent = message;
    }

    @Override
    public String getMessage() {
        return messageContent;
    }

    public String getSenderId() {
        return senderId;
    }

    public int getSenderX() {
        return senderX;
    }

    public int getSenderY() {
        return senderY;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public TWEntity getDiscoveredEntity() {
        return discoveredEntity;
    }

    public int getFuelLevel() {
        return fuelLevel;
    }

    public int getCarriedTiles() {
        return carriedTiles;
    }

    public boolean isProcessed() {
        return isProcessed;
    }

    public void setProcessed(boolean processed) {
        this.isProcessed = processed;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("消息来自 ").append(senderId).append("\n");
        if (discoveredEntity != null) {
            sb.append("发现实体: ").append(discoveredEntity.getClass().getSimpleName())
              .append(" 在位置(").append(discoveredEntity.getX())
              .append(", ").append(discoveredEntity.getY()).append(")");
        }
        return sb.toString();
    }
} 