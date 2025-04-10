package tileworld.agent;

import tileworld.environment.TWDirection;
import tileworld.environment.TWEnvironment;
import tileworld.environment.TWFuelStation;
import tileworld.environment.TWEntity;
import tileworld.planners.MyPlanner;
import tileworld.planners.TWPath;
import tileworld.planners.TWPathStep;
import tileworld.exceptions.CellBlockedException;
import sim.field.grid.ObjectGrid2D;
import sim.util.Int2D;
import tileworld.environment.TWObstacle;
import tileworld.environment.TWHole;
import tileworld.environment.TWTile;
import tileworld.agent.Message;
import tileworld.agent.MyCommunication;
import java.util.ArrayList;

public class MyAgent extends TWAgent{
	private String name;
    private MyPlanner planner;
    private TWAction nextAction = null;
    private int sensorRange = 3; // Add sensor range variable
    
    // Add behavior mode enumeration
    enum Mode {
        EXPLORE,      // Explore environment
        COLLECT,      // Collect tiles
        FILL,         // Fill holes
        REFUEL,       // Refuel
        WAIT,         // Wait
        FIND_FUEL_STATION // Find fuel station
    }
    
    private Mode currentMode = Mode.EXPLORE;
    private double fuelThreshold = 100.0; // Fuel threshold
    private int maxCarriedTiles = 3; // Maximum number of tiles that can be carried
    private int explorationStep = 0; // Exploration step counter
    private int lastFuelCheck = 0; // Last fuel check time step

    public int getSensorRange() {
        return sensorRange;
    }

    public MyAgent(String name, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
        super(xpos,ypos,env,fuelLevel);
        this.name = name;
        // Use MyMemory instead of default TWAgentWorkingMemory
        this.memory = new MyMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
        // Initialize planner
        this.planner = new MyPlanner(this, env);
    }

    public void setNextAction(TWAction action) {
        this.nextAction = action;
    }

    // Add state evaluation method
    private void evaluateState() {
        // Check fuel status
        if (fuelLevel < fuelThreshold) {
            currentMode = Mode.FIND_FUEL_STATION;
            return;
        }
        
        // Check tile status
        int carriedTiles = CountTile();
        if (carriedTiles >= maxCarriedTiles) {
            currentMode = Mode.FILL;
            return;
        }
        
        // Check surrounding environment
        TWEntity nearestTile = memory.getClosestObjectInSensorRange(TWTile.class);
        TWEntity nearestHole = memory.getClosestObjectInSensorRange(TWHole.class);
        
        if (carriedTiles > 0 && nearestHole != null) {
            currentMode = Mode.FILL;
        } else if (carriedTiles < maxCarriedTiles && nearestTile != null) {
            currentMode = Mode.COLLECT;
        } else {
            currentMode = Mode.EXPLORE;
        }
    }

    @Override
    public TWThought think() {
        // Periodically evaluate state
        if (explorationStep % 10 == 0) {
            evaluateState();
        }
        explorationStep++;
        
        // Execute corresponding behavior based on current mode
        switch (currentMode) {
            case FIND_FUEL_STATION:
                return handleFuelStationSearch();
            case COLLECT:
                return handleTileCollection();
            case FILL:
                return handleHoleFilling();
            case EXPLORE:
                return handleExploration();
            default:
                return handleExploration();
        }
    }

    private TWThought handleFuelStationSearch() {
        // Check if fuel station has been found
        if (((MyPlanner)planner).hasFoundFuelStation()) {
            TWFuelStation station = ((MyPlanner)planner).getFuelStation();
            if (x == station.getX() && y == station.getY()) {
                return new TWThought(TWAction.REFUEL, TWDirection.Z);
            }
            TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, station.getX(), station.getY());
            if (path != null && !path.getpath().isEmpty()) {
                TWPathStep step = path.getpath().removeFirst();
                return new TWThought(TWAction.MOVE, step.getDirection());
            }
        }
        
