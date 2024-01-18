package cretplayer1;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainPhase {
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    public static void run(RobotController rc) throws GameActionException {

        // Get info
        MapLocation robotLoc = rc.getLocation();
        List<Direction> movableDir = new ArrayList<>();
        List<MapLocation> attackLocs = new ArrayList<>();
        for(int i = 0; i < 8; i++) {
            MapLocation loc = robotLoc.add(directions[i]);
            if(rc.canAttack(loc)) {
                attackLocs.add(loc);
            } else if(rc.canMove(directions[i])) {
                movableDir.add(directions[i]);
            }
        }

        RobotInfo[] robots = rc.senseNearbyRobots();
        List<RobotInfo> enemies = new ArrayList<>();
        List<RobotInfo> friends = new ArrayList<>();
        for (RobotInfo robot : robots) {
            if (robot.team == RobotPlayer.team) {
                friends.add(robot);
            } else {
                enemies.add(robot);
            }
        }

        List<RobotInfo> healable = new ArrayList<>();
        for (RobotInfo friend : friends) {
            if (rc.canHeal(friend.location)) {
                healable.add(friend);
            }
        }

        // Compute move

        if(rc.canPickupFlag(robotLoc)){
            rc.pickupFlag(robotLoc);
            rc.setIndicatorString("Holding a flag!");
        }
        else if(rc.hasFlag()) {
            MapLocation[] spawnLocs = rc.getAllySpawnLocations();
            MapLocation firstLoc = spawnLocs[0];
            Direction dir = rc.getLocation().directionTo(firstLoc);
            if (rc.canMove(dir)) rc.move(dir);
            else if(!movableDir.isEmpty()) {
                rc.move(movableDir.get(RobotPlayer.rng.nextInt(movableDir.size())));
            }
        }
        else if(!healable.isEmpty()) {
            rc.heal(healable.get(RobotPlayer.rng.nextInt(healable.size())).location);
        }
        else if(!enemies.isEmpty()) {
            Direction dir = rc.getLocation().directionTo(enemies.get(RobotPlayer.id % enemies.size()).location);
            if(movableDir.contains(dir)) {
                rc.move(dir);
            } else if(!attackLocs.isEmpty()) {
                rc.attack(attackLocs.get(RobotPlayer.rng.nextInt(attackLocs.size())));
            }
        } else {
            if(!attackLocs.isEmpty()) {
                rc.attack(attackLocs.get(RobotPlayer.rng.nextInt(attackLocs.size())));
                System.out.println("Take that! Damaged an enemy that was in our way!");
            } else if(!movableDir.isEmpty()) {
                rc.move(movableDir.get(RobotPlayer.rng.nextInt(movableDir.size())));
            }
        }
    }
}