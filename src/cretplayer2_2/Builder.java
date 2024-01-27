package cretplayer2_2;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

public class Builder {
    public static void runBuilder(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyTeammates) throws GameActionException {
        // Build traps near enemies
        if (nearbyEnemies.length > 4) {
            if (rc.canBuild(TrapType.STUN, rc.getLocation())){
                rc.build(TrapType.STUN, rc.getLocation());
            }
        } else if(rc.getCrumbs() > 2000 && nearbyEnemies.length > 2) { // Usually means map is very big
            if(rc.canBuild(TrapType.STUN, rc.getLocation())) {
                rc.build(TrapType.STUN, rc.getLocation());
            }
        }

        // try to heal friendly robots
        for (RobotInfo robot : nearbyTeammates) {
            if (robot.hasFlag()) {
                if (rc.canHeal(robot.getLocation())) {
                    rc.heal(robot.getLocation());
                }
            }
        }

        for (RobotInfo robot : nearbyTeammates) {
            if (rc.canHeal(robot.getLocation())) {
                rc.heal(robot.getLocation());
            }
        }

        // attack enemies, prioritizing enemies that have your flag
        for (RobotInfo robot : nearbyEnemies) {
            if (robot.hasFlag()) {
                Pathfinder.moveToward(rc, robot.getLocation());
                if (rc.canAttack(robot.getLocation()))
                    rc.attack(robot.getLocation());
            }
        }
        for (RobotInfo robot : nearbyEnemies) {
            if (rc.canAttack(robot.getLocation())) {
                rc.attack(robot.getLocation());
            }
        }

        if (!rc.hasFlag()) {
            runFindFlags(rc, nearbyTeammates);
        } else {
            // if we have the flag, move towards the closest ally spawn zone
            MapLocation[] spawnLocs = rc.getAllySpawnLocations();
            MapLocation closestSpawn = Pathfinder.findClosestLocation(rc.getLocation(), Arrays.asList(spawnLocs));
            Direction dir = Pathfinder.directionToward(rc, closestSpawn);
            if(dir != null) {
                FlagInfo[] flag = rc.senseNearbyFlags(0,rc.getTeam().opponent());
                rc.move(dir);
                if(Arrays.asList(spawnLocs).contains(rc.getLocation())) {
                    Communication.capturedFlag(rc, flag[0].getID());
                }
            }
        }
    }

    public static void runFindFlags(RobotController rc, RobotInfo[] nearbyTeammates) throws GameActionException {

        // Help out if flag is taken nearby
        /*ArrayList<MapLocation> allyFlags = Communication.getAllyFlagLocations(rc);
        for(MapLocation allyLoc : allyFlags) {
            if(!Arrays.asList(RobotPlayer.flagSpawnLocation).contains(allyLoc)) { // If flag is taken by enemy
                if(rc.getLocation().distanceSquaredTo(allyLoc) < 50) {
                    Direction dir = Pathfinder.bugNavTwoDirection(rc, allyLoc, true);
                    if(dir != null) {
                        rc.move(dir);
                        rc.setIndicatorString("Defending flag!");
                        return;
                    }
                }
            }
        }*/

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
                    newLoc = newLoc.add(cretplayer2_2.RobotPlayer.directions[RobotPlayer.random.nextInt(8)]);
                }
                flagLocations.add(newLoc);
                rc.setIndicatorDot(newLoc, 255, 0, 0);
            }
        }


        MapLocation closestFlag = Pathfinder.findClosestLocation(rc.getLocation(), flagLocations);
        if(closestFlag != null) {
            Direction dir = Pathfinder.directionToward(rc, closestFlag);
            for(RobotInfo teammate : nearbyTeammates) {
                if(teammate.hasFlag() && flagLocations.contains(teammate.getLocation())) { // Our teammate has the flag
                    if(dir != null) {
                        rc.setIndicatorString("Following flag holder");
                        /*if(rc.getLocation().directionTo(closestFlag).equals(teammate.getLocation().directionTo(closestFlag))) {
                            if(rc.canMove(rc.getLocation().directionTo(closestFlag))) {
                                rc.move(rc.getLocation().directionTo(closestFlag));
                            }
                        }*/
                        int dist = rc.getLocation().add(dir).distanceSquaredTo(closestFlag);
                        if(dist > 4) {
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
                if(rc.canMove(dir)) rc.move(dir);
            }
        } else {
            // if there are no known enemy flags, explore randomly
            Pathfinder.explore(rc);
        }
    }


}
