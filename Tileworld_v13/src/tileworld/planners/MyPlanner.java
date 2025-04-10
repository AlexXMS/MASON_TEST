/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tileworld.planners;

import sim.util.Int2D;
import tileworld.agent.MyAgent;
import tileworld.agent.TWAction;
import tileworld.agent.TWThought;
import tileworld.agent.Message;
import tileworld.agent.MyCommunication;
import tileworld.environment.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DefaultTWPlanner
 *
 * @author michaellees
 * Created: Apr 22, 2010
 *
 * Copyright michaellees 2010
 *
 * Here is the skeleton for your planner. Below are some points you may want to
 * consider.
 *
 * Description: This is a simple implementation of a Tileworld planner. A plan
 * consists of a series of directions for the agent to follow. Plans are made,
 * but then the environment changes, so new plans may be needed
 *
 * As an example, your planner could have 4 distinct behaviors:
 *
 * 1. Generate a random walk to locate a Tile (this is triggered when there is
 * no Tile observed in the agents memory
 *
 * 2. Generate a plan to a specified Tile (one which is nearby preferably,
 * nearby is defined by threshold - @see TWEntity)
 *
 * 3. Generate a random walk to locate a Hole (this is triggered when the agent
 * has (is carrying) a tile but doesn't have a hole in memory)
 *
 * 4. Generate a plan to a specified hole (triggered when agent has a tile,
 * looks for a hole in memory which is nearby)
 *
 * The default path generator might use an implementation of A* for each of the behaviors
 *
 */
public class MyPlanner implements TWPlanner {
    private final MyAgent me;
    private final TWEnvironment environment;
    private final AstarPathGenerator pathGenerator;
    private TWPath currentPath;
    private Int2D currentGoal;
    private boolean isInitialMove = true;
    private int currentDirection = 0; // 0: right, 1: left
    private int currentRow = 0;
    private boolean foundFuelStation = false;
    private TWFuelStation fuelStation = null;
    
    // Add task-related fields
    private enum TaskType {
        COLLECT_TILE,
        FILL_HOLE,
        REFUEL,
        EXPLORE
    }
    
    private TaskType currentTask = TaskType.EXPLORE;
    private Int2D taskLocation = null;
    private int taskPriority = 0;
    private long taskStartTime = 0;
    private static final long TASK_TIMEOUT = 1000; // Task timeout (milliseconds)
    
    // Add path planning related fields
    private List<Int2D> visitedLocations = new ArrayList<>();
    private static final int MAX_VISITED_HISTORY = 100;
    private double explorationEfficiency = 0.0;

    private List<TWTile> tiles = new ArrayList<>();
    private List<TWHole> holes = new ArrayList<>();
    private List<TWFuelStation> fuelStations = new ArrayList<>();

    public MyPlanner(MyAgent me, TWEnvironment environment) {
        this.me = me;
        this.environment = environment;
        this.pathGenerator = new AstarPathGenerator(environment, me, Integer.MAX_VALUE);
        this.currentGoal = new Int2D(me.getX(), me.getY());
    }

    @Override
    public TWPath generatePlan() {
        // Check if task has timed out
        if (currentTask != TaskType.EXPLORE && 
            System.currentTimeMillis() - taskStartTime > TASK_TIMEOUT) {
            currentTask = TaskType.EXPLORE;
            taskLocation = null;
        }
        
        // Generate plan based on current task
        switch (currentTask) {
            case COLLECT_TILE:
                return generateTileCollectionPlan();
            case FILL_HOLE:
                return generateHoleFillingPlan();
            case REFUEL:
                return generateRefuelPlan();
            case EXPLORE:
                return generateExplorationPlan();
            default:
                return generateExplorationPlan();
        }
    }

    private TWPath generateTileCollectionPlan() {
        // If already at target location, execute pickup action
        if (taskLocation != null && 
            me.getX() == taskLocation.x && 
            me.getY() == taskLocation.y) {
            me.setNextAction(TWAction.PICKUP);
            return null;
        }
        
        // If target location is invalid, search for tile again
        if (taskLocation == null || !isValidLocation(taskLocation.x, taskLocation.y)) {
            TWEntity tile = findNearestTile();
            if (tile != null) {
                taskLocation = new Int2D(tile.getX(), tile.getY());
                taskStartTime = System.currentTimeMillis();
            } else {
                currentTask = TaskType.EXPLORE;
                return generateExplorationPlan();
            }
        }
        
        // Generate path to target location
        return pathGenerator.findPath(me.getX(), me.getY(), taskLocation.x, taskLocation.y);
    }
    
    private TWPath generateHoleFillingPlan() {
        // If already at target location, execute putdown action
        if (taskLocation != null && 
            me.getX() == taskLocation.x && 
            me.getY() == taskLocation.y) {
            me.setNextAction(TWAction.PUTDOWN);
            return null;
        }
        
        // If target location is invalid, search for hole again
        if (taskLocation == null || !isValidLocation(taskLocation.x, taskLocation.y)) {
            TWEntity hole = findNearestHole();
            if (hole != null) {
                taskLocation = new Int2D(hole.getX(), hole.getY());
                taskStartTime = System.currentTimeMillis();
            } else {
                currentTask = TaskType.EXPLORE;
                return generateExplorationPlan();
            }
        }
        
        // Generate path to target location
        return pathGenerator.findPath(me.getX(), me.getY(), taskLocation.x, taskLocation.y);
    }
    
    private TWPath generateRefuelPlan() {
        // If already at fuel station location, execute refuel action
        if (taskLocation != null && 
            me.getX() == taskLocation.x && 
            me.getY() == taskLocation.y) {
            me.setNextAction(TWAction.REFUEL);
            return null;
        }
        
        // If target location is invalid, search for fuel station again
        if (taskLocation == null || !isValidLocation(taskLocation.x, taskLocation.y)) {
            TWEntity station = findNearestFuelStation();
            if (station != null) {
                taskLocation = new Int2D(station.getX(), station.getY());
                taskStartTime = System.currentTimeMillis();
            } else {
                currentTask = TaskType.EXPLORE;
                return generateExplorationPlan();
            }
        }
        
        // Generate path to target location
        return pathGenerator.findPath(me.getX(), me.getY(), taskLocation.x, taskLocation.y);
    }
    
    private TWPath generateExplorationPlan() {
        // If current position has reached target position, generate next goal
        if (me.getX() == currentGoal.x && me.getY() == currentGoal.y) {
            generateNextGoal();
        }
        
        // Use A* pathfinding to avoid obstacles
        TWPath newPath = pathGenerator.findPath(me.getX(), me.getY(), currentGoal.x, currentGoal.y);
        if (newPath != null && newPath.getpath() != null && !newPath.getpath().isEmpty()) {
            currentPath = newPath;
            return currentPath;
        }
        
        // If no path found, try to generate new goal
        generateNextGoal();
        currentPath = pathGenerator.findPath(me.getX(), me.getY(), currentGoal.x, currentGoal.y);
        return currentPath;
    }
    
    private void generateNextGoal() {
        int maxX = environment.getxDimension() - 1;
        int maxY = environment.getyDimension() - 1;
        
        // Calculate next target position
        int nextX, nextY;
        
        if (isInitialMove) {
            nextX = me.getX();
            nextY = me.getY();
            isInitialMove = false;
            currentDirection = 0;
            currentRow = nextY;
        } else {
            // If reached boundary, move down one row and change direction
            if ((currentDirection == 0 && me.getX() >= maxX) || 
                (currentDirection == 1 && me.getX() <= 0)) {
                currentRow += 7;
                if (currentRow > maxY) {
                    currentRow = 0;
                } else {
                    currentRow = Math.min(currentRow, maxY);
                }
                currentDirection = 1 - currentDirection;
                nextX = me.getX();
                nextY = currentRow;
            } else {
                nextX = me.getX() + (currentDirection == 0 ? 1 : -1);
                nextY = me.getY();
            }
        }
        
        // Record visited locations
        Int2D newLocation = new Int2D(nextX, nextY);
        visitedLocations.add(newLocation);
        if (visitedLocations.size() > MAX_VISITED_HISTORY) {
            visitedLocations.remove(0);
        }
        
        // Update exploration efficiency
        updateExplorationEfficiency();
        
        currentGoal = new Int2D(nextX, nextY);
    }
    
    private void updateExplorationEfficiency() {
        int uniqueLocations = (int) visitedLocations.stream().distinct().count();
        explorationEfficiency = (double) uniqueLocations / visitedLocations.size();
    }
    
    private TWEntity findNearestTile() {
        // First check for tiles within sensor range
        TWEntity tile = me.getMemory().getClosestObjectInSensorRange(TWTile.class);
        if (tile != null) {
            return tile;
        }
        
        // If none in sensor range, search in memory
        return me.getMemory().getNearbyTile(me.getX(), me.getY(), 100);
    }
    
    private TWEntity findNearestHole() {
        // First check for holes within sensor range
        TWEntity hole = me.getMemory().getClosestObjectInSensorRange(TWHole.class);
        if (hole != null) {
            return hole;
        }
        
        // If none in sensor range, search in memory
        return me.getMemory().getNearbyHole(me.getX(), me.getY(), 100);
    }
    
    private TWEntity findNearestFuelStation() {
        // First check for fuel stations within sensor range
        TWEntity station = me.getMemory().getClosestObjectInSensorRange(TWFuelStation.class);
        if (station != null) {
            return station;
        }
        
        // If none in sensor range, check if fuel station has been found before
        if (foundFuelStation && fuelStation != null) {
            return fuelStation;
        }
        
        return null;
    }
    
    private boolean isValidLocation(int x, int y) {
        return x >= 0 && x < environment.getxDimension() &&
               y >= 0 && y < environment.getyDimension() &&
               !me.getMemory().isCellBlocked(x, y);
    }
    
    public void setTask(TaskType task, Int2D location) {
        this.currentTask = task;
        this.taskLocation = location;
        this.taskStartTime = System.currentTimeMillis();
    }
    
    public double getExplorationEfficiency() {
        return explorationEfficiency;
    }

    @Override
    public boolean hasPlan() {
        // 总是尝试生成新计划
        return true;
    }

    @Override
    public void voidPlan() {
        currentPath = null;
    }

    @Override
    public Int2D getCurrentGoal() {
        return currentGoal != null ? currentGoal : new Int2D(0, 0);
    }

    @Override
    public TWDirection execute() {
        if (currentPath == null || currentPath.getpath().isEmpty()) {
            currentPath = generatePlan();
            if (currentPath == null || currentPath.getpath().isEmpty()) {
                return TWDirection.Z;
            }
        }
        
        TWPathStep step = currentPath.getpath().removeFirst();
        return step.getDirection();
    }

    public boolean hasFoundFuelStation() {
        return foundFuelStation;
    }

    public TWFuelStation getFuelStation() {
        return fuelStation;
    }

    public AstarPathGenerator getPathGenerator() {
        return pathGenerator;
    }

    public void setFuelStation(TWFuelStation station) {
        this.fuelStation = station;
        this.foundFuelStation = true;
    }
}

