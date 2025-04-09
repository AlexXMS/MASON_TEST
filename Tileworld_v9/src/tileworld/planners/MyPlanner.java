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
    
    // 添加agent角色枚举
    private enum AgentRole {
        EXPLORER,      // 探索者
        TILE_COLLECTOR // 收集tile并填入hole
    }
    
    private AgentRole currentRole = AgentRole.EXPLORER;

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
        // 检查是否发现了加油站
        if (!foundFuelStation) {
            // 首先检查记忆中的加油站
            TWEntity entity = me.getMemory().getClosestObjectInSensorRange(TWFuelStation.class);
            if (entity instanceof TWFuelStation) {
                foundFuelStation = true;
                fuelStation = (TWFuelStation) entity;
            }
        }

        // 根据角色生成不同的计划
        String role = ((MyAgent)me).getRole();
        
        if (role.equals("EXPLORER")) {
            return generateExplorerPlan();
        } else if (role.equals("TILE_COLLECTOR")) {
            return generateTileCollectorPlan();
        } else {
            return generateExplorerPlan();
        }
    }

    private TWPath generateExplorerPlan() {
        // 如果当前位置已经到达目标位置，生成下一个目标
        if (me.getX() == currentGoal.x && me.getY() == currentGoal.y) {
            generateNextGoal();
        }

        // 使用A*寻路避开障碍物
        TWPath newPath = pathGenerator.findPath(me.getX(), me.getY(), currentGoal.x, currentGoal.y);
        if (newPath != null && newPath.getpath() != null && !newPath.getpath().isEmpty()) {
            currentPath = newPath;
            return currentPath;
        }

        // 如果找不到路径，尝试生成新的目标
        generateNextGoal();
        currentPath = pathGenerator.findPath(me.getX(), me.getY(), currentGoal.x, currentGoal.y);
        return currentPath;
    }

    private TWPath generateTileCollectorPlan() {
        // 获取当前携带的tile数量
        int carriedTileCount = ((MyAgent)me).CountTile();

        // 1. 首先检查感知范围内的实体
        int sensorRange = ((MyAgent)me).getSensorRange();
        for (int i = -sensorRange; i <= sensorRange; i++) {
            for (int j = -sensorRange; j <= sensorRange; j++) {
                int checkX = me.getX() + i;
                int checkY = me.getY() + j;
                
                // 检查位置是否有效
                if (checkX >= 0 && checkX < environment.getxDimension() &&
                    checkY >= 0 && checkY < environment.getyDimension()) {
                    
                    TWEntity entity = (TWEntity) environment.getObjectGrid().get(checkX, checkY);
                    if (entity != null) {
                        // 如果携带了tile，优先寻找hole
                        if (carriedTileCount > 0 && entity instanceof TWHole) {
                            if (me.getX() == checkX && me.getY() == checkY) {
                                ((MyAgent)me).setNextAction(TWAction.PUTDOWN);
                                return null;
                            }
                            return pathGenerator.findPath(me.getX(), me.getY(), checkX, checkY);
                        }
                        // 如果没有携带tile或tile数量不足，寻找tile
                        else if (carriedTileCount < 3 && entity instanceof TWTile) {
                            if (me.getX() == checkX && me.getY() == checkY) {
                                ((MyAgent)me).setNextAction(TWAction.PICKUP);
                                return null;
                            }
                            return pathGenerator.findPath(me.getX(), me.getY(), checkX, checkY);
                        }
                    }
                }
            }
        }

        // 2. 如果感知范围内没有找到合适的实体，从记忆中寻找
        if (carriedTileCount > 0) {
            // 寻找最近的hole
            TWEntity hole = me.getMemory().getClosestObjectInSensorRange(TWHole.class);
            if (hole != null) {
                if (me.getX() == hole.getX() && me.getY() == hole.getY()) {
                    ((MyAgent)me).setNextAction(TWAction.PUTDOWN);
                    return null;
                }
                return pathGenerator.findPath(me.getX(), me.getY(), hole.getX(), hole.getY());
            }
        } else {
            // 寻找最近的tile
            TWEntity tile = me.getMemory().getClosestObjectInSensorRange(TWTile.class);
            if (tile != null) {
                if (me.getX() == tile.getX() && me.getY() == tile.getY()) {
                    ((MyAgent)me).setNextAction(TWAction.PICKUP);
                    return null;
                }
                return pathGenerator.findPath(me.getX(), me.getY(), tile.getX(), tile.getY());
            }
        }

        // 3. 如果记忆中也找不到，检查消息中的实体信息
        ArrayList<Message> messages = me.getEnvironment().getMessages();
        if (messages != null) {
            for (Message msg : messages) {
                if (msg instanceof MyCommunication) {
                    MyCommunication comm = (MyCommunication) msg;
                    TWEntity entity = comm.getDiscoveredEntity();
                    if (entity != null) {
                        if (carriedTileCount > 0 && entity instanceof TWHole) {
                            return pathGenerator.findPath(me.getX(), me.getY(), entity.getX(), entity.getY());
                        } else if (carriedTileCount < 3 && entity instanceof TWTile) {
                            return pathGenerator.findPath(me.getX(), me.getY(), entity.getX(), entity.getY());
                        }
                    }
                }
            }
        }

        return null;
    }

    private void generateNextGoal() {
        int maxX = environment.getxDimension() - 1;
        int maxY = environment.getyDimension() - 1;
        
        // 计算下一个目标位置
        int nextX, nextY;
        
        // 如果是第一次生成目标，从当前位置开始探索
        if (isInitialMove) {
            nextX = me.getX();
            nextY = me.getY();
            isInitialMove = false;
            currentDirection = 0; // 初始方向向右
            currentRow = nextY;
        } else {
            // 如果到达边界，向下移动一行并改变方向
            if ((currentDirection == 0 && me.getX() >= maxX) || 
                (currentDirection == 1 && me.getX() <= 0)) {
                
                // 向下移动7格
                currentRow += 7;
                
                // 检查是否超出底部边界
                if (currentRow > maxY) {
                    // 只有当前行真正超出地图底部时才重置到顶部
                    currentRow = 0;
                } else {
                    // 如果接近底部但不会超出，就移动到最后一行
                    currentRow = Math.min(currentRow, maxY);
                }
                
                // 切换水平移动方向
                currentDirection = 1 - currentDirection;
                
                // 设置新的目标位置：当前x位置和新的y位置
                nextX = me.getX();
                nextY = currentRow;
                
            } else {
                // 正常的水平移动
                if (currentDirection == 0) {
                    // 向右移动
                    nextX = Math.min(me.getX() + 7, maxX);
                } else {
                    // 向左移动
                    nextX = Math.max(me.getX() - 7, 0);
                }
                nextY = currentRow;
            }
        }
        
        // 确保坐标在有效范围内
        nextX = Math.max(0, Math.min(nextX, maxX));
        nextY = Math.max(0, Math.min(nextY, maxY));
        
        // 更新目标位置
        currentGoal = new Int2D(nextX, nextY);
    }

    @Override
    public boolean hasPlan() {
        if (currentRole == AgentRole.TILE_COLLECTOR) {
            // 对于TILE_COLLECTOR角色，总是尝试生成新计划
            return true;
        }
        return currentPath != null && currentPath.getpath() != null && !currentPath.getpath().isEmpty();
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
        // 如果当前没有路径，尝试生成新路径
        if (currentPath == null || currentPath.getpath() == null || currentPath.getpath().isEmpty()) {
            TWPath newPath = generatePlan();
            if (newPath != null && newPath.getpath() != null && !newPath.getpath().isEmpty()) {
                currentPath = newPath;
            } else {
                // 如果无法生成新路径，停留在原地
                return TWDirection.Z;
            }
        }

        // 执行当前路径的下一步
        if (currentPath != null && currentPath.getpath() != null && !currentPath.getpath().isEmpty()) {
            TWPathStep step = currentPath.getpath().removeFirst();
            if (step != null && step.getDirection() != null) {
                return step.getDirection();
            }
        }

        // 如果所有尝试都失败，停留在原地
        return TWDirection.Z;
    }

    // 添加辅助方法：获取随机方向
    private TWDirection getRandomDirection() {
        TWDirection[] directions = {TWDirection.E, TWDirection.N, TWDirection.W, TWDirection.S};
        return directions[(int)(Math.random() * directions.length)];
    }

    private TWThought moveTo(TWEntity entity) {
        if (entity == null) {
            return new TWThought(TWAction.MOVE, TWDirection.Z);
        }
        return moveTo(entity.getX(), entity.getY());
    }

    private TWThought moveTo(int x, int y) {
        TWPath path = pathGenerator.findPath(me.getX(), me.getY(), x, y);
        if (path == null || path.getpath() == null || path.getpath().isEmpty()) {
            return new TWThought(TWAction.MOVE, TWDirection.Z);
        }
        TWPathStep step = path.getpath().removeFirst();
        return new TWThought(TWAction.MOVE, step.getDirection());
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

    private boolean isValidMove(int newX, int newY) {
        // 检查新位置是否在地图范围内
        if (!environment.isValidLocation(newX, newY)) {
            return false;
        }
        
        // 检查新位置是否被阻挡
        if (me.getMemory().isCellBlocked(newX, newY)) {
            return false;
        }
        
        // 检查新位置是否与当前位置相同
        if (newX == me.getX() && newY == me.getY()) {
            return false;
        }
        
        return true;
    }

    public void processMessage(Message message) {
        if (message instanceof MyCommunication) {
            MyCommunication comm = (MyCommunication) message;
            TWEntity entity = comm.getDiscoveredEntity();
            if (entity != null) {
                // 更新计划中的实体信息
                if (entity instanceof TWTile) {
                    tiles.add((TWTile) entity);
                } else if (entity instanceof TWHole) {
                    holes.add((TWHole) entity);
                } else if (entity instanceof TWFuelStation) {
                    fuelStations.add((TWFuelStation) entity);
                }
            }
        }
    }

    public void setFuelStation(TWFuelStation station) {
        this.fuelStation = station;
        this.foundFuelStation = true;
        System.out.println("Planner更新：加油站位置在(" + station.getX() + ", " + station.getY() + ")");
    }
}

