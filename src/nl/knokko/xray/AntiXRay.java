package nl.knokko.xray;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

public class AntiXRay extends JavaPlugin implements Listener, CommandExecutor {
	
	private long totalProcessTime;
	private int totalProcessCount;
	
	private long totalCheckTime;
	private int totalCheckCount;
	
	private Map<ChunkLocation,ChunkData> data = new TreeMap<ChunkLocation,ChunkData>();

	public AntiXRay() {}

	public AntiXRay(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
		super(loader, description, dataFolder, file);
	}	
	
	@Override
	public void onEnable(){
		super.onEnable();
		Bukkit.getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable(){
		super.onDisable();
		Iterator<Entry<ChunkLocation,ChunkData>> it = data.entrySet().iterator();
		while(it.hasNext()){
			Entry<ChunkLocation,ChunkData> entry = it.next();
			entry.getValue().restore(entry.getKey());
		}
		data.clear();
		if(totalProcessCount > 0)
			System.out.println("Average process time was " + (totalProcessTime / totalProcessCount / 1000) + " microseconds.");
		if(totalCheckCount > 0)
			System.out.println("Average check time was " + (totalCheckTime / totalCheckCount / 1000) + " microseconds.");
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkLoad(final ChunkLoadEvent event){
		if(event.getWorld().getEnvironment() == Environment.NORMAL && !hasChunkData(event.getChunk())){
			if(event.isNewChunk()){
				Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){
				
					public void run(){
						safeProcess(event.getChunk());
					}
				});
			}
			else
				safeProcess(event.getChunk());
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkUnload(ChunkUnloadEvent event){
		if(event.getWorld().getEnvironment() == Environment.NORMAL){
			ChunkData cd = getChunkData(event.getChunk());
			if(cd != null){
				cd.restore(event.getChunk());
				data.remove(new ChunkLocation(event.getChunk()));
			}
		}
	}
	
	private void safeProcess(Chunk chunk){
		long startTime = System.nanoTime();
		World world = chunk.getWorld();
		if(world.getEnvironment() != Environment.NORMAL)
			return;
		int x = chunk.getX();
		int z = chunk.getZ();
		ChunkData cdNorth = getChunkData(world.getUID(), x, z - 1);
		ChunkData cdNorthEast = getChunkData(world.getUID(), x + 1, z - 1);
		ChunkData cdEast = getChunkData(world.getUID(), x + 1, z);
		ChunkData cdSouthEast = getChunkData(world.getUID(), x + 1, z + 1);
		ChunkData cdSouth = getChunkData(world.getUID(), x, z + 1);
		ChunkData cdSouthWest = getChunkData(world.getUID(), x - 1, z + 1);
		ChunkData cdWest = getChunkData(world.getUID(), x - 1, z);
		ChunkData cdNorthWest = getChunkData(world.getUID(), x - 1, z - 1);
		boolean north = cdNorth != null;
		boolean east = cdEast != null;
		boolean south = cdSouth != null;
		boolean west = cdWest != null;
		boolean northEast = cdNorthEast != null;
		boolean southEast = cdSouthEast != null;
		boolean southWest = cdSouthWest != null;
		boolean northWest = cdNorthWest != null;
		processChunk(chunk, null, north, east, south, west);
		processIfCan(cdEast, world, x + 1, z, northEast, false, southEast, true);
		processIfCan(cdSouth, world, x, z + 1, true, southEast, false, southWest);
		processIfCan(cdWest, world, x - 1, z, northWest, true, southWest, false);
		processIfCan(cdNorth, world, x, z - 1, false, northEast, true, northWest);
		processIfCan(cdNorthEast, world, x + 1, z - 1, false, false, east, north);
		processIfCan(cdSouthEast, world, x + 1, z + 1, east, false, false, south);
		processIfCan(cdSouthWest, world, x - 1, z + 1, west, south, false, false);
		processIfCan(cdNorthWest, world, x - 1, z - 1, false, north, west, false);
		totalProcessTime += System.nanoTime() - startTime;
		totalProcessCount++;
	}
	
	private void processIfCan(ChunkData cd, World world, int x, int z, boolean north, boolean east, boolean south, boolean west){
		if(cd != null)
			processChunk(world.getChunkAt(x, z), cd, north, east, south, west);
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event){
		onBlockRemove(event.getBlock());
	}
	
	private void onBlockRemove(Block block){
		long startTime = System.nanoTime();
		if(block.getWorld().getEnvironment() != Environment.NORMAL)
			return;
		Chunk chunk = block.getChunk();
		ChunkData cd = getChunkData(chunk);
		if(cd != null){
			/*
			int x = block.getX() - chunk.getX() * 16;
			int y = block.getY();
			int z = block.getZ() - chunk.getZ() * 16;
			cd.onBlockRemove(chunk, (byte) x, (short) y, (byte) z);
			*/
			for(int i = 0; i < cd.index; i++){
				HiddenBlock b = cd.get(i);
				if(isClose(block.getX(), block.getY(), block.getZ(), b.x + chunk.getX() * 16, b.y, b.z + chunk.getZ() * 16)){
					chunk.getBlock(b.x, b.y, b.z).setType(b.type);
					cd.remove(i);
					i--;
				}
			}
		}
		else {
			Bukkit.getLogger().warning("No data for chunk " + block.getChunk() + "; reprocessing chunk...");
			processChunk(block.getChunk(), null, true, true, true, true);
		}
		totalCheckTime += System.nanoTime() - startTime;
		totalCheckCount++;
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void updateExploded(BlockExplodeEvent event){
		if(event.getBlock().getWorld().getEnvironment() != Environment.NORMAL)
			return;
		List<Block> blocks = event.blockList();
		for(Block block : blocks)
			onBlockRemove(block);
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void updateExploded(EntityExplodeEvent event){
		if(event.getEntity().getWorld().getEnvironment() != Environment.NORMAL)
			return;
		List<Block> blocks = event.blockList();
		for(Block block : blocks)
			onBlockRemove(block);
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void checkPiston(BlockPistonExtendEvent event){
		if(event.getBlock().getWorld().getEnvironment() != Environment.NORMAL)
			return;
		List<Block> blocks = event.getBlocks();
		for(Block block : blocks)
			onBlockRemove(block);
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void checkPiston(BlockPistonRetractEvent event){
		if(event.getBlock().getWorld().getEnvironment() != Environment.NORMAL)
			return;
		List<Block> blocks = event.getBlocks();
		for(Block block : blocks)
			onBlockRemove(block);
	}
	
	private void processChunk(Chunk chunk, ChunkData current, boolean hasNorth, boolean hasEast, boolean hasSouth, boolean hasWest){
		if(current == null){
			current = new ChunkData();
			data.put(new ChunkLocation(chunk), current);
		}
		int boundX = hasEast ? 16 : 15;
		int boundZ = hasSouth ? 16 : 15;
		byte minX = (byte) (hasWest ? 0 : 1);
		byte minZ = (byte) (hasNorth ? 0 : 1);
		for(byte cx = minX; cx < boundX; cx++){
			for(short cy = 1; cy < 256; cy++){
				for(byte cz = minZ; cz < boundZ; cz++){
					Block block = chunk.getWorld().getBlockAt(cx + chunk.getX() * 16, cy, cz + chunk.getZ() * 16);
					Material type = block.getType();
					if(needsHide(type) && isHidden(block)){
						current.add(new HiddenBlock(cx, cy, cz, type));
						//use cy - 128 or just cy?
						block.setType(Material.STONE);
					}
				}
			}
		}
		current.trim();
	}
	
	private boolean needsHide(Material t){
		return t == Material.COAL_ORE || t == Material.IRON_ORE || t == Material.LAPIS_ORE || t == Material.GOLD_ORE
				|| t == Material.REDSTONE_ORE || t == Material.DIAMOND_ORE || t == Material.EMERALD_ORE;
	}
	
	private boolean isClose(int x1, int y1, int z1, int x2, int y2, int z2){
		if(x2 == x1 + 1 && y1 == y2 && z1 == z2)
			return true;
		if(x2 == x1 - 1 && y1 == y2 && z1 == z2)
			return true;
		if(x1 == x2 && y1 == y2 + 1 && z1 == z2)
			return true;
		if(x1 == x2 && y1 == y2 - 1 && z1 == z2)
			return true;
		if(x1 == x2 && y1 == y2 && z1 == z2 + 1)
			return true;
		return x1 == x2 && y1 == y2 && z1 == z2 - 1;
	}
	
	private boolean isHidden(Block block){
		if(!block.getRelative(BlockFace.NORTH).getType().isOccluding())
			return false;
		if(!block.getRelative(BlockFace.EAST).getType().isOccluding())
			return false;
		if(!block.getRelative(BlockFace.SOUTH).getType().isOccluding())
			return false;
		if(!block.getRelative(BlockFace.WEST).getType().isOccluding())
			return false;
		if(!block.getRelative(BlockFace.UP).getType().isOccluding())
			return false;
		return block.getRelative(BlockFace.DOWN).getType().isOccluding();
	}
	
	private ChunkData getChunkData(UUID worldID, int chunkX, int chunkZ){
		ChunkLocation cl = new ChunkLocation(worldID, chunkX, chunkZ);
		return data.get(cl);
	}
	
	private boolean hasChunkData(Chunk chunk){
		return hasChunkData(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
	}
	
	private boolean hasChunkData(UUID id, int x, int z){
		return data.containsKey(new ChunkLocation(id, x, z));
	}
	
	private ChunkData getChunkData(Chunk chunk){
		ChunkLocation cl = new ChunkLocation(chunk);
		return data.get(cl);
	}
	
	private static class HiddenBlock {
		
		private final byte x;
		private final short y;
		private final byte z;
		
		private final Material type;
		
		private HiddenBlock(byte x, short y, byte z, Material type){
			this.x = x;
			this.y = y;
			this.z = z;
			this.type = type;
		}
	}
	
	private static class ChunkLocation implements Comparable<ChunkLocation> {
		
		private final int x;
		private final int z;
		
		private final UUID id;
		
		private ChunkLocation(Chunk chunk){
			x = chunk.getX();
			z = chunk.getZ();
			id = chunk.getWorld().getUID();
		}
		
		private ChunkLocation(UUID worldID, int x, int z){
			id = worldID;
			this.x = x;
			this.z = z;
		}
		
		@Override
		public boolean equals(Object other){
			if(other instanceof ChunkLocation){
				ChunkLocation cl = (ChunkLocation) other;
				return cl.x == x && cl.z == z && cl.id.equals(id);
			}
			return false;
		}
		
		@Override
		public String toString(){
			return "ChunkLocation(" + x + "," + z + ")";
		}
		
		public int compareTo(ChunkLocation cl){
			int cid = id.compareTo(cl.id);
			if(cid == 0){
				if(x > cl.x)
					return 1;
				if(x < cl.x)
					return -1;
				if(z > cl.z)
					return 1;
				if(z < cl.z)
					return -1;
				return 0;
			}
			return cid;
		}
	}
	
	private static class ChunkData {
		
		private static final byte BITS_XZ = 4;
		private static final byte BITS_Y = 7;
		private static final byte BITS_TILE = 3;
		
		private static final byte COORD_SIZE = 2 * ChunkData.BITS_XZ + ChunkData.BITS_Y;
		private static final byte SIZE = 2 * COORD_SIZE + BITS_TILE;
		
		private static final byte OFFSET_Y = BITS_XZ;
		private static final byte OFFSET_Z = OFFSET_Y + BITS_Y;
		private static final byte OFFSET_T = OFFSET_Z + BITS_XZ;
		
		private boolean[] data;
		
		private int index;
		
		ChunkData(){
			data = new boolean[300 * SIZE];
		}
		
		void add(HiddenBlock hb){
			if(index * SIZE >= data.length){
				boolean[] newData = new boolean[(data.length + 100) * SIZE];
				System.arraycopy(data, 0, newData, 0, data.length);
				data = newData;
			}
			byteToBinary(hb.x, BITS_XZ, data, index * SIZE);
			shortToBinary(hb.y, BITS_Y, data, index * SIZE + OFFSET_Y);
			byteToBinary(hb.z, BITS_XZ, data, index * SIZE + OFFSET_Z);
			oreToBinary(hb.type, data, index * SIZE + OFFSET_T);
			index++;
		}
		
		void restore(Chunk chunk){
			for(int i = 0; i < index; i++){
				chunk.getBlock(
						byteFromBinary(data, BITS_XZ, i * SIZE), 
						shortFromBinary(data, BITS_Y, i * SIZE + OFFSET_Y), 
						byteFromBinary(data, BITS_XZ, i * SIZE + OFFSET_Z))
						.setType(oreFromBinary(data, i * SIZE + OFFSET_T));
			}
		}
		
		HiddenBlock get(int index){
			return new HiddenBlock(
					byteFromBinary(data, BITS_XZ, index * SIZE), 
					shortFromBinary(data, BITS_Y, index * SIZE + OFFSET_Y), 
					byteFromBinary(data, BITS_XZ, index * SIZE + OFFSET_Z),
					oreFromBinary(data, index * SIZE + OFFSET_T));
		}
		
		void remove(int index){
			System.arraycopy(data, (index + 1) * SIZE, data, index * SIZE, SIZE * (this.index - index - 1));
			this.index--;
		}
		
		void restore(ChunkLocation chunkLocation){
			restore(Bukkit.getWorld(chunkLocation.id).getChunkAt(chunkLocation.x, chunkLocation.z));
		}
		
		/*
		void onBlockRemove(Chunk chunk, byte x, short y, byte z){
			System.out.println("(" + x + "," + y + "," + z + ")");
			//boolean[] b1 = createCoords(x, y, z);
			boolean[] b2 = createCoords((byte) (x - 1), y, z);
			boolean[] b3 = createCoords((byte) (x + 1), y, z);
			boolean[] b4 = createCoords(x, (short) (y - 1), z);
			boolean[] b5 = createCoords(x, (short) (y + 1), z);
			boolean[] b6 = createCoords(x, y, (byte) (z - 1));
			boolean[] b7 = createCoords(x, y, (byte) (z + 1));
			for(int i = 0; i < index; i++){
				if(compareCoords(i, b2) || compareCoords(i, b3) || compareCoords(i, b4) || compareCoords(i, b5) || compareCoords(i, b6) || compareCoords(i, b7)){
					remove(i);
					chunk.getBlock(x, y, z).setType(oreFromBinary(data, i * SIZE + OFFSET_T));
					i--;
				}
			}
		}
		
		private boolean compareCoords(int i, boolean[] b){
			i *= SIZE;
			for(int j = 0; j < COORD_SIZE; j++)
				if(b[j] != data[i + j])
					return false;
			return true;
		}
		
		private boolean[] createCoords(byte x, short y, byte z){
			boolean[] coords = new boolean[COORD_SIZE];
			byteToBinary(x, BITS_XZ, coords, 0);
			shortToBinary(y, BITS_Y, coords, OFFSET_Y);
			byteToBinary(z, BITS_XZ, coords, OFFSET_Z);
			return coords;
		}
		
		*/
		
		void trim(){
			boolean[] newData = new boolean[index * SIZE];
			System.arraycopy(data, 0, newData, 0, index * SIZE);
			data = newData;
		}
		
		private static Material oreFromBinary(boolean[] data, int index){
			boolean b1 = data[index];
			boolean b2 = data[index + 1];
			boolean b3 = data[index + 2];
			if(!b1 && !b2 && !b3)
				return Material.COAL_ORE;
			if(!b1 && !b2 && b3)
				return Material.IRON_ORE;
			if(!b1 && b2 && !b3)
				return Material.LAPIS_ORE;
			if(!b1 && b2 && b3)
				return Material.REDSTONE_ORE;
			if(b1 && !b2 && !b3)
				return Material.GOLD_ORE;
			if(b1 && !b2 && b3)
				return Material.DIAMOND_ORE;
			if(b1 && b2 && !b3)
				return Material.EMERALD_ORE;
			throw new IllegalArgumentException("Unknown combination: [" + b1 + "," + b2 + "," + b3 + "]");
		}
		
		private static void oreToBinary(Material ore, boolean[] dest, int index){
			if(ore == Material.COAL_ORE){
				dest[index] = false;
				dest[index + 1] = false;
				dest[index + 2] = false;
			}
			else if(ore == Material.IRON_ORE){
				dest[index] = false;
				dest[index + 1] = false;
				dest[index + 2] = true;
			}
			else if(ore == Material.LAPIS_ORE){
				dest[index] = false;
				dest[index + 1] = true;
				dest[index + 2] = false;
			}
			else if(ore == Material.REDSTONE_ORE){
				dest[index] = false;
				dest[index + 1] = true;
				dest[index + 2] = true;
			}
			else if(ore == Material.GOLD_ORE){
				dest[index] = true;
				dest[index + 1] = false;
				dest[index + 2] = false;
			}
			else if(ore == Material.DIAMOND_ORE){
				dest[index] = true;
				dest[index + 1] = false;
				dest[index + 2] = true;
			}
			else if(ore == Material.EMERALD_ORE){
				dest[index] = true;
				dest[index + 1] = true;
				dest[index + 2] = false;
			}
		}
		
		private static void shortToBinary(short number, byte bits, boolean[] dest, int index){
			for(byte b = 0; b < bits; b++){
				if(number >= POWERS[bits - b - 1]){
					number -= POWERS[bits - b - 1];
					dest[b + index] = true;
				}
			}
		}
		
		private static void byteToBinary(byte number, byte bits, boolean[] dest, int index){
			for(byte b = 0; b < bits; b++){
				if(number >= POWERS[bits - b - 1]){
					number -= POWERS[bits - b - 1];
					dest[b + index] = true;
				}
			}
		}
		
		private static short shortFromBinary(boolean[] bools, byte bits, int index){
			short number = 0;
			for(byte b = 0; b < bits; b++)
				if(bools[b + index])
					number += POWERS[bits - b - 1];
			return number;
		}
		
		private static byte byteFromBinary(boolean[] bools, byte bits, int index){
			byte number = 0;
			for(byte b = 0; b < bits; b++)
				if(bools[b + index])
					number += POWERS[bits - b - 1];
			return number;
		}
		
		private static final short[] POWERS = {1,2,4,8,16,32,64,128};
		
	}
}
