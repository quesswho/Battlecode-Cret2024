package cretplayer2_2;

import battlecode.common.*;
import jdk.nashorn.internal.objects.Global;

import java.util.*;

public class RobotPlayer {

    static int roundCount;
    static int startRound;
    public static String indicator;

    public static Random random = null;
    public static int personalID = -1;

    public static Role role;
    public static int guardianID = -1;
    public static ArrayList<MapLocation> centers = new ArrayList<>();

    public static boolean capturingUpgrade = false;
    public static boolean attackUpgrade = false;
    public static boolean healingUpgrade = false;

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

    public static int mapWidth = 0;
    public static int mapHeight = 0;
    public static Team team;
    public static Team opponent;

    public static MapLocation[] flagSpawnLocation = new MapLocation[3];
    static MapLocation middle;
    static MapLocation[] flagGoal = new MapLocation[3];
    static MapLocation corner;

    static boolean hasBeenAlive = false;
    static boolean jailedPenalty = false;
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
    }

    public static void run(RobotController rc) throws GameActionException {
        role = Role.MINION;

        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();
        middle = new MapLocation(mapWidth/2, mapHeight/2);
        team = rc.getTeam();
        opponent = rc.getTeam().opponent();
        roundCount = 0;
        FastMath.initRand(rc);

        populateSpawnCenters(rc);

        if (personalID == -1 && rc.canWriteSharedArray(0, 0)) {
            personalID = rc.readSharedArray(0);
            rc.writeSharedArray(0, personalID + 1);

            // Assign first three guardians and three builders
            int assignCount = rc.readSharedArray(1);
            if (assignCount < 3) {
                role = Role.GUARDIAN;
                guardianID = assignCount;
                rc.writeSharedArray(1, assignCount + 1);
            } else if(assignCount < 6) {
                role = Role.BUILDER;
                rc.writeSharedArray(1, assignCount + 1);
            }
        }

        while (true) {
            try {
                startRound = rc.getRoundNum();
                if (random == null) {
                    random = new Random(rc.getID());
                }
                indicator = "";
                trySpawn(rc);
                if (rc.isSpawned()) {
                    hasBeenAlive = true;

                    // Buy global upgrade
                    if(rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) {
                        rc.buyGlobal(GlobalUpgrade.CAPTURING);
                        Communication.updateGlobalUpgrade(rc, GlobalUpgrade.CAPTURING);
                    }
                    else if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
                        rc.buyGlobal(GlobalUpgrade.HEALING);
                        Communication.updateGlobalUpgrade(rc, GlobalUpgrade.HEALING);
                    }
                    else if(rc.canBuyGlobal(GlobalUpgrade.ATTACK)) {
                        rc.buyGlobal(GlobalUpgrade.ATTACK);
                        Communication.updateGlobalUpgrade(rc, GlobalUpgrade.ATTACK);
                    }

                    int round = rc.getRoundNum();
                    if (round < GameConstants.SETUP_ROUNDS) {
                        SetupPhase.runSetup(rc);
                    } else {
                        MainPhase.runMainPhase(rc);
                    }
                } else if(hasBeenAlive){
                    jailedPenalty = true;
                }
                rc.setIndicatorString(indicator);
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                roundCount += 1;
                if (startRound != rc.getRoundNum()) {
                    System.out.print("Overran a round!");
                }
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
        List<MapLocation> list = Arrays.asList(spawnLocs);
        Collections.shuffle(list);
        list.toArray(spawnLocs);
        for (MapLocation loc : spawnLocs) {
            if (rc.canSpawn(loc)) {
                rc.spawn(loc);
                return;
            }
        }
    }
}
