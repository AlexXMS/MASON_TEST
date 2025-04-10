package tileworld.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.text.html.HTMLDocument;
import sim.engine.Schedule;
import sim.field.grid.ObjectGrid2D;
import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import tileworld.environment.NeighbourSpiral;
import tileworld.Parameters;
import tileworld.environment.TWEntity;


import tileworld.environment.TWHole;
import tileworld.environment.TWObject;
import tileworld.environment.TWObstacle;
import tileworld.environment.TWTile;
import tileworld.environment.TWFuelStation;

/**
 * TWAgentMemory
 * 
 * @author michaellees
 * 
 *         Created: Apr 15, 2010 Copyright michaellees 2010
 * 
 *         Description:
 * 
 *         This class represents the memory of the TileWorld agents. It stores
 *         all objects which is has observed for a given period of time. You may
 *         want to develop an entirely new memory system by extending this one.
 * 
 *         The memory is supposed to have a probabilistic decay, whereby an element is
 *         removed from memory with a probability proportional to the length of
 *         time the element has been in memory. The maximum length of time which
 *         the agent can remember is specified as MAX_TIME. Any memories beyond
 *         this are automatically removed.
 */
public class MyMemory extends TWAgentWorkingMemory{

	/**
	 * Access to Scedule (TWEnvironment) so that we can retrieve the current timestep of the simulation.
	 */
	private Schedule schedule;
	private TWAgent me;
	private static final int MAX_TIME = 10;
	private static final float MEM_DECAY = 0.5f;
	protected Int2D fuelStation;

	private ObjectGrid2D memoryGrid;

	/*
	 * This was originally a queue ordered by the time at which the fact was observed.
	 * However, when updating the memory a queue is very slow.
	 * Here we trade off memory (in that we maintain a complete image of the map)
	 * for speed of update. Updating the memory is a lot more straightforward.
	 */
	private TWAgentPercept[][] objects;
	/**
	 * Number of items recorded in memory, currently doesn't decrease as memory
	 * is not degraded - nothing is ever removed!
	 */
	private int memorySize;

	/**
	 * Stores (for each TWObject type) the closest object within sensor range,
	 * null if no objects are in sensor range
	 */
	private HashMap<Class<?>, TWEntity> closestInSensorRange;
	static private List<Int2D> spiral = new NeighbourSpiral(Parameters.defaultSensorRange * 4).spiral();
	//    private List<TWAgent> neighbouringAgents = new ArrayList<TWAgent>();
	// x, y: the dimension of the grid
	public MyMemory(TWAgent moi, Schedule schedule, int x, int y) {
		super(moi, schedule, x, y);
		this.me = moi;
		this.schedule = schedule;
		this.memoryGrid = new ObjectGrid2D(x, y);
		this.objects = new TWAgentPercept[x][y];
		this.closestInSensorRange = new HashMap<Class<?>, TWEntity>(4);
		this.memorySize = 0;
		this.fuelStation = null;
	}

	/**
	 * Called at each time step, updates the memory map of the agent.
	 * Note that some objects may disappear or be moved, in which case part of
	 * sensed may contain null objects
	 *
	 * Also note that currently the agent has no sense of moving objects, so
	 * an agent may remember the same object at two locations simultaneously.
	 * 
	 * Other agents in the grid are sensed and passed to this function. But it
	 * is currently not used for anything. Do remember that an agent sense itself
	 * too.
	 *
	 * @param sensedObjects bag containing the sensed objects
	 * @param objectXCoords bag containing x coordinates of objects
	 * @param objectYCoords bag containing y coordinates of object
	 * @param sensedAgents bag containing the sensed agents
	 * @param agentXCoords bag containing x coordinates of agents
	 * @param agentYCoords bag containing y coordinates of agents
	 */
	@Override
	public void updateMemory(Bag sensedObjects, IntBag objectXCoords, IntBag objectYCoords, 
						   Bag sensedAgents, IntBag agentXCoords, IntBag agentYCoords) {
		// Check if parameters are null
		if (sensedObjects == null || objectXCoords == null || objectYCoords == null) {
			return;
		}
		
		// Reset closest perceived objects
		if (closestInSensorRange == null) {
			closestInSensorRange = new HashMap<Class<?>, TWEntity>(4);
		} else {
			closestInSensorRange.clear();
		}
		
		// Update each perceived object
		for (int i = 0; i < sensedObjects.size(); i++) {
			TWEntity o = (TWEntity) sensedObjects.get(i);
			if (o == null || !(o instanceof TWObject)) continue;
			
			// Update object in memory
			updateEntityInMemory(o);
		}
		
		// Process perceived other agents
		if (sensedAgents != null && agentXCoords != null && agentYCoords != null) {
			for (int i = 0; i < sensedAgents.size(); i++) {
				TWAgent a = (TWAgent) sensedAgents.get(i);
				if (a == null || a.equals(me)) continue;  // Skip self and null values
				
				// Can add logic for processing other agents here
			}
		}
		
		// Clean up expired memories
		cleanExpiredMemory();
	}
	
