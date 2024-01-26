package cretplayer2_2;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

public class MainPhase {

    public static void runMainPhase(RobotController rc) throws GameActionException {
        Communication.updateRobot(rc);

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyTeammates = rc.senseNearbyRobots(-1, rc.getTeam());

        FlagInfo[] allFlags = rc.senseNearbyFlags(-1);
        for (FlagInfo flag : allFlags) {
            Communication.updateFlagInfo(rc, flag);
        }

        ArrayList<MapLocation> flagLocations = Communication.getEnemyFlagLocations(rc);

        MapLocation closestFlag = Pathfinder.findClosestLocation(rc.getLocation(), flagLocations);
        if (closestFlag != null) {
            if (rc.canPickupFlag(closestFlag))
                rc.pickupFlag(closestFlag);
        }

        // Prioritize actions based on role
        if (RobotPlayer.role == Role.MINION) {
            // TODO: Probability based attack/heal
            runAttack(rc, nearbyEnemies);
            runHeal(rc, nearbyTeammates);
        } else if (RobotPlayer.role == Role.BUILDER) {
            runBuild(rc, nearbyEnemies, nearbyTeammates);
            runHeal(rc, nearbyTeammates);
            runAttack(rc, nearbyEnemies);
        } else {
            runGuardian(rc, nearbyEnemies);
            runAttack(rc, nearbyEnemies);
            return;
        }

        if (!rc.hasFlag()) {

            runFindFlags(rc, nearbyTeammates);

            // Send some bots to defend flags if there is a warning

        } else {

            // if we have the flag, move towards the closest ally spawn zone
            MapLocation[] spawnLocs = rc.getAllySpawnLocations();
            MapLocation closestSpawn = Pathfinder.findClosestLocation(rc.getLocation(), Arrays.asList(spawnLocs));
            Direction dir = Pathfinder.bugNavTwo(rc, closestSpawn, true);
            if(dir != null) {
                FlagInfo[] flag = rc.senseNearbyFlags(0,rc.getTeam().opponent());
                rc.move(dir);
                if(Arrays.asList(spawnLocs).contains(rc.getLocation())) {
                    Communication.capturedFlag(rc, flag[0].getID());
                }
            }
        }
    }

    private static  void runHeal(RobotController rc, RobotInfo[] nearbyTeammates) throws GameActionException {
        // try to heal friendly robots
        for (RobotInfo robot : nearbyTeammates) {
            if (rc.canHeal(robot.getLocation())) {
                rc.heal(robot.getLocation());
            }
        }
    }

    private static void runAttack(RobotController rc, RobotInfo[] nearbyEnemies) throws GameActionException {
        // attack enemies, prioritizing enemies that have your flag
        for (RobotInfo robot : nearbyEnemies) {
            if (robot.hasFlag()) {
                Pathfinder.bugNavTwo(rc, robot.getLocation(), true);
                if (rc.canAttack(robot.getLocation()))
                    rc.attack(robot.getLocation());
            }
        }
        for (RobotInfo robot : nearbyEnemies) {
            if (rc.canAttack(robot.getLocation())) {
                rc.attack(robot.getLocation());
            }
        }
    }

    private static void runBuild(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyTeammates) throws GameActionException {
        //STUN, EXPLOSIVE, WATER

    }

    public static void runGuardian(RobotController rc, RobotInfo[] nearbyEnemies) throws GameActionException {
        // Always move towards flag location
        MapLocation homeLoc = RobotPlayer.flagSpawnLocation[RobotPlayer.guardianID];
        rc.setIndicatorDot(homeLoc, 0, 0, 255);

        // If we are not at the location then return
        if (!rc.getLocation().equals(homeLoc)) {
            Pathfinder.bugNavTwo(rc, homeLoc, true);
        }
        // Warn teammates if there are enemies nearby
        /*MapLocation curWarning = Communication.getLocation(rc, Communication.FLAG_WARNING_IDX);
        if (nearbyEnemies.length > 0) {
            if (curWarning == null)
                Communication.updateWarningInfo(rc, homeLoc, Communication.FLAG_WARNING_IDX);
        } else if (curWarning != null && curWarning.equals(homeLoc)) {
            Communication.updateWarningInfo(rc, null, Communication.FLAG_WARNING_IDX);
        }*/
    }

    public static void runFindFlags(RobotController rc, RobotInfo[] nearbyTeammates) throws GameActionException {
        // move towards the closest enemy flag (including broadcast locations)
        ArrayList<MapLocation> flagLocations = Communication.getEnemyFlagLocations(rc);
        ArrayList<MapLocation> flagSpawnLocations = Communication.getEnemyFlagSpawnLocations(rc);

        // Runs until we find the first enemy flag spawn location
        if (flagSpawnLocations.isEmpty()) {
            MapLocation[] broadcastLocs = rc.senseBroadcastFlagLocations();
            for (MapLocation flagLoc : broadcastLocs) {
                // Jitter the broadcast location to allow for some exploration
                MapLocation newLoc = flagLoc;
                for (int i = 0; i < 5; i++) {
                    newLoc = newLoc.add(RobotPlayer.directions[RobotPlayer.random.nextInt(8)]);
                }
                flagLocations.add(newLoc);
                rc.setIndicatorDot(newLoc, 255, 0, 0);
            }
        }


        MapLocation closestFlag = Pathfinder.findClosestLocation(rc.getLocation(), flagLocations);
        if(closestFlag != null) {
            Direction dir = Pathfinder.bugNavTwo(rc, closestFlag, true);
            for(RobotInfo teammate : nearbyTeammates) {
                if(teammate.hasFlag() && flagLocations.contains(teammate.getLocation())) { // Our teammate has the flag
                    if(dir != null) {
                        rc.setIndicatorString("Following flag holder");
                        int dist = rc.getLocation().add(dir).distanceSquaredTo(closestFlag);
                        if(dist > 2) {
                            rc.move(dir);
                        } else { // Too close means we move away
                            if(rc.canMove(dir.opposite())) {
                                rc.move(dir.opposite());
                            }
                        }
                        return;
                    }
                }
            }


            if(dir != null) {
                rc.move(dir);
            }
        } else {
            // if there are no known enemy flags, explore randomly
            Pathfinder.explore(rc);
        }
    }

    public static void runDefendFlags(RobotController rc, MapLocation loc) throws GameActionException {

    }


}
