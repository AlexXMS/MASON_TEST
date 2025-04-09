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
    private String role;
    private MyPlanner planner;
    private TWAction nextAction = null;
    private int sensorRange = 5; // 添加感知范围变量

    public int getSensorRange() {
        return sensorRange;
    }

    public MyAgent(String name, String role, int xpos, int ypos, TWEnvironment env, double fuelLevel) {
        super(xpos,ypos,env,fuelLevel);
        this.name = name;
        this.role = role;
        // 使用MyMemory替代默认的TWAgentWorkingMemory
        this.memory = new MyMemory(this, env.schedule, env.getxDimension(), env.getyDimension());
        // 初始化planner
        this.planner = new MyPlanner(this, env);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String newRole) {
        this.role = newRole;
    }

    // 添加辅助方法：获取携带的tile数量
    private int getCarriedTileCount() {
        return this.hasTile() ? 1 : 0;
    }

    public void setNextAction(TWAction action) {
        this.nextAction = action;
    }

    @Override
    public TWThought think() {
        // 紧急加油检查：如果燃料低于100，立即中断当前路径去加油
        if (fuelLevel < 100) {
            System.out.println("Agent " + this.name + " 燃料严重不足，紧急加油！当前燃料: " + fuelLevel);
            
            // 检查是否已经发现fuel station
            if (((MyPlanner)planner).hasFoundFuelStation()) {
                TWFuelStation station = ((MyPlanner)planner).getFuelStation();
                System.out.println("Agent " + this.name + " 已发现加油站，位置: (" + station.getX() + ", " + station.getY() + ")");
                
                // 如果已经在加油站位置，进行加油
                if (x == station.getX() && y == station.getY()) {
                    System.out.println("Agent " + this.name + " 已在加油站位置，开始加油");
                    return new TWThought(TWAction.REFUEL, TWDirection.Z);
                }
                
                // 生成到加油站的路径
                TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, station.getX(), station.getY());
                if (path != null && path.getpath() != null && !path.getpath().isEmpty()) {
                    TWPathStep step = path.getpath().removeFirst();
                    System.out.println("Agent " + this.name + " 生成到加油站的路径，下一步: " + step.getDirection());
                    return new TWThought(TWAction.MOVE, step.getDirection());
                } else {
                    System.out.println("Agent " + this.name + " 无法生成到加油站的路径");
                }
            } else {
                System.out.println("Agent " + this.name + " 未发现加油站，在感知范围内寻找");
                // 在感知范围内寻找最近的加油站
                TWEntity entity = memory.getClosestObjectInSensorRange(TWFuelStation.class);
                if (entity != null) {
                    System.out.println("Agent " + this.name + " 在感知范围内发现加油站，位置: (" + entity.getX() + ", " + entity.getY() + ")");
                    TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, entity.getX(), entity.getY());
                    if (path != null && path.getpath() != null && !path.getpath().isEmpty()) {
                        TWPathStep step = path.getpath().removeFirst();
                        System.out.println("Agent " + this.name + " 生成到感知范围内加油站的路径，下一步: " + step.getDirection());
                        return new TWThought(TWAction.MOVE, step.getDirection());
                    } else {
                        System.out.println("Agent " + this.name + " 无法生成到感知范围内加油站的路径");
                    }
                } else {
                    System.out.println("Agent " + this.name + " 在感知范围内未发现加油站");
                }
            }
            // 如果找不到加油站，停留在原地等待其他agent分享加油站位置
            System.out.println("Agent " + this.name + " 无法找到加油站，停留在原地等待消息");
            return new TWThought(TWAction.MOVE, TWDirection.Z);
        }

        // 根据角色执行不同的行为
        if (this.role.equals("TILE_COLLECTOR")) {
            // 如果携带了tile，先检查感知范围内的hole
            if (CountTile() > 0) {
                // 首先检查感知范围内的hole
                TWEntity entity = memory.getClosestObjectInSensorRange(TWHole.class);
                if (entity != null) {
                    // 如果已经在hole位置，执行填补
                    if (x == entity.getX() && y == entity.getY()) {
                        return new TWThought(TWAction.PUTDOWN, TWDirection.Z);
                    }
                    // 使用A*算法生成到hole的路径
                    TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, entity.getX(), entity.getY());
                    if (path != null && path.getpath() != null && !path.getpath().isEmpty()) {
                        TWPathStep step = path.getpath().removeFirst();
                        return new TWThought(TWAction.MOVE, step.getDirection());
                    }
                } else {
                    // 如果感知范围内没有hole，检查记忆中是否有hole
                    TWEntity memoryHole = memory.getNearbyHole(x, y, 100); // 100是时间阈值
                    if (memoryHole != null) {
                        // 使用A*算法生成到记忆中的hole的路径
                        TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, memoryHole.getX(), memoryHole.getY());
                        if (path != null && path.getpath() != null && !path.getpath().isEmpty()) {
                            TWPathStep step = path.getpath().removeFirst();
                            return new TWThought(TWAction.MOVE, step.getDirection());
                        }
                    }
                }
            }
            
            // 如果携带的tile数量不足3个，先检查感知范围内是否有tile
            if (CountTile() < 3) {
                // 首先检查感知范围内的tile
                TWEntity entity = memory.getClosestObjectInSensorRange(TWTile.class);
                if (entity != null) {
                    // 如果已经在tile位置，执行捡起
                    if (x == entity.getX() && y == entity.getY()) {
                        return new TWThought(TWAction.PICKUP, TWDirection.Z);
                    }
                    // 使用A*算法生成到tile的路径
                    TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, entity.getX(), entity.getY());
                    if (path != null && path.getpath() != null && !path.getpath().isEmpty()) {
                        TWPathStep step = path.getpath().removeFirst();
                        return new TWThought(TWAction.MOVE, step.getDirection());
                    }
                } else {
                    // 如果感知范围内没有tile，检查记忆中是否有tile
                    TWEntity memoryTile = memory.getNearbyTile(x, y, 100); // 100是时间阈值
                    if (memoryTile != null) {
                        // 使用A*算法生成到记忆中的tile的路径
                        TWPath path = ((MyPlanner)planner).getPathGenerator().findPath(x, y, memoryTile.getX(), memoryTile.getY());
                        if (path != null && path.getpath() != null && !path.getpath().isEmpty()) {
                            TWPathStep step = path.getpath().removeFirst();
                            return new TWThought(TWAction.MOVE, step.getDirection());
                        }
                    }
                }
            }
        }
        
        // 如果没有特定的任务，执行蛇形探索
        TWDirection direction = planner.execute();
        return new TWThought(TWAction.MOVE, direction);
    }

    // 添加辅助方法：检查移动是否有效
    private boolean isValidMove(int newX, int newY) {
        // 检查新位置是否在地图范围内
        if (!getEnvironment().isValidLocation(newX, newY)) {
            return false;
        }
        
        // 检查新位置是否被阻挡
        if (memory.isCellBlocked(newX, newY)) {
            return false;
        }
        
        // 检查新位置是否与当前位置相同
        if (newX == x && newY == y) {
            return false;
        }
        
        return true;
    }

    // 添加辅助方法：获取方向名称
    private String getDirectionName(TWDirection direction) {
        switch (direction) {
            case E: return "东";
            case N: return "北";
            case W: return "西";
            case S: return "南";
            default: return "未知方向";
        }
    }

    @Override
    protected void act(TWThought thought) {
        if (thought.getAction() == TWAction.PICKUP) {
            // 首先检查是否已经携带了最大数量的tile
            if (CountTile() >= 3) {
                return;
            }
            
            // 同时检查环境和记忆中的tile
            Object memoryObj = memory.getMemoryGrid().get(x, y);
            Object envObj = getEnvironment().getObjectGrid().get(x, y);
            
            if (envObj instanceof TWTile) {
                TWTile tile = (TWTile)envObj;
                // 使用父类的pickUpTile方法
                super.pickUpTile(tile);
                
                // 先从记忆中删除tile
                memory.getMemoryGrid().set(x, y, null);
                
                // 打印捡起tile的坐标和memory
                System.out.println("Agent " + this.name + " 捡起tile，坐标: (" + tile.getX() + ", " + tile.getY() + ")");
                
                // 发送消息给所有agent，通知tile被捡起
                MyCommunication message = new MyCommunication(
                    this.getName(),
                    "ALL",  // 发送给所有agent
                    "TILE_PICKED_UP",  // 消息类型
                    tile  // 被捡起的tile
                );
                // 打印发送消息的信息
                System.out.println("Agent " + this.name + " 发送消息：tile在(" + tile.getX() + ", " + tile.getY() + ")被捡起");
                // 通过环境发送消息
                this.getEnvironment().receiveMessage(message);
                
                // 立即调用communicate()方法处理消息
                this.communicate();
            } else {
                // 如果记忆中有tile但环境中没有，更新记忆
                if (memoryObj instanceof TWTile) {
                    memory.getMemoryGrid().set(x, y, null);
                }
            }
            return;
        }

        if (thought.getAction() == TWAction.PUTDOWN) {
            // 检查当前位置是否有hole
            Object obj = memory.getMemoryGrid().get(x, y);
            if (obj instanceof TWHole) {
                TWHole hole = (TWHole)obj;
                // 检查是否携带了tile
                if (CountTile() > 0) {
                    // 使用父类的putTileInHole方法
                    super.putTileInHole(hole);
                    // 从记忆中删除hole
                    memory.getMemoryGrid().set(x, y, null);
                }
            }
            return;
        }

        if (thought.getAction() == TWAction.REFUEL) {
            // 检查当前位置是否有fuel station
            Object obj = memory.getMemoryGrid().get(x, y);
            if (obj instanceof TWFuelStation) {
                // 使用父类的refuel方法
                super.refuel();
            }
            return;
        }

        // 处理移动动作
        try {
            this.move(thought.getDirection());
            // 移动后立即处理消息
            this.communicate();
        } catch (CellBlockedException ex) {
            // 移动被阻挡，重新规划路径
        }
    }


    private TWDirection getRandomDirection(){

        TWDirection randomDir = TWDirection.values()[this.getEnvironment().random.nextInt(5)];

        if(this.getX()>=this.getEnvironment().getxDimension() ){
            randomDir = TWDirection.W;
        }else if(this.getX()<=1 ){
            randomDir = TWDirection.E;
        }else if(this.getY()<=1 ){
            randomDir = TWDirection.S;
        }else if(this.getY()>=this.getEnvironment().getxDimension() ){
            randomDir = TWDirection.N;
        }

       return randomDir;

    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void communicate() {
        // 获取当前感知范围内的所有实体
        ArrayList<TWEntity> discoveredEntities = new ArrayList<>();
        
        // 检查感知范围内的实体
        for (int i = -sensorRange; i <= sensorRange; i++) {
            for (int j = -sensorRange; j <= sensorRange; j++) {
                int checkX = x + i;
                int checkY = y + j;
                if (checkX >= 0 && checkX < getEnvironment().getxDimension() &&
                    checkY >= 0 && checkY < getEnvironment().getyDimension()) {
                    Object obj = getEnvironment().getObjectGrid().get(checkX, checkY);
                    
                    if (obj != null) {
                        // 发现新实体
                        TWEntity discoveredEntity = (TWEntity) obj;
                        discoveredEntities.add(discoveredEntity);
                        
                        // 如果是加油站，立即广播给所有agent
                        if (discoveredEntity instanceof TWFuelStation) {
                            System.out.println("Agent " + this.name + " 发现加油站，位置: (" + 
                                             discoveredEntity.getX() + ", " + discoveredEntity.getY() + ")");
                            
                            // 更新planner中的加油站信息
                            ((MyPlanner)planner).setFuelStation((TWFuelStation)discoveredEntity);
                            
                            // 发送消息给所有agent
                            MyCommunication message = new MyCommunication(
                                this.getName(),
                                "ALL",  // 广播给所有agent
                                "发现加油站",
                                discoveredEntity
                            );
                            
                            // 打印发送消息的信息
                            System.out.println("Agent " + this.name + " 发送消息：发现加油站在(" + 
                                             discoveredEntity.getX() + ", " + discoveredEntity.getY() + ")");
                            
                            // 通过环境发送消息
                            this.getEnvironment().receiveMessage(message);
                        }
                    }
                }
            }
        }
        
        // 处理接收到的消息
        ArrayList<Message> messages = this.getEnvironment().getMessages();
        if (messages != null && !messages.isEmpty()) {
            for (Message msg : messages) {
                if (msg instanceof MyCommunication) {
                    MyCommunication comm = (MyCommunication) msg;
                    // 如果消息是发送给所有agent或者是发送给当前agent的
                    if (comm.getTo().equals("ALL") || comm.getTo().equals(this.getName())) {
                        // 处理消息
                        receiveMessage(msg);
                    }
                }
            }
        }
    }

    public void receiveMessage(Message message) {
        if (message instanceof MyCommunication) {
            MyCommunication comm = (MyCommunication) message;
            TWEntity entity = comm.getDiscoveredEntity();
            
            // 打印接收到的消息
            System.out.println("Agent " + this.name + " 收到来自 " + comm.getSenderId() + " 的消息: " + 
                             comm.getMessage() + " 实体类型: " + (entity != null ? entity.getClass().getSimpleName() : "null") + 
                             " 位置: (" + (entity != null ? entity.getX() : "null") + "," + (entity != null ? entity.getY() : "null") + ")");
            
            // 处理接收到的消息
            if (entity != null) {
                // 如果是加油站消息
                if (comm.getMessage().equals("发现加油站")) {
                    // 更新记忆中的加油站信息
                    if (memory instanceof MyMemory) {
                        ((MyMemory)memory).updateFromMessage(comm);
                        // 更新planner中的加油站信息
                        ((MyPlanner)planner).setFuelStation((TWFuelStation)entity);
                        System.out.println("Agent " + this.name + " 更新记忆：加油站位置在(" + 
                                         entity.getX() + ", " + entity.getY() + ")");
                    }
                }
            }
        }
    }

    // 获取tile数量
    public int CountTile() {
        return carriedTiles.size();
    }
}


