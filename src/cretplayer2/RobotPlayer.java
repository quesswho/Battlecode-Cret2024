package cretplayer2;

import java.util.ArrayList;
import java.util.Random;

import battlecode.common.*;

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

    private static void populateSpawnCenters(RobotController rc) throws GameActionException {
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
            }
        }
    }

    public static void run(RobotController rc) throws GameActionException {
        role = Role.getRobotRole(rc.getID());

        populateSpawnCenters(rc);

        if (personalID == -1 && rc.canWriteSharedArray(0, 0)) {
            personalID = rc.readSharedArray(63);
            rc.writeSharedArray(63, personalID + 1);

            // Assign first three builders to be in the guardian role
            int guardianCount = rc.readSharedArray(62);
            if (role == Role.BUILDER && guardianCount < 3) {
                role = Role.GUARDIAN;
                guardianID = guardianCount;
                rc.writeSharedArray(62, guardianCount + 1);
            }

        }

        rc.setIndicatorString("My role is: " + role);

        while (true) {
            try {

                if (random == null)
                    random = new Random(rc.getID());
                trySpawn(rc);
                if (rc.isSpawned()) {
                    int round = rc.getRoundNum();
                    if (round < GameConstants.SETUP_ROUNDS)
                        SetupPhase.runSetup(rc);
                    else
                        MainPhase.runMainPhase(rc);
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
        for (MapLocation loc : spawnLocs) {
            if (rc.canSpawn(loc)) {
                rc.spawn(loc);
                break;
            }
        }
    }
}