	/**
	 * Update memory from message
	 */
	public void updateFromMessage(MyCommunication message) {
		if (message == null || message.getDiscoveredEntity() == null) return;
		
		TWEntity entity = message.getDiscoveredEntity();
		updateEntityInMemory(entity);
		
		// If it's a fuel station, update fuelStation
		if (entity instanceof TWFuelStation) {
			fuelStation = new Int2D(entity.getX(), entity.getY());
		}
	}
	
	/**
	 * Update entity in memory
	 */
	private void updateEntityInMemory(TWEntity entity) {
		if (entity == null || objects == null || memoryGrid == null) return;
		
		try {
			// If there's no object at current position, increase memory size
			if (objects[entity.getX()][entity.getY()] == null) {
				memorySize++;
			}
			
			// Update object in memory
			objects[entity.getX()][entity.getY()] = new TWAgentPercept(entity, this.getSimulationTime());
			memoryGrid.set(entity.getX(), entity.getY(), entity);
			
			// Update closest perceived object
			updateClosest(entity);
		} catch (ArrayIndexOutOfBoundsException e) {
			System.err.println("Error updating memory: entity position out of bounds (" + 
							 entity.getX() + ", " + entity.getY() + ")");
		}
	}
	
	/**
	 * Clean up expired memories
	 */
	private void cleanExpiredMemory() {
		if (objects == null || memoryGrid == null) return;
		
		double currentTime = this.getSimulationTime();
		
		for (int x = 0; x < objects.length; x++) {
			for (int y = 0; y < objects[x].length; y++) {
				TWAgentPercept percept = objects[x][y];
				if (percept != null) {
					// If memory exceeds maximum time, remove directly
					if (currentTime - percept.getT() > MAX_TIME) {
						objects[x][y] = null;
						memoryGrid.set(x, y, null);
						memorySize--;
					}
					// Otherwise decay with probability
					else if (Math.random() < MEM_DECAY) {
						objects[x][y] = null;
						memoryGrid.set(x, y, null);
						memorySize--;
					}
				}
			}
		}
	}
	
	private void updateClosest(TWEntity o) {
		if (o == null) return;
		
		Class<?> type = o.getClass();
		if (!closestInSensorRange.containsKey(type)) {
			closestInSensorRange.put(type, o);
		} else {
			TWEntity current = closestInSensorRange.get(type);
			if (getDistance(me.getX(), me.getY(), o.getX(), o.getY()) < 
				getDistance(me.getX(), me.getY(), current.getX(), current.getY())) {
				closestInSensorRange.put(type, o);
			}
		}
	}
	
	private double getDistance(int x1, int y1, int x2, int y2) {
		return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
	}

	/**
	 * updates memory using 2d array of sensor range - currently not used
	 * @see TWAgentWorkingMemory#updateMemory(sim.util.Bag, sim.util.IntBag, sim.util.IntBag)
	 */
	public void updateMemory(TWEntity[][] sensed, int xOffset, int yOffset) {
		for (int x = 0; x < sensed.length; x++) {
			for (int y = 0; y < sensed[x].length; y++) {
				objects[x + xOffset][y + yOffset] = new TWAgentPercept(sensed[x][y], this.getSimulationTime());
			}
		}
	}

	/**
	 * removes all facts earlier than now - max memory time. 
	 * TODO: Other facts are
	 * remove probabilistically (exponential decay of memory)
	 */
	public void decayMemory() {
		// put some decay on other memory pieces (this will require complete
		// iteration over memory though, so expensive.
		//This is a simple example of how to do this.
		//        for (int x = 0; x < this.objects.length; x++) {
		//       for (int y = 0; y < this.objects[x].length; y++) {
		//           TWAgentPercept currentMemory =  objects[x][y];
		//           if(currentMemory!=null && currentMemory.getT() < schedule.getTime()-MAX_TIME){
		//               memoryGrid.set(x, y, null);
		//               memorySize--;
		//           }
		//       }
		//   }
	}


