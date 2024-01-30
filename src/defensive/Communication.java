package defensive;

import battlecode.common.*;

import java.util.ArrayList;

public class Communication {

    /*
        0-1: (Before setup) Initialization of ducks during setup
        0: Global upgrade (first three bits)
        2-13: Enemy flag (4 each, id, spawn, current, roundnum)
        14-25: Ally flag (4 each, id, spawn, current, roundnum)
        26: Warning location
    */

    public static final int ENEMY_FLAG_IDX = 2;
    public static final int ALLY_FLAG_IDX = 14;

    /*
        Location is stored in the first 12 bits, offset by 1 so that 0 = null

    */

    static int[] allyFlags = { -1, -1, -1 };
    static int[] enemyFlags = { -1, -1, -1 };

    public static void clearSetup(RobotController rc) throws GameActionException {
        if(rc.canWriteSharedArray(0,0)) {
            rc.writeSharedArray(0,0);
            rc.writeSharedArray(1,0);
        }
    }

    /*
        Check global upgrades and reset flag locations if needed
     */
    public static void updateRobot(RobotController rc) throws GameActionException {
        int val = rc.readSharedArray(0);
        if((val & 1) > 0) {
            RobotPlayer.attackUpgrade = true;
        }
        if((val & 2) > 0) {
            RobotPlayer.healingUpgrade = true;
        }
        if((val & 4) > 0) {
            RobotPlayer.capturingUpgrade = true;
        }


        for(int i = 0; i < 3; i++) {
            int id = rc.readSharedArray(ENEMY_FLAG_IDX+i*4);
            if(id == 0) continue;

            enemyFlags[i] = --id;

            MapLocation current = getLocation(rc, ENEMY_FLAG_IDX+i*4+2); // Current location
            if(current != null) {
                int round = rc.readSharedArray(ENEMY_FLAG_IDX+i*4+3);
                int resetTime = RobotPlayer.capturingUpgrade ? 25 : 4; // Flag resets after 4 rounds and 25 rounds with upgrade
                if(rc.getRoundNum() - round > resetTime) {
                    int spawnLocation = rc.readSharedArray(ENEMY_FLAG_IDX + i*4 + 1); // Must be set because of loc != null
                    rc.writeSharedArray(ENEMY_FLAG_IDX + i*4+2, spawnLocation);
                    rc.writeSharedArray(ENEMY_FLAG_IDX + i*4+3, rc.getRoundNum());
                }
            }
        }

        for(int i = 0; i < 3; i++) {
            int id = rc.readSharedArray(ALLY_FLAG_IDX+i*4);
            if(id == 0) continue;

            allyFlags[i] = --id;

            // Heuristically reset ally flag if we cant find it
            MapLocation current = getLocation(rc, ALLY_FLAG_IDX+i*4+2); // Current location
            if(current != null) {
                int round = rc.readSharedArray(ALLY_FLAG_IDX+i*4+3);
                int resetTime = RobotPlayer.capturingUpgrade ? 25 : 4;
                if(rc.getRoundNum() - round > resetTime) {
                    rc.writeSharedArray(ALLY_FLAG_IDX + i*4+2, 0);
                    rc.writeSharedArray(ALLY_FLAG_IDX + i*4+3, rc.getRoundNum());
                }
            }
        }
    }
    public static void updateFlagInfo(RobotController rc, FlagInfo flag) throws GameActionException {
        int id = flag.getID();
        Team team = flag.getTeam();
        int idx;
        int index = -1;
        if(team == RobotPlayer.team) {
            idx = ALLY_FLAG_IDX;
            for(int i = 0; i < allyFlags.length; i++) {
                if(allyFlags[i] == id) {
                    index = i;
                    break;
                } else if(allyFlags[i] == -1) {
                    rc.writeSharedArray(ALLY_FLAG_IDX+i*4, id+1);
                    allyFlags[i] = id;
                    index = i;
                    break;
                }
            }
        } else {
            idx = ENEMY_FLAG_IDX;
            for(int i = 0; i < enemyFlags.length; i++) {
                if(enemyFlags[i] == id) {
                    index = i;
                    break;
                } else if(enemyFlags[i] == -1) {
                    rc.writeSharedArray(ENEMY_FLAG_IDX+i*4, id+1);
                    enemyFlags[i] = id;
                    index = i;
                    break;
                }
            }
        }
        if(index == -1) {
            System.out.println("Could not find flag id!! " + id);
            return;
        }

        int spawn = rc.readSharedArray(idx + index*4+1); // Spawn location
        if(spawn == 0) { // First time we are seeing this flag so write down this spawn location
            int value = locationToInt(flag.getLocation());
            rc.writeSharedArray(idx + index*4+1, value);

            // Update current position
            rc.writeSharedArray(idx + index*4+2, value);
            rc.writeSharedArray(idx + index*4+3, rc.getRoundNum());

            System.out.println("Found flag " + id);

        } else { // If we have seen this flag before
            MapLocation current = intToLocation(rc.readSharedArray(idx + index*4+2));

            if(idx==ALLY_FLAG_IDX) {
                RobotPlayer.flagSpawnLocation[index] = intToLocation(spawn);
                int value = locationToInt(flag.getLocation());
                rc.writeSharedArray(idx + index*4+2, value);
                rc.writeSharedArray(idx + index*4+3, rc.getRoundNum());
                return;
            }

            if(current == null) {
                System.out.println("Current enemy flag location was not found! " + id);
                return;
            }

            if(!current.equals(flag.getLocation()) || flag.isPickedUp()) {
                int value = locationToInt(flag.getLocation());
                rc.writeSharedArray(idx + index*4+2, value);
                rc.writeSharedArray(idx + index*4+3, rc.getRoundNum());
            }

        }
    }

