package cretplayer2_2;

import battlecode.common.*;
import cretplayer2_2.Communication;
import cretplayer2_2.Pathfinder;
import cretplayer2_2.RobotPlayer;

import java.util.ArrayList;
import java.util.Arrays;

public class Minion {
    public static final int ATTACK_DISTANCE = 4;
    public static final int HEAL_DISTANCE = 4;
    public static int DAMAGE = 150; // TODO: Compute additional damage from levels
    public static final int BASE_DAMAGE = 150;
    public static final int DAMAGE_UPGRADE = 60;
    public static final int BASE_HEAL = 150;
    public static final int HEAL_UPGRADE = 50;
    public static int VISION_DIS = 25;
    static RobotInfo[] nearbyEnemies;
    static RobotInfo[] nearbyTeammates;
    static int teamStrength;
    static RobotInfo leader = null;
    static RobotInfo chaseTarget = null;
    static RobotInfo attackTarget = null;
    static RobotInfo healTarget = null;
    static boolean instantkill = false;
    static MapLocation cachedEnemyLocation = null;
    static int flagLast = -1;
    static int cachedRound = 0;
    private static void sense(RobotController rc) throws GameActionException {
        nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        nearbyTeammates = rc.senseNearbyRobots(-1, rc.getTeam());
        // TODO: Use one senseNearbyRobots for less bytecode

        leader = null;
        chaseTarget = null;
        attackTarget = null;
        healTarget = null;
        teamStrength = 1;
        instantkill = false;
        
        for (RobotInfo robot : nearbyTeammates) {
            if (leader == null || robot.getHealth() > leader.getHealth()) {
                leader = robot;
            }
            teamStrength += 1;
        }

        for (RobotInfo robot : nearbyEnemies) {
            teamStrength -= 1;
            if (robot.location.distanceSquaredTo(rc.getLocation()) > ATTACK_DISTANCE) {
                chaseTarget = robot;
            }
        }
        attackTarget = getBestTarget(rc);
        healTarget = getBestHealTarget(rc);
    }

    public static void runMinion(RobotController rc) throws GameActionException {
        sense(rc);

        /*if (nearbyEnemies.length > 4) {
            if (rc.canBuild(TrapType.STUN, rc.getLocation())) {
                rc.build(TrapType.STUN, rc.getLocation());
            }
        }*/

        if(attackTarget != null) {
            RobotInfo deadTarget = null;
            if(rc.canAttack(attackTarget.location)) {
                if (attackTarget.health <= DAMAGE) {
                    deadTarget = attackTarget;
                }
                rc.attack(attackTarget.location);
            }

            // Find the nearest enemy excluding any dead targets
            int minDis = Integer.MAX_VALUE;
            cachedEnemyLocation = null;
            for (RobotInfo enemy : nearbyEnemies) {
                int dis = enemy.location.distanceSquaredTo(rc.getLocation());
                if (enemy != deadTarget && dis < minDis) {
                    cachedEnemyLocation = enemy.location;
                    minDis = dis;
                }
            }

            if (cachedEnemyLocation != null && rc.isMovementReady()) {
                fallback(rc, cachedEnemyLocation);
            }

        }

        if (rc.isMovementReady() && rc.isActionReady()) {
            if (chaseTarget != null) {
                cachedEnemyLocation = chaseTarget.location;
                cachedRound = rc.getRoundNum();
                if (rc.getHealth() > chaseTarget.health || teamStrength > 2) {
                    chase(rc, chaseTarget.location);
                } else { // we are at disadvantage, pull back
                    fallback(rc, chaseTarget.location);
                }
            } else if (cachedEnemyLocation != null && rc.getRoundNum() - cachedRound <= 2) {
                chase(rc, cachedEnemyLocation);
            }
        }

        // try to heal friendly robots
        for (RobotInfo robot : nearbyTeammates) {
            if (rc.canHeal(robot.getLocation())) {
                rc.heal(robot.getLocation());
            }
        }

        MapLocation[] spawnLocs = rc.getAllySpawnLocations();

        if (!rc.hasFlag()) {
            runFindFlags(rc, nearbyTeammates);
        } else {
            // if we have the flag, move towards the closest ally spawn zone

            MapLocation closestSpawn = Pathfinder.findClosestLocation(rc.getLocation(), Arrays.asList(spawnLocs));
            Direction dir = Pathfinder.directionToward(rc, closestSpawn);
            if(dir != null) {
                rc.move(dir);
            }
        }

        if(flagLast > 0 && !rc.hasFlag() && Arrays.asList(spawnLocs).contains(rc.getLocation())) {
            Communication.capturedFlag(rc, flagLast);
        }

        FlagInfo[] flag = rc.senseNearbyFlags(0,rc.getTeam().opponent());
        if(flag.length > 0) {
            flagLast = flag[0].getID();
        } else {
            flagLast = -1;
        }

    }