        // Look for fuel station in sensor range
        TWEntity entity = memory.getClosestObjectInSensorRange(TWFuelStation.class);
        if (entity != null) {
            TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, entity.getX(), entity.getY());
            if (path != null && !path.getpath().isEmpty()) {
                TWPathStep step = path.getpath().removeFirst();
                return new TWThought(TWAction.MOVE, step.getDirection());
            }
        }
        
        // If fuel station not found, continue exploration
        return handleExploration();
    }

    private TWThought handleTileCollection() {
        if (CountTile() >= maxCarriedTiles) {
            currentMode = Mode.FILL;
            return handleHoleFilling();
        }
        
        TWEntity tile = memory.getClosestObjectInSensorRange(TWTile.class);
        if (tile != null) {
            if (x == tile.getX() && y == tile.getY()) {
                return new TWThought(TWAction.PICKUP, TWDirection.Z);
            }
            TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, tile.getX(), tile.getY());
            if (path != null && !path.getpath().isEmpty()) {
                TWPathStep step = path.getpath().removeFirst();
                return new TWThought(TWAction.MOVE, step.getDirection());
            }
        }
        
        return handleExploration();
    }

    private TWThought handleHoleFilling() {
        if (CountTile() == 0) {
            currentMode = Mode.COLLECT;
            return handleTileCollection();
        }
        
        TWEntity hole = memory.getClosestObjectInSensorRange(TWHole.class);
        if (hole != null) {
            if (x == hole.getX() && y == hole.getY()) {
                return new TWThought(TWAction.PUTDOWN, TWDirection.Z);
            }
            TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, hole.getX(), hole.getY());
            if (path != null && !path.getpath().isEmpty()) {
                TWPathStep step = path.getpath().removeFirst();
                return new TWThought(TWAction.MOVE, step.getDirection());
            }
        }
        
        return handleExploration();
    }

    private TWThought handleExploration() {
        TWDirection direction = planner.execute();
        return new TWThought(TWAction.MOVE, direction);
    }

    @Override
    protected void act(TWThought thought) {
        if (thought.getAction() == TWAction.PICKUP) {
            // First check if maximum number of tiles is already carried
            if (CountTile() >= 3) {
                return;
            }
            
            // Check both environment and memory for tiles
            Object memoryObj = memory.getMemoryGrid().get(x, y);
            Object envObj = getEnvironment().getObjectGrid().get(x, y);
            
            if (envObj instanceof TWTile) {
                TWTile tile = (TWTile)envObj;
                // Use parent class's pickUpTile method
                super.pickUpTile(tile);
                
                // First remove tile from memory
                memory.getMemoryGrid().set(x, y, null);
                
                // Print tile pickup coordinates and memory
                System.out.println("Agent " + this.name + " picked up tile at coordinates: (" + tile.getX() + ", " + tile.getY() + ")");
                
                // Send message to all agents about tile pickup
                MyCommunication message = new MyCommunication(
                    this.getName(),
                    "ALL",
                    MyCommunication.MessageType.TILE_PICKED_UP,
                    tile
                );
                // Print message sending information
                System.out.println("Agent " + this.name + " sent message: tile at (" + tile.getX() + ", " + tile.getY() + ") was picked up");
                // Send message through environment
                this.getEnvironment().receiveMessage(message);
                
                // Immediately call communicate() to process message
                this.communicate();
            } else {
                // If tile exists in memory but not in environment, update memory
                if (memoryObj instanceof TWTile) {
                    memory.getMemoryGrid().set(x, y, null);
                }
            }
            return;
        }

        if (thought.getAction() == TWAction.PUTDOWN) {
            // Check if there is a hole at current position
            Object obj = memory.getMemoryGrid().get(x, y);
            if (obj instanceof TWHole) {
                TWHole hole = (TWHole)obj;
                // Check if carrying a tile
                if (CountTile() > 0) {
                    // Use parent class's putTileInHole method
                    super.putTileInHole(hole);
                    // Remove hole from memory
                    memory.getMemoryGrid().set(x, y, null);
                }
            }
            return;
        }

        if (thought.getAction() == TWAction.REFUEL) {
            // Check if there is a fuel station at current position
            Object obj = memory.getMemoryGrid().get(x, y);
            if (obj instanceof TWFuelStation) {
                // Use parent class's refuel method
                super.refuel();
            }
            return;
        }

        // Handle movement action
        try {
            this.move(thought.getDirection());
            // Process messages immediately after movement
            this.communicate();
        } catch (CellBlockedException ex) {
            // Movement blocked, replan path
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void communicate() {
        // Get all entities in current sensor range
        ArrayList<TWEntity> discoveredEntities = new ArrayList<>();
        
        // Check entities in sensor range
        for (int i = -sensorRange; i <= sensorRange; i++) {
            for (int j = -sensorRange; j <= sensorRange; j++) {
                int checkX = x + i;
                int checkY = y + j;
                if (checkX >= 0 && checkX < getEnvironment().getxDimension() &&
                    checkY >= 0 && checkY < getEnvironment().getyDimension()) {
                    Object obj = getEnvironment().getObjectGrid().get(checkX, checkY);
                    
                    if (obj != null) {
                        // Discover new entity
                        TWEntity discoveredEntity = (TWEntity) obj;
                        discoveredEntities.add(discoveredEntity);
                        
                        // Check if message about this entity has already been sent
                        boolean alreadySent = false;
                        for (Message msg : this.getEnvironment().getMessages()) {
                            if (msg instanceof MyCommunication) {
                                MyCommunication comm = (MyCommunication) msg;
                                if (comm.getDiscoveredEntity() != null &&
                                    comm.getDiscoveredEntity().getX() == discoveredEntity.getX() &&
                                    comm.getDiscoveredEntity().getY() == discoveredEntity.getY()) {
                                    alreadySent = true;
                                    break;
                                }
                            }
                        }
                        
                        if (!alreadySent) {
                            // Send message to other agents
                            MyCommunication message;
                            
                            // Special handling for fuel station
                            if (discoveredEntity instanceof TWFuelStation) {
                                message = new MyCommunication(
                                    this.getName(),
                                    "ALL",
                                    MyCommunication.MessageType.FUEL_STATION_FOUND,
                                    discoveredEntity
                                );
                                // Print message sending information
                                System.out.println("Agent " + this.name + " sent message: found fuel station at (" + 
                                                 discoveredEntity.getX() + ", " + discoveredEntity.getY() + ")");
                                
                                // Send message through environment
                                this.getEnvironment().receiveMessage(message);
                                
                                // Immediately update own memory
                                if (memory instanceof MyMemory) {
                                    ((MyMemory)memory).updateFromMessage(message);
                                }
                                
                                // Update fuel station information in planner
                                if (planner instanceof MyPlanner) {
                                    ((MyPlanner)planner).setFuelStation((TWFuelStation)discoveredEntity);
                                    System.out.println("Agent " + this.name + " updated fuel station information in planner");
                                }
                            } else {
                                message = new MyCommunication(
                                    this.getName(),
                                    "ALL",  // Broadcast to all agents
                                    MyCommunication.MessageType.TILE_FOUND,
                                    discoveredEntity
                                );
                                // Print message sending information
                                System.out.println("Agent " + this.name + " sent message: found entity " + 
                                                 discoveredEntity.getClass().getSimpleName() + 
                                                 " at (" + discoveredEntity.getX() + ", " + discoveredEntity.getY() + ")");
                                
                                // Send message through environment
                                this.getEnvironment().receiveMessage(message);
                                
                                // Immediately update own memory
                                if (memory instanceof MyMemory) {
                                    ((MyMemory)memory).updateFromMessage(message);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Process received messages
        ArrayList<Message> messages = this.getEnvironment().getMessages();
        if (messages != null && !messages.isEmpty()) {
            for (Message msg : messages) {
                if (msg instanceof MyCommunication) {
                    MyCommunication comm = (MyCommunication) msg;
                    
                    // Skip if message has already been processed by current agent
                    if (comm.isProcessedBy(this.getName())) {
                        continue;
                    }
                    
                    // If message is sent to all agents or specifically to current agent
                    String to = comm.getTo();
                    if (to == null || to.equals("ALL") || to.equals(this.getName())) {
                        // Process message
                        receiveMessage(msg);
                        // Mark message as processed by current agent
                        comm.markProcessedBy(this.getName());
                    }
                }
            }
        }
        
        // Print memory contents
        printMemory();
    }

    public void receiveMessage(Message message) {
        if (message instanceof MyCommunication) {
            MyCommunication comm = (MyCommunication) message;
            TWEntity entity = comm.getDiscoveredEntity();
            
            // Print received message
            System.out.println("Agent " + this.name + " received message from " + comm.getSenderId() + ": " + 
                             comm.getMessage() + " Entity type: " + (entity != null ? entity.getClass().getSimpleName() : "null") + 
                             " Location: (" + (entity != null ? entity.getX() : "null") + "," + (entity != null ? entity.getY() : "null") + ")");
            
            // Process received message
            if (entity != null) {
                // Process based on message type
                switch (comm.getMessageType()) {
                    case TILE_PICKED_UP:
                        // Remove picked up tile from memory
                        memory.getMemoryGrid().set(entity.getX(), entity.getY(), null);
                        System.out.println("Agent " + this.name + " received message: tile at (" + entity.getX() + ", " + entity.getY() + ") was picked up, memory updated");
                        break;
                    case TILE_FOUND:
                    case FUEL_STATION_FOUND:
                        // Update entity information in memory
                        if (memory instanceof MyMemory) {
                            // Ensure complete entity information update
                            ((MyMemory)memory).updateFromMessage(comm);
                            // Special handling for fuel station
                            if (entity instanceof TWFuelStation) {
                                System.out.println("Agent " + this.name + " updated memory: fuel station location at (" + 
                                                 entity.getX() + ", " + entity.getY() + ")");
                                // Update fuel station information in planner
                                if (planner instanceof MyPlanner) {
                                    ((MyPlanner)planner).setFuelStation((TWFuelStation)entity);
                                    System.out.println("Agent " + this.name + " updated fuel station information in planner");
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    // Get number of tiles
    public int CountTile() {
        return carriedTiles.size();
    }

    // Print memory contents
    public void printMemory() {
        System.out.println("\nAgent " + this.name + "'s Memory contents:");
        System.out.println("----------------------------------------");
        
        // Get memory grid
        ObjectGrid2D memoryGrid = memory.getMemoryGrid();
        int width = memoryGrid.getWidth();
        int height = memoryGrid.getHeight();
        
        // Traverse memory grid
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Object obj = memoryGrid.get(x, y);
                if (obj != null) {
                    TWEntity entity = (TWEntity) obj;
                    String entityType = entity.getClass().getSimpleName();
                    System.out.println("Location: (" + x + ", " + y + ") - Entity type: " + entityType);
                }
            }
        }
        
        System.out.println("----------------------------------------\n");
    }
}


