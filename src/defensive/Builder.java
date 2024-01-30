package defensive;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

public class Builder {
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
    public static int flagLast = -1;
    static int cachedRound = 0;

    static final boolean FOLLOW_FLAG = true;


    public static void runBuilder(RobotController rc) throws GameActionException {
        nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        nearbyTeammates = rc.senseNearbyRobots(-1, rc.getTeam());
        // TODO: Use one senseNearbyRobots for less bytecode

        leader = null;
        chaseTarget = null;
        attackTarget = null;
        healTarget = null;
        teamStrength = 1;
        instantkill = false;

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
                    newLoc = newLoc.add(RobotPlayer.directions[RobotPlayer.random.nextInt(8)]);
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
}