    private static void chase(RobotController rc, MapLocation location) throws GameActionException{
        Direction forwardDir = rc.getLocation().directionTo(location);
        Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;

        // pick a direction to chase to minimize the number of enemies that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir) && rc.getLocation().add(dir).distanceSquaredTo(location) <= ATTACK_DISTANCE) {
                int canSee = 0;
                for (RobotInfo enemy : nearbyEnemies){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemy.location);
                    if (newDis <= VISION_DIS) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                } else if (minCanSee == canSee && isDiagonal(bestDir) && !isDiagonal(dir)) {  // TODO: Test without diagonal
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null) {
            RobotPlayer.indicator += String.format("chase%s,", location);
            rc.move(bestDir);
        } else {
            RobotPlayer.indicator += "failchase,";
        }
    }
    private static void fallback(RobotController rc, MapLocation location) throws GameActionException {
        Direction backDir = rc.getLocation().directionTo(location).opposite();
        Direction[] dirs = {Direction.CENTER, backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        // pick a direction to move back to minimize the number of enemies that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                int canSee = 0;
                for (RobotInfo enemy : nearbyEnemies){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemy.location);
                    if (newDis <= VISION_DIS) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                } else if (minCanSee == canSee && isDiagonal(bestDir) && !isDiagonal(dir)) { // TODO: Test without diagonal
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null && bestDir != Direction.CENTER){
            RobotPlayer.indicator += "kite,";
            rc.move(bestDir);
        }
    }

    public static void runFindFlags(RobotController rc, RobotInfo[] nearbyTeammates) throws GameActionException {

        // Help out if flag is taken nearby
        ArrayList<MapLocation> allyFlags = Communication.getAllyFlagLocations(rc);
        for(MapLocation allyLoc : allyFlags) {
            if(!Arrays.asList(RobotPlayer.flagSpawnLocation).contains(allyLoc)) { // If flag is taken by enemy
                if(rc.getLocation().distanceSquaredTo(allyLoc) < 100) {
                    Direction dir = Pathfinder.directionToward(rc, allyLoc);
                    if(dir != null) {
                        rc.move(dir);
                        RobotPlayer.indicator += "Defending flag!";
                        return;
                    }
                }
            }
        }

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

            }
        }


        MapLocation closestFlag = Pathfinder.findClosestLocation(rc.getLocation(), flagLocations);
        if(closestFlag != null) {

            Direction dir = Pathfinder.directionToward(rc, closestFlag);
            for(RobotInfo teammate : nearbyTeammates) {
                if(teammate.hasFlag() && flagLocations.contains(teammate.getLocation())) { // Our teammate has the flag
                    if(dir != null) {
                        RobotPlayer.indicator += "Following flag holder";
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

    private static RobotInfo getBestTarget(RobotController rc) throws GameActionException {
        int minHitReqired = Integer.MAX_VALUE;
        RobotInfo rv = null;
        int minDis = Integer.MAX_VALUE;
        RobotInfo self = rc.senseRobotAtLocation(rc.getLocation());
        for (RobotInfo enemy : nearbyEnemies) {
            int dis = enemy.location.distanceSquaredTo(rc.getLocation());
            if (dis > ATTACK_DISTANCE) {
                continue;
            }
            if(rv==null) rv = enemy;
            // Prioritize enemy with flags
            // TODO: We may exit out early and not attack enemy with a flag
            if(enemy.hasFlag) return enemy;

            int totalDamage = computeDamage(self);

            if (enemy.getHealth() <= totalDamage) {
                return enemy;
            }

            // Compute how much damage we can do collectively

            for (RobotInfo friend : nearbyTeammates) {
                int friendEnemyDis = friend.location.distanceSquaredTo(enemy.location);
                if (friendEnemyDis <= ATTACK_DISTANCE) {
                    totalDamage += computeDamage(friend);
                }
            }

            int hitRequired = (int) Math.ceil((double) enemy.getHealth() / totalDamage);
            if (hitRequired < minHitReqired) {
                minHitReqired = hitRequired;
                rv = enemy;
                minDis = enemy.location.distanceSquaredTo(rc.getLocation());
            } else if (hitRequired == minHitReqired) {
                if (dis < minDis) { // Prioritize closer enemies
                    rv = enemy;
                    minDis = dis;
                }
            }
            if(hitRequired == 1) instantkill = true;
        }
        return rv;
    }

    private static RobotInfo getBestHealTarget(RobotController rc) throws GameActionException {
        int maxDamage = 0;
        RobotInfo result = null;
        int minDis = Integer.MAX_VALUE;
        RobotInfo self = rc.senseRobotAtLocation(rc.getLocation());
        for (RobotInfo friend : nearbyTeammates) {
            int dis = friend.location.distanceSquaredTo(rc.getLocation());
            if (dis > HEAL_DISTANCE) {
                continue;
            }
            if(friend.health >= 1000) continue;

            if(result==null) result = friend;
            // Prioritize enemy with flags
            if(friend.hasFlag) return friend;


            // Compute how much damage we can do collectively
            // TODO: Cache damage of each teammate and self
            int totalDamage = computeDamage(self);
            for (RobotInfo enemy : nearbyEnemies) {
                int friendEnemyDis = friend.location.distanceSquaredTo(enemy.location);
                if (friendEnemyDis <= ATTACK_DISTANCE) {
                    totalDamage += computeDamage(friend);
                }
            }

            if (totalDamage >= 1000) {
                maxDamage = 1000;
                if (dis < minDis) { // Prioritize closer friends
                    result = friend;
                    minDis = dis;
                }
            } else if (totalDamage >= maxDamage) {
                maxDamage = totalDamage;
                result = friend;
            }
        }
        return result;
    }

    static int computeDamage(RobotInfo robot) {
        int level = robot.attackLevel;
        int base = BASE_DAMAGE;
        if(RobotPlayer.attackUpgrade) {
            base += DAMAGE_UPGRADE;
        }
        int jail = RobotPlayer.jailedPenalty ? 1 : 0;

        switch(level) {
            case 0:
                return base-jail;
            case 1:
                return (int) Math.round((base-2*jail)*1.05);
            case 2:
                return (int) Math.round((base-2*jail)*1.07);
            case 3:
                return (int) Math.round((base-5*jail)*1.1);
            case 4:
                return (int) Math.round((base-5*jail)*1.3);
            case 5:
                return (int) Math.round((base-10*jail)*1.35);
            case 6:
                return (int) Math.round((base-12*jail)*1.6);
        }
        System.out.println("Could not calculate attack damage!");
        return 150;
    }

    static int computeHeal(RobotInfo robot) {
        int level = robot.healLevel;
        int base = BASE_HEAL;
        if(RobotPlayer.healingUpgrade) {
            base += HEAL_UPGRADE;
        }
        int jail = RobotPlayer.jailedPenalty ? 1 : 0;

        switch(level) {
            case 0:
                return base-jail;
            case 1:
                return (int) Math.round((base-5*jail)*1.03);
            case 2:
                return (int) Math.round((base-5*jail)*1.05);
            case 3:
                return (int) Math.round((base-10*jail)*1.07);
            case 4:
                return (int) Math.round((base-10*jail)*1.1);
            case 5:
                return (int) Math.round((base-15*jail)*1.15);
            case 6:
                return (int) Math.round((base-18*jail)*1.25);
        }
        System.out.println("Could not calculate attack damage!");
        return 150;
    }

    static boolean isDiagonal(Direction dir) {
        return dir.dx * dir.dy != 0;
    }
}
