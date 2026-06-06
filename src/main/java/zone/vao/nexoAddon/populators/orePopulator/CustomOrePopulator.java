package zone.vao.nexoAddon.populators.orePopulator;

import com.nexomc.nexo.api.NexoBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CustomOrePopulator extends BlockPopulator {

  private final OrePopulator orePopulator;

  private final Map<String, BlockData> cachedBlocks = new HashMap<>();
  public CustomOrePopulator(OrePopulator orePopulator) {
    this.orePopulator = orePopulator;
  }

  public WorldInfo worldInfo;

  @Override
  public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull LimitedRegion limitedRegion) {
    for (Ore ore : orePopulator.getOres()) {
      if (ore.nexoBlocks != null && NexoBlocks.isNexoStringBlock(ore.nexoBlocks.getItemID()) && NexoBlocks.stringMechanic(ore.nexoBlocks.getItemID()).isSapling()
          || ore.getNexoFurniture() != null ||
          (!ore.worldNames.contains(worldInfo.getName()) && !ore.worldNames.contains("all"))
      ) continue;
      if(this.worldInfo != worldInfo) this.worldInfo = worldInfo;
      if(ore.getIterations() instanceof Integer iterations && iterations < 0)
        replaceBlocks(worldInfo, random, chunkX, chunkZ, limitedRegion, ore);
      else
        generateOre(worldInfo, random, chunkX, chunkZ, limitedRegion, ore);
    }
  }

  private void replaceBlocks(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion, Ore ore){
    List<Material> toReplace = ore.getReplace();
    int startX = chunkX << 4;
    int startZ = chunkZ << 4;
    boolean replaceAir = !toReplace.contains(Material.AIR);

    for (int x = startX; x < startX + 16; x++) {
      for (int z = startZ; z < startZ + 16; z++) {

        for (int y = worldInfo.getMinHeight(); y <= (replaceAir ? limitedRegion.getHighestBlockYAt(x,z) : worldInfo.getMaxHeight()); y++) {
          if (!limitedRegion.isInRegion(x, y, z)) continue;
          if (!ore.biomes.isEmpty() && !ore.biomes.contains(limitedRegion.getBiome(x, y, z))) continue;

          Material currentMaterial = limitedRegion.getType(x, y, z);

          if (toReplace.contains(currentMaterial)) {
            PlacementPosition position = new PlacementPosition(worldInfo, x, y, z, currentMaterial, limitedRegion.getBiome(x, y, z), limitedRegion);
            placeBlock(position, ore, worldInfo, limitedRegion);
          }
        }
      }
    }
  }

  private void generateOre(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion, Ore ore) {
    if (random.nextDouble() > ore.getChance()) return;

    int attempts;
    if(ore.getIterations() instanceof String str){
      String[] parts = str.split("-");
      int min = Integer.parseInt(parts[0].trim());
      int max = Integer.parseInt(parts[1].trim());
      attempts = random.nextInt(max - min + 1) + min;
    }else{
      attempts = (int) ore.getIterations();
    }
    int successfulPlacements = 0;
    int totalAttempts = 0;
    int maxRetries = attempts * 80;

    while (successfulPlacements < attempts && totalAttempts < maxRetries) {
      totalAttempts++;
      PlacementPosition position = getRandomPlacementPosition(chunkX, chunkZ, limitedRegion, ore, random, worldInfo);

      if(position == null) continue;
      int veinSize;
      if(ore.getVeinSize() instanceof String str){
        String[] parts = str.split("-");
        int min = Integer.parseInt(parts[0].trim());
        int max = Integer.parseInt(parts[1].trim());
        veinSize = random.nextInt(max - min + 1) + min;
      }else{
        veinSize = (int) ore.getVeinSize();
      }
      if (random.nextDouble() <= ore.getClusterChance() && veinSize > 0 && ore.getClusterChance() > 0.0) {
        successfulPlacements += generateVein(worldInfo, random, limitedRegion, position, ore, veinSize);
      } else {
        if (canReplaceBlock(position, ore)) {
          placeBlock(position, ore, worldInfo, limitedRegion);
          successfulPlacements++;
        } else if (canPlaceOnBlock(position, ore, limitedRegion)) {
          placeBlock(position.above(), ore, worldInfo, limitedRegion);
          successfulPlacements++;
        } else if (canPlaceBelowBlock(position, ore, limitedRegion)) {
          placeBlock(position.below(), ore, worldInfo, limitedRegion);
          successfulPlacements++;
        }
      }
    }
  }

  private int generateBlobVein(WorldInfo worldInfo, Random random, LimitedRegion limitedRegion, PlacementPosition start, Ore ore, int veinSize) {
    int placedBlocks = 0;
    Set<String> placed = new HashSet<>();
    float angle = random.nextFloat() * (float) Math.PI;
    float sizeScale = (float) veinSize / 8.0F;
    double startX = start.x() + Math.sin(angle) * sizeScale;
    double endX = start.x() - Math.sin(angle) * sizeScale;
    double startZ = start.z() + Math.cos(angle) * sizeScale;
    double endZ = start.z() - Math.cos(angle) * sizeScale;
    double startY = start.y() + random.nextInt(3) - 2;
    double endY = start.y() + random.nextInt(3) - 2;
    for (int i = 0; i <= veinSize; ++i) {
      float progress = (float) i / (float) veinSize;
      double currentX = startX + (endX - startX) * progress;
      double currentY = startY + (endY - startY) * progress;
      double currentZ = startZ + (endZ - startZ) * progress;
      double randomSize = random.nextDouble() * (double) veinSize / 16.0D;
      double diameterXZ = (Math.sin(Math.PI * progress) + 1.0) * randomSize + 1.0;
      double diameterY = (Math.sin(Math.PI * progress) + 1.0) * randomSize + 1.0;
      int minX = (int) Math.floor(currentX - diameterXZ / 2.0D);
      int minY = (int) Math.floor(currentY - diameterY / 2.0D);
      int minZ = (int) Math.floor(currentZ - diameterXZ / 2.0D);
      int maxX = (int) Math.floor(currentX + diameterXZ / 2.0D);
      int maxY = (int) Math.floor(currentY + diameterY / 2.0D);
      int maxZ = (int) Math.floor(currentZ + diameterXZ / 2.0D);
      for (int x = minX; x <= maxX; ++x) {
        double dx = ((double) x + 0.5D - currentX) / (diameterXZ / 2.0D);
        if (dx * dx >= 1.0D) continue;
        for (int y = minY; y <= maxY; ++y) {
          double dy = ((double) y + 0.5D - currentY) / (diameterY / 2.0D);
          if (dx * dx + dy * dy >= 1.0D) continue;
          for (int z = minZ; z <= maxZ; ++z) {
            double dz = ((double) z + 0.5D - currentZ) / (diameterXZ / 2.0D);
            if (dx * dx + dy * dy + dz * dz >= 1.0D) continue;
            if (!limitedRegion.isInRegion(x, y, z)) continue;
            Material blockType = limitedRegion.getType(x, y, z);
            Biome biome = limitedRegion.getBiome(x, y, z);
            PlacementPosition pos = new PlacementPosition(worldInfo, x, y, z, blockType, biome, limitedRegion);
            if (canReplaceBlock(pos, ore)) {
              String key = x + "," + y + "," + z;
              if (!placed.contains(key)) {
                placeBlock(pos, ore, worldInfo, limitedRegion);
                placed.add(key);
                placedBlocks++;
              }
            } else if (canPlaceOnBlock(pos, ore, limitedRegion)) {
              String key = x + "," + (y + 1) + "," + z;
              if (!placed.contains(key) && limitedRegion.isInRegion(x, y + 1, z)) {
                PlacementPosition above = pos.above();
                if (above != null) {
                  placeBlock(above, ore, worldInfo, limitedRegion);
                  placed.add(key);
                  placedBlocks++;
                }
              }
            } else if (canPlaceBelowBlock(pos, ore, limitedRegion)) {
              String key = x + "," + (y - 1) + "," + z;
              if (!placed.contains(key) && limitedRegion.isInRegion(x, y - 1, z)) {
                PlacementPosition below = pos.below();
                if (below != null) {
                  placeBlock(below, ore, worldInfo, limitedRegion);
                  placed.add(key);
                  placedBlocks++;
                }
              }
            }
          }
        }
      }
    }
    return placedBlocks;
  }

  private int generateVein(WorldInfo worldInfo, Random random, LimitedRegion limitedRegion, PlacementPosition start, Ore ore, int veinSize) {
    String pattern = ore.getRandomPattern();
    if (pattern.equalsIgnoreCase("blob")) {
      return generateBlobVein(worldInfo, random, limitedRegion, start, ore, veinSize);
    }

    int placedBlocks = 0;
    List<PlacementPosition> placedPositions = new ArrayList<>();

    for (int i = 0; i < veinSize; i++) {
      PlacementPosition currentOrigin = start;
      if (pattern.equalsIgnoreCase("blob") && !placedPositions.isEmpty()) {
        currentOrigin = placedPositions.get(random.nextInt(placedPositions.size()));
      }
      PlacementPosition nextPosition = getAdjacentPlacementPosition(currentOrigin, random, limitedRegion, ore, pattern, placedBlocks > 0 && !ore.getPlaceBelow().isEmpty());

      if (nextPosition == null) break;

//      Bukkit.broadcastMessage(ore.id+" "+limitedRegion.getCenterChunkX()+","+limitedRegion.getCenterChunkZ()+" "+placedBlocks+1);
//      Bukkit.broadcastMessage("  "+nextPosition.x+" "+nextPosition.y+" "+nextPosition.z);

      PlacementPosition placedPosition = null;
      if (canReplaceBlock(nextPosition, ore)) {
        if(!limitedRegion.isInRegion(new Location(Bukkit.getWorld(worldInfo.getUID()), nextPosition.x(), nextPosition.y(), nextPosition.z())))
          continue;
        placeBlock(nextPosition, ore, worldInfo, limitedRegion);
        placedPosition = nextPosition;
        placedBlocks++;
      } else if (canPlaceOnBlock(nextPosition, ore, limitedRegion)) {
        if(!limitedRegion.isInRegion(new Location(Bukkit.getWorld(worldInfo.getUID()), nextPosition.x(), nextPosition.y()+1, nextPosition.z())))
          continue;
        placeBlock(nextPosition.above(), ore, worldInfo, limitedRegion);
        placedPosition = nextPosition.above();
        placedBlocks++;
      } else if( canPlaceBelowBlock(nextPosition, ore, limitedRegion) || placedBlocks > 0 && !ore.getPlaceBelow().isEmpty()) {
        if(!limitedRegion.isInRegion(new Location(Bukkit.getWorld(worldInfo.getUID()), nextPosition.x(), nextPosition.y()-1, nextPosition.z())))
          continue;
        placeBlock(nextPosition.below(), ore, worldInfo, limitedRegion);
        placedPosition = nextPosition.below();
        placedBlocks++;
      } else {
        break;
      }

      if (placedPosition != null) {
        placedPositions.add(placedPosition);
        start = nextPosition;
      }
    }

    return placedBlocks;
  }

  private PlacementPosition getAdjacentPlacementPosition(PlacementPosition start, Random random, LimitedRegion limitedRegion, Ore ore, String pattern, boolean below) {
    Set<String> checkedLocations = new HashSet<>();
    int attempts = 0;

    try {
      for (int i = 0; i < 20 && attempts < 100; i++) {
        attempts++;
        int xOffset = 0;
        int yOffset = 0;
        int zOffset = 0;

        if (pattern.equalsIgnoreCase("vertical")) {
          yOffset = random.nextBoolean() ? 1 : -1;
        } else if (pattern.equalsIgnoreCase("horizontal")) {
          xOffset = random.nextInt(3) - 1;
          zOffset = random.nextInt(3) - 1;
        } else {
          xOffset = random.nextInt(3) - 1;
          yOffset = below ? -1 : (random.nextInt(3) - 1);
          zOffset = random.nextInt(3) - 1;
        }
        if (xOffset == 0 && yOffset == 0 && zOffset == 0) continue;

        int x = start.x() + xOffset;
        int y = start.y() + yOffset;
        int z = start.z() + zOffset;

        String key = x + "," + y + "," + z;
        if (checkedLocations.contains(key)) continue;
        checkedLocations.add(key);
        if (!limitedRegion.isInRegion(x, y, z))
          continue;

        Material blockType = limitedRegion.getType(x, y, z);

        if ((ore.getPlaceOn().contains(blockType) && (!ore.isOnlyAir() || !blockType.isAir())) || ore.getReplace().contains(blockType) || (ore.getPlaceBelow().contains(blockType) && (!ore.isOnlyAir() || !blockType.isAir()))) {
          return new PlacementPosition(start.worldInfo, x, y, z, blockType, start.biome(), limitedRegion);
        }
      }
      return null;
    }catch(Exception ignored){
      return null;
    }
  }

  private PlacementPosition getRandomPlacementPosition(int chunkX, int chunkZ, LimitedRegion limitedRegion, Ore ore, Random random, WorldInfo worldInfo) {
    try {
      int x = (chunkX << 4) + random.nextInt(16);
      int z = (chunkZ << 4) + random.nextInt(16);
      int y = ore.getMinLevel() + random.nextInt(ore.getMaxLevel() - ore.getMinLevel() + 1);

      if (!limitedRegion.isInRegion(x, y, z))
        return null;

      Material blockType = limitedRegion.getType(x, y, z);
      Biome biome = limitedRegion.getBiome(x, y, z);

      return new PlacementPosition(worldInfo, x, y, z, blockType, biome, limitedRegion);
    }catch(Exception ignored){
      return null;
    }
  }

  private boolean canReplaceBlock(PlacementPosition position, Ore ore) {
    return ore.getReplace() != null
        && position != null
        && ore.getReplace().contains(position.blockType())
        && ore.getBiomes().contains(position.biome());
  }

  private boolean canPlaceOnBlock(PlacementPosition position, Ore ore, LimitedRegion limitedRegion) {
    if(!limitedRegion.isInRegion(position.x(), position.y() + 1, position.z())) return false;
    Material aboveBlockType = limitedRegion.getType(position.x(), position.y() + 1, position.z());
    return ore.getPlaceOn() != null
        && ore.getPlaceOn().contains(position.blockType())
        && ore.getBiomes().contains(position.biome())
        && (!ore.isOnlyAir() || aboveBlockType.isAir());
  }

  private boolean canPlaceBelowBlock(PlacementPosition position, Ore ore, LimitedRegion limitedRegion) {
    if(!limitedRegion.isInRegion(position.x(), position.y() - 1, position.z())) return false;
    Material belowBlockType = limitedRegion.getType(position.x(), position.y() - 1, position.z());
    return (!ore.getPlaceBelow().isEmpty())
        && ore.getPlaceBelow().contains(position.blockType())
        && ore.getBiomes().contains(position.biome())
        && (!ore.getPlaceBelow().contains(belowBlockType))
        && (!ore.isOnlyAir() || belowBlockType.isAir());
  }

  private void placeBlock(PlacementPosition position, Ore ore, WorldInfo worldInfo, LimitedRegion limitedRegion) {
    if (ore.getNexoBlocks() != null && ore.getNexoBlocks().getBlockData() != null) {
      if(ore.isTall()) {
        limitedRegion.setBlockData(position.x(), position.y(), position.z(), ore.getNexoBlocks().getBlockData());
        World world = Bukkit.getWorld(worldInfo.getUID());
        if(limitedRegion.getType(new Location(world, position.x(), position.y()+1, position.z())).isAir())
          limitedRegion.setBlockData(position.x(), position.y()+1, position.z(), Material.TRIPWIRE.createBlockData());
      }else{
        limitedRegion.setBlockData(position.x(), position.y(), position.z(), ore.getNexoBlocks().getBlockData());
      }
    } else{
      limitedRegion.setBlockData(position.x(), position.y(), position.z(), ore.getVanillaMaterial().createBlockData());
    }
    PlacementPosition belowPosition = position.below();
    if(belowPosition != null) {
      if (belowPosition.blockType.equals(Material.GRASS_BLOCK) && !limitedRegion.getBlockData(belowPosition.x, belowPosition.y, belowPosition.z).equals(Material.GRASS_BLOCK.createBlockData())) {
        limitedRegion.setBlockData(belowPosition.x(), belowPosition.y(), belowPosition.z(), Material.GRASS_BLOCK.createBlockData());
      }
    }
  }

  public record PlacementPosition(WorldInfo worldInfo, int x, int y, int z, Material blockType, Biome biome, LimitedRegion limitedRegion) {

    PlacementPosition above() {
      if(!limitedRegion.isInRegion(x, y + 1, z)) return null;
      return new PlacementPosition(worldInfo, x, y + 1, z, limitedRegion.getType( x, y + 1, z), biome, limitedRegion);
    }

    PlacementPosition below() {
      if(!limitedRegion.isInRegion(x, y - 1, z)) return null;
      return new PlacementPosition(worldInfo,x, y - 1, z, limitedRegion.getType(x, y - 1, z), biome, limitedRegion);
    }

    Location getLocation() {
      return new Location(Bukkit.getWorld(worldInfo.getUID()), x, y, z);
    }
  }
}