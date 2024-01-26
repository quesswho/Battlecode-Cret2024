package cretplayer2_2;

import battlecode.common.*;
import jdk.nashorn.internal.objects.Global;

import java.util.ArrayList;

public class Communication {

    /*
        0-1: (Before setup) Initialization of ducks during setup
        0: Global upgrade (first three bits)
        2-4: Ally flag spawn location
        5-7: Enemy flag spawn location
        8-13: Current ally flag location (2 each, one for location one for round number)
        14-19: Current enemy flag location (2 each, one for location one for round number)
        20-22: enemyFlag ids
        23-25: allyFlag ids
    */

    public static final int ALLY_FLAG_SPAWN_IDX = 2;
    public static final int ENEMY_FLAG_SPAWN_IDX = 5;

    public static final int ALLY_CURRENT_FLAG_IDX = 8;
    public static final int ENEMY_CURRENT_FLAG_IDX = 14;

    public static final int ENEMY_FLAG_IDX = 20;

    public static final int ALLY_FLAG_IDX = 23;
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
            int id = rc.readSharedArray(ENEMY_FLAG_IDX+i);
            if(id == 0) continue;

            enemyFlags[i] = id;

            MapLocation loc = getLocation(rc, ENEMY_CURRENT_FLAG_IDX+i*2);
            if(loc != null) {
                int round = rc.readSharedArray(ENEMY_CURRENT_FLAG_IDX+i*2+1);
                int resetTime = RobotPlayer.capturingUpgrade ? 25 : 4; // Flag resets after 4 rounds and 25 rounds with upgrade
                if(rc.getRoundNum() - round > resetTime) {
                    if(rc.canWriteSharedArray(ENEMY_CURRENT_FLAG_IDX+i*2, 0)) {
                        int spawnLocation = rc.readSharedArray(ENEMY_FLAG_SPAWN_IDX); // Must be set because of loc != null
                        rc.writeSharedArray(ENEMY_CURRENT_FLAG_IDX + i*2, spawnLocation);
                        rc.writeSharedArray(ENEMY_CURRENT_FLAG_IDX + i*2+1, rc.getRoundNum());
                    }
                }
            }
        }

        for(int i = 0; i < 3; i++) {
            int id = rc.readSharedArray(ALLY_FLAG_IDX+i);
            if(id == 0) continue;

            allyFlags[i] = id;

            MapLocation loc = getLocation(rc, ALLY_CURRENT_FLAG_IDX+i*2);
            if(loc != null) {
                int round = rc.readSharedArray(ALLY_CURRENT_FLAG_IDX+i*2+1);
                int resetTime = RobotPlayer.capturingUpgrade ? 25 : 4; // Flag resets after 4 rounds and 25 rounds with upgrade
                if(rc.getRoundNum() - round > resetTime) {
                    if(rc.canWriteSharedArray(ALLY_CURRENT_FLAG_IDX+i*2, 0)) {
                        int spawnLocation = rc.readSharedArray(ALLY_FLAG_SPAWN_IDX); // Must be set because of loc != null
                        rc.writeSharedArray(ALLY_CURRENT_FLAG_IDX + i*2, spawnLocation);
                        rc.writeSharedArray(ALLY_CURRENT_FLAG_IDX + i*2+1, rc.getRoundNum());
                    }
                }
            }
        }
    }
    public static void updateFlagInfo(RobotController rc, FlagInfo flag) throws GameActionException {
        int id = flag.getID();
        Team team = flag.getTeam();
        int idx = -1;
        int index = -1;
        if(team == RobotPlayer.team) {
            for(int i = 0; i < allyFlags.length; i++) {
                if(allyFlags[i] == id) {
                    idx = ALLY_FLAG_SPAWN_IDX;
                    index = i;
                    break;
                } else if(allyFlags[i] == -1) {
                    rc.writeSharedArray(ALLY_FLAG_IDX+i, id);
                    allyFlags[i] = id;
                    idx = ALLY_FLAG_SPAWN_IDX;
                    index = i;
                    break;
                }
            }
        } else {
            for(int i = 0; i < enemyFlags.length; i++) {
                if(enemyFlags[i] == id) {
                    idx = ENEMY_FLAG_SPAWN_IDX;
                    index = i;
                    break;
                } else if(enemyFlags[i] == -1) {
                    rc.writeSharedArray(ENEMY_FLAG_IDX+i, id);
                    enemyFlags[i] = id;
                    idx = ENEMY_FLAG_SPAWN_IDX;
                    index = i;
                    break;
                }
            }
        }
        if(index == -1) {
            //System.out.println("Could not find flag id!!" + id);
            return;
        }
        int read = rc.readSharedArray(idx + index);
        if(read == 0) { // First time we are seeing this flag so write down spawn location
            int value = locationToInt(flag.getLocation());
            if (rc.canWriteSharedArray(idx + index, value)) {
                rc.writeSharedArray(idx + index, value);
            }


            // Update current position
            if(idx==ALLY_FLAG_SPAWN_IDX) {
                rc.writeSharedArray(ALLY_CURRENT_FLAG_IDX + index*2, value);
                rc.writeSharedArray(ALLY_CURRENT_FLAG_IDX + index*2+1, rc.getRoundNum());
            } else {
                System.out.println("First update of flag " + enemyFlags[index]);
                rc.writeSharedArray(ENEMY_CURRENT_FLAG_IDX + index*2, value);
                rc.writeSharedArray(ENEMY_CURRENT_FLAG_IDX + index*2+1, rc.getRoundNum());
            }
        } else if(read > 0) {
            if(!intToLocation(read).equals(flag.getLocation()) || flag.isPickedUp()) {
                if(idx==ALLY_FLAG_SPAWN_IDX) {
                    idx = ALLY_CURRENT_FLAG_IDX;
                    RobotPlayer.flagSpawnLocation[index] = intToLocation(read);
                } else {
                    idx = ENEMY_CURRENT_FLAG_IDX;
                }
                System.out.println("Updating flag position " + enemyFlags[index]);
                int value = locationToInt(flag.getLocation());
                if (rc.canWriteSharedArray(idx + 2*index, value)) {
                    rc.writeSharedArray(idx + 2*index, value);
                    rc.writeSharedArray(idx + 2*index+1, rc.getRoundNum());
                }
                System.out.println(intToLocation(rc.readSharedArray(ENEMY_CURRENT_FLAG_IDX+index*2)).toString());
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

        }
        // Set it to 0
        System.out.println("Setting enemy flag " + id + ", " + i + " to null");
        if (rc.canWriteSharedArray(ENEMY_FLAG_SPAWN_IDX+i, 0)) {
            rc.writeSharedArray(ENEMY_FLAG_SPAWN_IDX+i, 0);
            rc.writeSharedArray(ENEMY_CURRENT_FLAG_IDX+2*i, 0);
            rc.writeSharedArray(ENEMY_CURRENT_FLAG_IDX+2*i+1, rc.getRoundNum());
        }
    }
    public static ArrayList<MapLocation> getEnemyFlagLocations(RobotController rc) throws GameActionException {
        ArrayList<MapLocation> result = new ArrayList<>();
        for(int i = 0; i < 3; i++) {
            MapLocation loc = intToLocation(rc.readSharedArray(ENEMY_CURRENT_FLAG_IDX+i*2));
            if(loc != null) result.add(loc);
        }
        return result;
    }

    public static ArrayList<MapLocation> getEnemyFlagSpawnLocations(RobotController rc) throws GameActionException {
        ArrayList<MapLocation> result = new ArrayList<>();
        for(int i = 0; i < 3; i++) {
            MapLocation loc = intToLocation(rc.readSharedArray(ENEMY_FLAG_SPAWN_IDX+i));
            if(loc != null) result.add(loc);
        }
        return result;
    }

    public static ArrayList<MapLocation> getAllyFlagLocations(RobotController rc) throws GameActionException {
        ArrayList<MapLocation> result = new ArrayList<>();
        for(int i = 0; i < 3; i++) {
            MapLocation loc = intToLocation(rc.readSharedArray(ALLY_CURRENT_FLAG_IDX+i*2));
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