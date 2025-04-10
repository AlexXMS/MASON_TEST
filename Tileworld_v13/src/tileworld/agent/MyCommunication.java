package tileworld.agent;

import tileworld.environment.TWEntity;
import tileworld.environment.TWFuelStation;
import tileworld.environment.TWHole;
import tileworld.environment.TWTile;
import java.util.List;
import java.util.ArrayList;

/**
 * MyCommunication
 * 
 * Extends Message class to implement communication between agents
 * Contains the following information:
 * 1. Sender ID
 * 2. Sender location
 * 3. Sender role
 * 4. Discovered entity information (tiles, holes, fuel stations, etc.)
 */
public class MyCommunication extends Message {
    // Message type enumeration
    public enum MessageType {
        TILE_FOUND,           // Found tile
        HOLE_FOUND,          // Found hole
        FUEL_STATION_FOUND,  // Found fuel station
        TILE_PICKED_UP,      // Picked up tile
        HOLE_FILLED,         // Filled hole
        FUEL_LOW,           // Low fuel
        REQUEST_HELP,       // Request help
        OFFER_HELP,         // Offer help
        TASK_COMPLETED      // Task completed
    }
    
    private String senderId;
    private int senderX;
    private int senderY;
    private String senderRole;
    private TWEntity discoveredEntity;
    private int fuelLevel;
    private int carriedTiles;
    private boolean isProcessed; // Marks whether the message has been processed
    private long timestamp; // Message timestamp
    private String messageContent; // New message content field
    private boolean isBroadcast; // Whether it's a broadcast message
    private int messagePriority; // Message priority
    private MessageType messageType;
    private int urgencyLevel; // Urgency level: 1-5, 5 being the most urgent
    private List<String> processedBy;  // Records which agents have processed this message

    public MyCommunication(String senderId, String to, MessageType messageType, TWEntity discoveredEntity) {
        // If it's a broadcast message, set to to null so all agents can receive it
        super(senderId, "ALL".equals(to) ? null : to, messageType.toString());
        this.senderId = senderId;
        this.discoveredEntity = discoveredEntity;
        this.messageType = messageType;
        this.isProcessed = false;
        
        // Set message content based on message type
        String messageContent;
        switch (messageType) {
            case TILE_PICKED_UP:
                messageContent = "TILE_PICKED_UP";
                break;
            case TILE_FOUND:
                messageContent = "Entity found";
                break;
            case FUEL_STATION_FOUND:
                messageContent = "Fuel station found";
                break;
            default:
                messageContent = "Unknown message type";
                break;
        }
        setMessage(messageContent);
        
        this.timestamp = System.currentTimeMillis();
        this.isBroadcast = to.equals("ALL");
        this.messagePriority = calculatePriority(messageType, discoveredEntity);
        this.urgencyLevel = calculateUrgencyLevel(messageType);
        this.processedBy = new ArrayList<>();
    }

    private int calculatePriority(MessageType type, TWEntity entity) {
        int basePriority = 0;
        
        switch (type) {
            case FUEL_STATION_FOUND:
                basePriority = 5;
                break;
            case FUEL_LOW:
                basePriority = 4;
                break;
            case REQUEST_HELP:
                basePriority = 3;
                break;
            case TILE_FOUND:
            case HOLE_FOUND:
                basePriority = 2;
                break;
            default:
                basePriority = 1;
        }
        
        // If the message contains an entity, adjust priority based on entity type
        if (entity != null) {
            if (entity instanceof TWFuelStation) {
                basePriority += 2;
            } else if (entity instanceof TWTile) {
                basePriority += 1;
            }
        }
        
        return basePriority;
    }
    
    private int calculateUrgencyLevel(MessageType type) {
        switch (type) {
            case FUEL_LOW:
            case REQUEST_HELP:
                return 5;
            case FUEL_STATION_FOUND:
                return 4;
            case TILE_FOUND:
            case HOLE_FOUND:
                return 3;
            default:
                return 1;
        }
    }

    public boolean isBroadcast() {
        return isBroadcast;
    }

    public int getMessagePriority() {
        return messagePriority;
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

    public String getSenderRole() {
        return senderRole;
    }

    public TWEntity getDiscoveredEntity() {
        return discoveredEntity;
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

    public MessageType getMessageType() {
        return messageType;
    }

    public int getUrgencyLevel() {
        return urgencyLevel;
    }

    /**
     * Check if the message has been processed by a specific agent
     */
    public boolean isProcessedBy(String agentId) {
        return processedBy.contains(agentId);
    }
    
    /**
     * Mark the message as processed by a specific agent
     */
    public void markProcessedBy(String agentId) {
        if (!processedBy.contains(agentId)) {
            processedBy.add(agentId);
        }
    }
    
    /**
     * Check if the message has been processed by all agents
     */
    public boolean isProcessedByAll(List<String> allAgentIds) {
        return processedBy.containsAll(allAgentIds);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Message type: ").append(messageType).append("\n");
        sb.append("Sender: ").append(senderId).append("\n");
        sb.append("Priority: ").append(messagePriority).append("\n");
        sb.append("Urgency level: ").append(urgencyLevel).append("\n");
        if (discoveredEntity != null) {
            sb.append("Entity information: ").append(discoveredEntity.getClass().getSimpleName())
              .append(" at position(").append(discoveredEntity.getX())
              .append(", ").append(discoveredEntity.getY()).append(")");
        }
        return sb.toString();
    }
} 