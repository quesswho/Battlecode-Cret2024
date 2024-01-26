package cretplayer2_1;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainPhase {
    static MapLocation lastLeaderPos = null;



    public static void runMainPhase(RobotController rc) throws GameActionException {

        if (RobotPlayer.personalID == 0) {
            for (int i = 0; i < 64; i++) {
                Communication.setUnupdated(rc, i);
            }
        }
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyTeammates = rc.senseNearbyRobots(-1, rc.getTeam());
        Communication.updateEnemyInfo(rc, rc.getLocation(), nearbyEnemies.length);

        FlagInfo[] allFlags = rc.senseNearbyFlags(-1);
        for (FlagInfo flag : allFlags) {
            int flagID = flag.getID();
            int idx = RobotPlayer.flagIDToIdx(rc, flagID);
            Communication.updateFlagInfo(rc, flag.getLocation(), flag.isPickedUp(), flag.getTeam(), idx);
        }

        ArrayList<MapLocation> flagLocs = new ArrayList<>();
        FlagInfo[] enemyFlags = rc.senseNearbyFlags(-1, rc.getTeam().opponent());
        for (FlagInfo flag : enemyFlags) {
            // Move towards flag, but not too close to give teammates that are carrying the flag space
            if (!flag.isPickedUp() || flag.getLocation().distanceSquaredTo(rc.getLocation()) > 9)
                flagLocs.add(flag.getLocation());
            else
                flagLocs.add(rc.getLocation().add(rc.getLocation().directionTo(flag.getLocation()).opposite()));
        }

        MapLocation closestFlag = Pathfinder.findClosestLocation(rc.getLocation(), flagLocs);
        if (closestFlag != null) {
            if (rc.canPickupFlag(closestFlag))
                rc.pickupFlag(closestFlag);
        }

        // Prioritize actions based on role
        if (RobotPlayer.role == Role.MINION) {
            runAttack(rc, nearbyEnemies);
            runHeal(rc, nearbyTeammates);
        } else if (RobotPlayer.role == Role.BUILDER) {
            runBuild(rc, nearbyEnemies, nearbyTeammates);
            runHeal(rc, nearbyTeammates);
            runAttack(rc, nearbyEnemies);
        } else {
            runGuardian(rc, nearbyEnemies);
            return;
        }

        if (!rc.hasFlag()) {
            // Send some bots to defend flags if there is a warning
            MapLocation warnLoc = Communication.getLocation(rc, Communication.FLAG_WARNING_IDX);
            if (warnLoc != null && rc.getID() % 2 == 0)
                runDefendFlags(rc, warnLoc);
            else {
                // Otherwise, try to follow a leader
                rc.setIndicatorString("Attempting to follow leader!");
                boolean followedLeader = tryFollowLeader(rc);
                if (!followedLeader)
                    runFindFlags(rc);
            }
        } else {
            // if we have the flag, move towards the closest ally spawn zone
            MapLocation[] spawnLocs = rc.getAllySpawnLocations();
            MapLocation closestSpawn = Pathfinder.findClosestLocation(rc.getLocation(), Arrays.asList(spawnLocs));
            Pathfinder.bugNavTwo(rc, closestSpawn, true);
        }
    }

    private static boolean tryFollowLeader(RobotController rc) throws GameActionException{
        int leaderID = -1;
        MapLocation leaderPos = null;
        boolean foundLeader = false;
        boolean isLeaderHoldingFlag = false;

        // Attackers will only be leaders
        if (RobotPlayer.role == Role.MINION)
            return false;

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies){
            int allyID = ally.ID;
            if (allyID != rc.getID() && allyID > leaderID){
                leaderID = allyID;
                leaderPos = ally.location;
                foundLeader = true;
                isLeaderHoldingFlag =  ally.hasFlag;
            }
        }
        if (foundLeader && !isLeaderHoldingFlag) {
            // Try to move towards the leader unless the leader is stuck. Then we should try and create space
            if (lastLeaderPos != null && lastLeaderPos.equals(leaderPos)) {
                MapLocation awayPos = rc.getLocation().add(rc.getLocation().directionTo(leaderPos).opposite());
                Pathfinder.moveTowards(rc, awayPos, true);
            } else {
                Pathfinder.moveTowards(rc, leaderPos,true);
            }
            lastLeaderPos = leaderPos;
            rc.setIndicatorString("Following robot with id " + leaderID);
            return true;
        } else if(foundLeader) {
            // Try to move towards the leader unless the leader is stuck. Then we should try and create space
            if (lastLeaderPos != null && lastLeaderPos.equals(leaderPos)) {
                MapLocation awayPos = rc.getLocation().add(rc.getLocation().directionTo(leaderPos).opposite());
                Pathfinder.moveTowards(rc, awayPos, true);
            } else {
                Pathfinder.moveTowards(rc, leaderPos, 2,true);
            }
            lastLeaderPos = leaderPos;
            rc.setIndicatorString("Following robot with flag and id " + leaderID);
            return true;
        }
        return false;
    }
    private static  void runHeal(RobotController rc, RobotInfo[] nearbyTeammates) throws GameActionException {
        // try to heal friendly robots
        for (RobotInfo robot : nearbyTeammates) {
            if (rc.canHeal(robot.getLocation()))
                rc.heal(robot.getLocation());
        }
    }

    private static void runAttack(RobotController rc, RobotInfo[] nearbyEnemies) throws GameActionException {
        // attack enemies, prioritizing enemies that have your flag
        for (RobotInfo robot : nearbyEnemies) {
            if (robot.hasFlag()) {
                Pathfinder.moveTowards(rc, robot.getLocation(), true);
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

        //build stun traps if we are nearby any flags being carried
        for (int i = 0; i <= Communication.LAST_FLAG_IDX; i++){
            if (Communication.getIfUpdated(rc, i) && Communication.getCarried(rc, i)){
                MapLocation flagLoc = Communication.getLocation(rc, i);
                if (flagLoc.distanceSquaredTo(rc.getLocation()) < 36){
                    if (rc.canBuild(TrapType.STUN, rc.getLocation())){
                        rc.build(TrapType.STUN, rc.getLocation());
                        return;
                    }
                }
            }
        }

        //build a water trap if location is sparse and it hinders enemies more than it hinders us
        if (nearbyEnemies.length > nearbyTeammates.length + 2 && nearbyEnemies.length < 7 && nearbyTeammates.length <= 3){
            if (rc.canBuild(TrapType.WATER, rc.getLocation())){
                rc.build(TrapType.WATER, rc.getLocation());
                return;
            }
        }

        //default to building an explosive trap occasionally
        if (RobotPlayer.random.nextBoolean()){
            if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())){
                rc.build(TrapType.EXPLOSIVE, rc.getLocation());
            }
        }
    }

    public static void runGuardian(RobotController rc, RobotInfo[] nearbyEnemies) throws GameActionException {
        // Always move towards flag location
        MapLocation homeLoc = RobotPlayer.flagGoal[RobotPlayer.guardianID];
        rc.setIndicatorDot(homeLoc, 0, 0, 255);

        // If we are far away then return back
        if (!rc.getLocation().equals(homeLoc)) {
            Pathfinder.moveTowards(rc, homeLoc, true);
        }
        // Warn teammates if there are enemies nearby
        MapLocation curWarning = Communication.getLocation(rc, Communication.FLAG_WARNING_IDX);
        if (nearbyEnemies.length > 0) {
            if (curWarning == null)
                Communication.updateWarningInfo(rc, homeLoc, Communication.FLAG_WARNING_IDX);
        } else if (curWarning != null && curWarning.equals(homeLoc)) {
            Communication.updateWarningInfo(rc, null, Communication.FLAG_WARNING_IDX);
        }
    }

    public static void runFindFlags(RobotController rc) throws GameActionException {
        // move towards the closest enemy flag (including broadcast locations)
        ArrayList<MapLocation> flagLocs = new ArrayList<>();
        FlagInfo[] enemyFlags = rc.senseNearbyFlags(-1, rc.getTeam().opponent());
        for (FlagInfo flag : enemyFlags) {
            // Move towards flag, but not too close to give teammates that are carrying the flag space
            if (!flag.isPickedUp() || flag.getLocation().distanceSquaredTo(rc.getLocation()) > 9)
                flagLocs.add(flag.getLocation());
            else
                flagLocs.add(rc.getLocation().add(rc.getLocation().directionTo(flag.getLocation()).opposite()));
        }
        if (flagLocs.isEmpty()) {
            for (int i = 0; i <= Communication.LAST_FLAG_IDX; i++) {
                if (Communication.getTeam(rc, i) == rc.getTeam().opponent() && Communication.getIfUpdated(rc, i)) {
                    flagLocs.add(Communication.getLocation(rc, i));
                }
            }
            if (flagLocs.isEmpty()) {
                MapLocation[] broadcastLocs = rc.senseBroadcastFlagLocations();
                for (MapLocation flagLoc : broadcastLocs) {
                    // Jitter the broadcast location to allow for some exploration
                    MapLocation newLoc = flagLoc;
                    for (int i = 0; i < 5; i++) {
                        newLoc = newLoc.add(RobotPlayer.directions[RobotPlayer.random.nextInt(8)]);
                    }
                    flagLocs.add(newLoc);
                    rc.setIndicatorDot(newLoc, 255, 0, 0);
                }
            }
        }

        MapLocation closestFlag = Pathfinder.findClosestLocation(rc.getLocation(), flagLocs);
        if (closestFlag != null) {
            Pathfinder.moveTowards(rc, closestFlag, true);

            if (rc.canPickupFlag(closestFlag))
                rc.pickupFlag(closestFlag);
        } else {
            // if there are no dropped enemy flags, explore randomly
            Pathfinder.explore(rc);
        }
    }

    public static void runDefendFlags(RobotController rc, MapLocation loc) throws GameActionException {
        Pathfinder.moveTowards(rc, loc, true);
    }


}
