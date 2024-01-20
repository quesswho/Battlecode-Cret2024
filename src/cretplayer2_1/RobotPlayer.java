package cretplayer2_1;

import battlecode.common.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

public class RobotPlayer {

    public static Random random = null;
    public static int personalID = -1;

    public static Role role;
    public static int guardianID = -1;
    public static ArrayList<MapLocation> centers = new ArrayList<>();

    public static Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    static int mapWidth = 0;
    static int mapHeight = 0;

    static MapLocation middle;
    static MapLocation[] flagGoal = new MapLocation[3];
    static MapLocation corner;
    static int[] flagIDs; // 0 0 0
    private static void populateSpawnCenters(RobotController rc) throws GameActionException {
        int tempx=0, tempy=0;
        // Center iff adjacent to 8 other spawn locations
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        for (int i = 0; i < spawnLocs.length; i++) {
            int adjCount = 0;
            for (int j = 0; j < spawnLocs.length; j++) {
                if (spawnLocs[j].isAdjacentTo(spawnLocs[i])) {
                    adjCount++;
                }
            }
            if (adjCount == 9) {
                centers.add(spawnLocs[i]);
                tempx += spawnLocs[i].x;
                tempy += spawnLocs[i].y;
            }
        }
        int distbetweenflags = 7;
        int min = 10000;
        int dist = Pathfinder.findClosestDistance(new MapLocation(0,0), centers);
        if(dist < min) {
            min = dist;
            corner = new MapLocation(0,0);
            flagGoal[0] = new MapLocation(0,0);
            flagGoal[1] = new MapLocation(distbetweenflags,0);
            flagGoal[2] = new MapLocation(0,distbetweenflags);
        }
        dist = Pathfinder.findClosestDistance(new MapLocation(0,mapHeight-1), centers);
        if(dist < min) {
            min = dist;
            corner = new MapLocation(0,mapHeight-1);
            flagGoal[0] = new MapLocation(0,mapHeight-1);
            flagGoal[1] = new MapLocation(distbetweenflags,mapHeight-1);
            flagGoal[2] = new MapLocation(0,mapHeight-1-distbetweenflags);
        }
        dist = Pathfinder.findClosestDistance(new MapLocation(mapWidth-1,0), centers);
        if(dist < min) {
            min = dist;
            corner = new MapLocation(mapWidth-1,0);
            flagGoal[0] = new MapLocation(mapWidth-1,0);
            flagGoal[1] = new MapLocation(mapWidth-1-distbetweenflags,0);
            flagGoal[2] = new MapLocation(mapWidth-1,distbetweenflags);
        }
        dist = Pathfinder.findClosestDistance(new MapLocation(mapWidth-1,mapHeight-1), centers);
        if(dist < min) {
            corner = new MapLocation(mapWidth-1,mapHeight-1);
            flagGoal[0] = new MapLocation(mapWidth-1,mapHeight-1);
            flagGoal[1] = new MapLocation(mapWidth-1-distbetweenflags,mapHeight);
            flagGoal[2] = new MapLocation(mapWidth-1,mapHeight-1-distbetweenflags);
        }

        /*flagGoal = new MapLocation(tempx/3, tempy/3);
        for(int i = 0; i < Math.min(mapWidth,mapHeight)/5; i++) {
            flagGoal = flagGoal.add(middle.directionTo(corner));
        }
        flagGoal = corner;*/
    }

    public static void run(RobotController rc) throws GameActionException {
        role = Role.MINION;

        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();
        middle = new MapLocation(mapWidth/2, mapHeight/2);

        populateSpawnCenters(rc);

        if (personalID == -1 && rc.canWriteSharedArray(0, 0)) {
            personalID = rc.readSharedArray(63);
            rc.writeSharedArray(63, personalID + 1);

            // Assign first three guardians and three builders
            int assignCount = rc.readSharedArray(62);
            if (assignCount < 3) {
                role = Role.GUARDIAN;
                guardianID = assignCount;
                rc.writeSharedArray(62, assignCount + 1);
            } else if(assignCount < 6) {
                role = Role.BUILDER;
                rc.writeSharedArray(62, assignCount + 1);
            }
        }

        rc.setIndicatorString("My role is: " + role);

        while (true) {
            try {

                if (random == null) {
                    random = new Random(rc.getID());
                }

                trySpawn(rc);
                if (rc.isSpawned()) {
                    // Buy global upgrade
                    if(rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) {
                        rc.buyGlobal(GlobalUpgrade.CAPTURING);
                    }
                    else if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
                        rc.buyGlobal(GlobalUpgrade.HEALING);
                    }
                    else if(rc.canBuyGlobal(GlobalUpgrade.ATTACK)) {
                        rc.buyGlobal(GlobalUpgrade.ATTACK);
                    }

                    flagIDs = new int[6];

                    int round = rc.getRoundNum();
                    if (round < GameConstants.SETUP_ROUNDS) {
                        SetupPhase.runSetup(rc);
                    } else {
                        MainPhase.runMainPhase(rc);
                    }
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    private static void trySpawn(RobotController rc) throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        if(guardianID > -1) {
            if(rc.canSpawn(centers.get(guardianID))) {
                rc.spawn(centers.get(guardianID));
                return;
            }
        }
        for (MapLocation loc : spawnLocs) {
            if (rc.canSpawn(loc)) {
                rc.spawn(loc);
                return;
            }
        }
    }

    public static int flagIDToIdx(RobotController rc, int flagID) {
        for (int i = 0; i < flagIDs.length; i++) {
            if (flagIDs[i] == 0) {
                flagIDs[i] = flagID;
                return i;
            } else if (flagIDs[i] == flagID) {
                return i;
            } else
                continue;
        }
        return 0;
    }
}