	public void removeAgentPercept(int x, int y){
		objects[x][y] = null;
	}


	public void removeObject(TWEntity o){
		removeAgentPercept(o.getX(), o.getY());
	}


	/**
	 * @return
	 */
	private double getSimulationTime() {
		return schedule.getTime();
	}

	/**
	 * Finds a nearby tile we have seen less than threshold timesteps ago
	 *
	 * @see TWAgentWorkingMemory#getNearbyObject(int, int, double, java.lang.Class)
	 */
	public TWTile getNearbyTile(int x, int y, double threshold) {
		return (TWTile) this.getNearbyObject(x, y, threshold, TWTile.class);
	}

	/**
	 * Finds a nearby hole we have seen less than threshold timesteps ago
	 *
	 * @see TWAgentWorkingMemory#getNearbyObject(int, int, double, java.lang.Class)
	 */
	public TWHole getNearbyHole(int x, int y, double threshold) {
		return (TWHole) this.getNearbyObject(x, y, threshold, TWHole.class);
	}


	/**
	 * Returns the number of items currently in memory
	 */
	public int getMemorySize() {
		return memorySize;
	}



	/**
	 * Returns the nearest object that has been remembered recently where recently
	 * is defined by a number of timesteps (threshold)
	 *
	 * If no Object is in memory which has been observed in the last threshold
	 * timesteps it returns the most recently observed object. If there are no objects in
	 * memory the method returns null. Note that specifying a threshold of one
	 * will always return the most recently observed object. Specifying a threshold
	 * of MAX_VALUE will always return the nearest remembered object.
	 *
	 * Also note that it is likely that nearby objects are also the most recently observed
	 *
	 *
	 * @param x coordinate from which to check for objects
	 * @param y coordinate from which to check for objects
	 * @param threshold how recently we want to have seen the object
	 * @param type the class of object we're looking for (Must inherit from TWObject, specifically tile or hole)
	 * @return
	 */
	private TWEntity getNearbyObject(int sx, int sy, double threshold, Class<?> type) {
		//If we cannot find an object which we have seen recently, then we want
		//the one with maxTimestamp
		double maxTimestamp = 0;
		TWEntity o = null;
		double time = 0;
		TWEntity ret = null;
		int x, y;
		for (Int2D offset : spiral) {
			x = offset.x + sx;
			y = offset.y + sy;

			if (me.getEnvironment().isInBounds(x, y) && objects[x][y] != null) {
				o = (TWEntity) objects[x][y].getO();//get mem object
				if (type.isInstance(o)) {//if it's not the type we're looking for do nothing

					time = objects[x][y].getT();//get time of memory

					if (this.getSimulationTime() - time <= threshold) {
						//if we found one satisfying time, then return
						return o;
					} else if (time > maxTimestamp) {
						//otherwise record the timestamp and the item in case
						//it's the most recent one we see
						ret = o;
						maxTimestamp = time;
					}
				}
			}
		}

		//this will either be null or the object of Class type which we have
		//seen most recently but longer ago than now-threshold.
		return ret;
	}

	/**
	 * Used for invalidating the plan, returns the object of a particular type
	 * (Tile or Hole) which is closest to the agent and within it's sensor range
	 *
	 * @param type
	 * @return
	 */
	public TWEntity getClosestObjectInSensorRange(Class<?> type) {
		return closestInSensorRange.get(type);
	}

	/**
	 * Is the cell blocked according to our memory?
	 * 
	 * @param tx x position of cell
	 * @param ty y position of cell
	 * @return true if the cell is blocked in our memory
	 */
	public boolean isCellBlocked(int tx, int ty) {

		//no memory at all, so assume not blocked
		if (objects[tx][ty] == null) {
			return false;
		}

		TWEntity e = (TWEntity) objects[tx][ty].getO();
		//is it an obstacle?
		return (e instanceof TWObstacle);
	}

	public ObjectGrid2D getMemoryGrid() {
		return this.memoryGrid;
	}

	/**
	 * 获取记忆中的燃料站位置
	 * @return 燃料站的位置，如果没有找到则返回null
	 */
	public sim.util.Int2D getFuelStation() {
		return this.fuelStation;
	}
}