    public static void capturedFlag(RobotController rc, int id) throws GameActionException {
        int i;
        boolean found = false;
        for(i = 0; i < enemyFlags.length; i++) {
            if (enemyFlags[i] == id) {
                found = true;
                break;
            }
        }
        if(!found) {
            System.out.println("Could not find enemy flag " + id + " in array!");
            for(i = 0; i < enemyFlags.length; i++) {
                System.out.println(enemyFlags[i]);
            }
            return;
        }
        // Set it to 0
        System.out.println("Captured enemy flag " + id + ", " + i);
        rc.writeSharedArray(ENEMY_FLAG_IDX+i*4 +1, 0);
        rc.writeSharedArray(ENEMY_FLAG_IDX+i*4+2, 0);
        rc.writeSharedArray(ENEMY_FLAG_IDX+i*4+3, rc.getRoundNum());
    }
    public static ArrayList<MapLocation> getEnemyFlagLocations(RobotController rc) throws GameActionException {
        ArrayList<MapLocation> result = new ArrayList<>();
        for(int i = 0; i < 3; i++) {
            MapLocation loc = intToLocation(rc.readSharedArray(ENEMY_FLAG_IDX+i*4+2));
            if(loc != null) {
                result.add(loc);
                rc.setIndicatorDot(loc, 255, 0, 0);
            }
        }
        return result;
    }

    public static ArrayList<MapLocation> getEnemyFlagSpawnLocations(RobotController rc) throws GameActionException {
        ArrayList<MapLocation> result = new ArrayList<>();
        for(int i = 0; i < 3; i++) {
            MapLocation loc = intToLocation(rc.readSharedArray(ENEMY_FLAG_IDX+i*4+1));
            if(loc != null) result.add(loc);
        }
        return result;
    }

    public static ArrayList<MapLocation> getAllyFlagLocations(RobotController rc) throws GameActionException {
        ArrayList<MapLocation> result = new ArrayList<>();
        for(int i = 0; i < 3; i++) {
            MapLocation loc = intToLocation(rc.readSharedArray(ALLY_FLAG_IDX+i*4+2));
            if(loc != null) result.add(loc);
        }
        return result;
    }

    public static void updateGlobalUpgrade(RobotController rc, GlobalUpgrade upgrade) throws GameActionException {
        if(rc.canWriteSharedArray(0, 0)) {
            int val = rc.readSharedArray(0);
            switch (upgrade) {
                case ATTACK:
                    rc.writeSharedArray(0, val | 1);
                    break;
                case HEALING:
                    rc.writeSharedArray(0, val | 2);
                    break;
                case CAPTURING:
                    rc.writeSharedArray(0, val | 4);
                    break;
            }
        }
    }

    public static MapLocation getLocation(RobotController rc, int idx) throws GameActionException {
        int value = rc.readSharedArray(idx);
        return intToLocation(value);
    }

    /*
        Uses at most 12 bits
     */
    public static int locationToInt(MapLocation loc) {
        if(loc==null) {
            return 0;
        }
        return 1 + loc.x + loc.y * RobotPlayer.mapWidth;
    }
    public static MapLocation intToLocation(int m) {
        if (m == 0)
            return null;
        return new MapLocation((m - 1) % RobotPlayer.mapWidth, (m - 1) / RobotPlayer.mapWidth);
    }
}