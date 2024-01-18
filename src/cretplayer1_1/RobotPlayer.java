package cretplayer1_1;

import battlecode.common.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public strictfp class RobotPlayer {

    public static Random rng = null;
    static int id = -1;
    static int roundNum = 0;

    static int attack = 0;
    static int build = 0;
    static int heal = 0;

    static Team team;
    static Team opponentTeam;
    static MapLocation robotLoc;
    static MapLocation prevLoc;

    static int mapHeight = 0;
    static int mapWidth = 0;
    static MapLocation middle = null;
    public static void run(RobotController rc) throws GameActionException {
        while (true) {

            roundNum++;

            try {
                if(id == -1) { // Get all base information
                    id = rc.getID();
                    team = rc.getTeam();
                    opponentTeam = team == Team.A ? Team.B : Team.A;
                    rng = new Random(id);
                    mapHeight = rc.getMapHeight();
                    mapWidth = rc.getMapWidth();
                    middle = new MapLocation(mapWidth/2, mapHeight/2);
                    PathFinder.resetBug();
                }
                if(trySpawn(rc)) {
                    roundNum = rc.getRoundNum();
                    PathFinder.resetBug();
                    continue;
                }
                robotLoc = rc.getLocation();
                if(prevLoc == null) prevLoc = robotLoc;

                if(roundNum <= GameConstants.SETUP_ROUNDS) {
                    SetupPhase.run(rc);
                } else {
                    MainPhase.run(rc);
                }
                if(rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) {
                    rc.buyGlobal(GlobalUpgrade.CAPTURING);
                    System.out.println("Bought Capturing global Upgrade!");
                } else if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
                    rc.buyGlobal(GlobalUpgrade.HEALING);
                    System.out.println("Bought Healing global Upgrade!");
                } else if(rc.canBuyGlobal(GlobalUpgrade.ATTACK)) {
                    rc.buyGlobal(GlobalUpgrade.ATTACK);
                    System.out.println("Bought Action global Upgrade!");
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

    private static boolean trySpawn(RobotController rc) throws GameActionException {
        if (!rc.isSpawned()){
            MapLocation[] spawnArr = rc.getAllySpawnLocations();
            List<MapLocation> spawnList = Arrays.asList(spawnArr);
            Collections.shuffle(spawnList);
            spawnList.toArray(spawnArr);
            for (MapLocation mapLocation : spawnArr) {
                if (rc.canSpawn(mapLocation)) {
                    rc.spawn(mapLocation);
                    return true;
                }
            }
            //System.out.println("Could not spawn because of blockage!");
            return true; // Could not spawn anywhere
        }
        return false;
    }

    public static void MoveAction(RobotController rc, List<Task> moveList, List<Task> actionList) throws GameActionException {
        double actionSum = 0.0; // Will never be zero because of NONE action
        for (Task action : actionList) actionSum += action.value;

        double actionRandom = Math.random();
        double temp = 0.0;
        for (Task action : actionList) {
            temp += action.value / actionSum;
            if (temp > actionRandom) {
                switch (action.type) {
                    case NONE:
                        rc.setIndicatorString("NONE");
                        break;
                    case ATTACK:
                        attack++;
                        rc.attack(action.location);
                        rc.setIndicatorString("Attacking");
                        break;
                    case HEAL:
                        heal++;
                        rc.heal(action.location);
                        rc.setIndicatorString("Healing");
                        break;
                    case PICKUP_FLAG:
                        rc.pickupFlag(action.location);
                        PathFinder.resetBug();
                        rc.setIndicatorString("Picking up flag");
                        break;
                    case BUILD_BOMB:
                        build++;
                        rc.build(TrapType.EXPLOSIVE,action.location);
                        rc.setIndicatorString("Building bomb");
                        break;
                }

                break;
            }
        }

        double moveSum = 0.0; // Will never be zero because of NONE action
        for (Task move : moveList) moveSum += move.value;

        double moveRandom = Math.random();
        temp = 0.0;
        for (Task move : moveList) {
            temp += move.value / moveSum;
            if (temp > moveRandom) {
                prevLoc = robotLoc;
                switch(move.type) {
                    case MOVE:
                        rc.move(robotLoc.directionTo(move.location));
                        break;
                    case FILL:
                        // TODO: do not check if we can fill, this is because of an action cooldown and movement cooldown at the same time
                        if(rc.canFill(move.location)) rc.fill(move.location);
                        break;
                    case NONE:
                        break;
                }
                break;
            }
        }
    }
}
