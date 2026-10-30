package dev.wiktor.unboundhorizons.cubic;

import dev.wiktor.unboundhorizons.vertical.VerticalWorldBorder;
import dev.wiktor.unboundhorizons.vertical.WideBlockPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Random;

/** Plain Java 21 integration test (does not need a Minecraft process). */
public final class CubicTerrainWorldSelfTest {
    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        long seed=0x6A4D5E66CCAA5511L;
        CubicTerrainGenerator gen=new CubicTerrainGenerator(seed);
        CubicTerrainGenerator other=new CubicTerrainGenerator(seed);
        Random random=new Random(0xD33FC0BEL);

        // Absolute-coordinate determinism and exact 16^3 seam layout.
        for (int i=0;i<1200;i++) {
            int x=random.nextInt(-50000,50001);
            int y=random.nextInt(VerticalWorldBorder.MIN_PLAYABLE_Y,
                VerticalWorldBorder.MAX_PLAYABLE_Y+1);
            int z=random.nextInt(-50000,50001);
            CubicChunk section=gen.generate(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16));
            check(section.blockId(new WideBlockPos(x,y,z)).equals(
                CubicBlocks.id(other.blockAt(x,y,z))),
                "3D cubic seam mismatch at ("+x+","+y+","+z+")");
        }

        // Count naturally generated ores/lakes/biomes down to very deep levels.
        int ores=0,water=0,lava=0,air=0,rock=0,skyRock=0;
        EnumSet<CubicBiome> biomes=EnumSet.noneOf(CubicBiome.class);
        final int[] depths={
            -256,-1400,-10000,-100000,-1200000,-9900000,-29990000
        };
        for(int depth : depths) {
            int depthOre=0;
            int depthSolid=0;
            for(int i=0;i<70;i++) {
                int x=random.nextInt(-25000,25001);
                int z=random.nextInt(-25000,25001);
                // Sample same approximate depth with enough vertical spread
                // to cover several cave/lake/ore strata.
                int y=depth+random.nextInt(-340,341);
                CubicBiome biome=gen.biomeAt(x,y,z);
                biomes.add(biome);
                for(int t=0;t<190;t++){
                    int xx=x+random.nextInt(-24,25);
                    int yy=y+random.nextInt(-56,57);
                    int zz=z+random.nextInt(-24,25);
                    byte block=gen.blockAt(xx,yy,zz);
                    if(CubicBlocks.isOre(block)){ ores++; depthOre++; }
                    if(block==CubicBlocks.WATER)water++;
                    if(block==CubicBlocks.LAVA)lava++;
                    if(block==CubicBlocks.AIR)air++;
                    else {rock++;depthSolid++;}
                }
            }
            check(depthSolid>0, "Deep terrain generation stopped at Y="+depth);
            check(depthOre>0, "Ores missing at Y="+depth);
        }
        for(int i=0;i<4000;i++){
            int x=random.nextInt(-200000,200001);
            int z=random.nextInt(-200000,200001);
            int y=512*random.nextInt(1,8000)+256;
            if(y>VerticalWorldBorder.MAX_PLAYABLE_Y)continue;
            if(gen.blockAt(x,y,z)!=CubicBlocks.AIR)skyRock++;
        }
        check(air>0 && rock>0,"Cave geometry is not mixed with rock");
        check(ores>300,"Not enough distributed deep ores: "+ores);
        check(water>10,"No natural deep water lake/cavern bodies: "+water);
        check(lava>10,"No natural deep lava lake/cavern bodies: "+lava);
        check(biomes.size()>=5,"Missing biome variety: "+biomes);
        check(skyRock>0,"No high-Y sky island rock generated");

        // Persist ONLY edits, distinguishing mining AIR from no edit.
        Path path=Files.createTempDirectory("unbound-cubic-world-");
        WideBlockPos low=new WideBlockPos(55,-29_999_984,79);
        WideBlockPos high=new WideBlockPos(55,29_999_984,79);
        WideBlockPos mined=new WideBlockPos(99,-12_345_680,39);
        WideBlockPos another=new WideBlockPos(-16,-3_000,16);
        String original;
        try (CubicTerrainWorld world=new CubicTerrainWorld(path,seed)) {
            original=world.getNatural(mined);
            world.set(low,"minecraft:diamond_block");
            world.set(high,"minecraft:gold_block");
            world.mine(mined);
            world.set(another,"minecraft:chest");
            check(world.get(low).equals("minecraft:diamond_block"),"Lower virtual edit");
            check(world.get(high).equals("minecraft:gold_block"),"Upper virtual edit");
            check(world.get(mined).equals("minecraft:air"),"Mined air overlay");
            for(int i=0;i<160;i++){
                int y=-29_800_000+i*65536;
                world.getChunk(0,Math.floorDiv(y,16),0);
            }
            check(world.cachedCubeCount()<=CubicTerrainWorld.CACHE_LIMIT,
                "More than 128 generated cubes held in RAM");
        }
        try (CubicTerrainWorld reopened=new CubicTerrainWorld(path,seed)) {
            check(reopened.get(low).equals("minecraft:diamond_block"),"Lower edit lost");
            check(reopened.get(high).equals("minecraft:gold_block"),"Upper edit lost");
            check(reopened.get(mined).equals("minecraft:air"),"Mined air not persisted");
            check(reopened.get(another).equals("minecraft:chest"),"Nonpalette edit lost");
            reopened.restoreNatural(mined);
            reopened.restoreNatural(another);
            reopened.set(low,reopened.getNatural(low));
        }
        try (CubicTerrainWorld reopened=new CubicTerrainWorld(path,seed)) {
            check(reopened.get(mined).equals(original),"Natural block not restored after mined-air override");
            check(reopened.get(another).equals(reopened.getNatural(another)),"Edit deletion failed");
            check(reopened.get(low).equals(reopened.getNatural(low)),"Same-as-natural did not erase edit");
            check(reopened.get(high).equals("minecraft:gold_block"),"Independent edit disappeared");
        }
        boolean seedMismatch=false;
        try(CubicTerrainWorld ignored=new CubicTerrainWorld(path,seed+1)){}
        catch(java.io.IOException expected){seedMismatch=true;}
        check(seedMismatch,"Seed mismatch was not rejected");

        System.out.println("UNBOUND_CUBIC_WORLD_PASS 16^3 sections; 1200 cross-cube consistency "
            +"checks, ores at all seven ultra-deep ranges, water="+water
            +", lava="+lava+", oreBlocks="+ores+", biomes="+biomes
            +", skyIslands="+skyRock+", edits saved/reloaded and LRU <=128.");
    }
}
