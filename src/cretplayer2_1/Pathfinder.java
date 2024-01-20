package cretplayer2_1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.List;

public class Pathfinder {

    private static Direction dir;

    public static void moveTowards(RobotController rc, MapLocation loc, boolean fill) throws GameActionException {
        if(rc.getLocation().equals(loc)) return;
        Direction dir = rc.getLocation().directionTo(loc);
        if(fill & rc.canFill(rc.getLocation().add(dir))) rc.fill(rc.getLocation().add(dir));
        bugNavZero(rc, loc);
    }

    public static void moveTowards(RobotController rc, MapLocation loc, int distance, boolean fill) throws GameActionException {
        if(rc.getLocation().equals(loc)) return;
        Direction dir = rc.getLocation().directionTo(loc);
        if(fill & rc.canFill(rc.getLocation().add(dir))) rc.fill(rc.getLocation().add(dir));
        bugNavZero(rc, loc, distance);
    }

    public static void explore(RobotController rc) throws GameActionException {
        if(rc.isMovementReady()) {
            MapLocation[] crumbLocs = rc.senseNearbyCrumbs(-1);
            if(crumbLocs.length > 0) {
                moveTowards(rc, crumbLocs[0], true);
            }

            if(dir == null || !rc.canMove(dir)) {
                dir = RobotPlayer.directions[RobotPlayer.random.nextInt(8)];
            }
            if(rc.canMove(dir)) rc.move(dir);
            else if(rc.canFill(rc.getLocation().add(dir))) rc.fill(rc.getLocation().add(dir));
        }
    }

    public static void bugNavZero(RobotController rc, MapLocation destination) throws GameActionException{
        Direction bugDir = rc.getLocation().directionTo(destination);

        if(rc.canMove(bugDir)){
            rc.move(bugDir);
        } else {
            for(int i = 0; i < 8; i++){
                if(rc.canMove(bugDir)){
                    rc.move(bugDir);
                    break;
                } else {
                    bugDir = bugDir.rotateLeft();
                }
            }
        }
    }

    public static void bugNavZero(RobotController rc, MapLocation destination, int distance) throws GameActionException{
        Direction bugDir = rc.getLocation().directionTo(destination);

        if(rc.canMove(bugDir) && rc.getLocation().add(bugDir).distanceSquaredTo(destination) > distance){
            rc.move(bugDir);
        } else {
            for(int i = 0; i < 8; i++){
                if(rc.canMove(bugDir) && rc.getLocation().add(bugDir).distanceSquaredTo(destination) > distance){
                    rc.move(bugDir);
                    break;
                } else {
                    bugDir = bugDir.rotateLeft();
                }
            }
        }
    }
    private static int bugState = 0; // 0 head to target, 1 circle obstacle
    private static MapLocation closestObstacle = null;
    private static int closestObstacleDist = 10000;
    private static Direction bugDir = null;

    public static void resetBug(){
        bugState = 0; // 0 head to target, 1 circle obstacle
        closestObstacle = null;
        closestObstacleDist = 10000;
        bugDir = null;
    }
    public static void bugNavOne(RobotController rc, MapLocation destination, boolean fill) throws GameActionException{
        if(!rc.isMovementReady()) return;
        rc.setIndicatorString("Navigating with state " + bugState);
        if(bugState == 0) {
            bugDir = rc.getLocation().directionTo(destination);
            if(fill & rc.canFill(rc.getLocation().add(bugDir))) {
                rc.fill(rc.getLocation().add(bugDir));
            } else if(rc.canMove(bugDir)) { // should check if its passable
                rc.move(bugDir);
            } else {
                bugState = 1;
                closestObstacle = null;
                closestObstacleDist = 10000;
            }
        } else {
            if(rc.getLocation().distanceSquaredTo(destination) < closestObstacleDist){
                closestObstacleDist = rc.getLocation().distanceSquaredTo(destination);
                closestObstacle = rc.getLocation();
            }

            if(rc.getLocation().equals(closestObstacle)){
                bugState = 0;
            }

            for(int i = 0; i < 8; i++) {
                if(fill & rc.canFill(rc.getLocation().add(bugDir))) {
                    rc.fill(rc.getLocation().add(bugDir));
                    break;
                } else if(rc.canMove(bugDir)){
                    rc.move(bugDir);
                    bugDir = bugDir.rotateRight();
                    bugDir = bugDir.rotateRight();
                    break;
                } else {
                    bugDir = bugDir.rotateLeft();
                }
            }
        }
    }

    public static void bugNavOne(RobotController rc, MapLocation destination, MapLocation avoid, int distance, boolean fill) throws GameActionException{
        if(!rc.isMovementReady()) return;
        rc.setIndicatorString("Navigating with state " + bugState);
        if(bugState == 0) {
            bugDir = rc.getLocation().directionTo(destination);
            if(fill & rc.canFill(rc.getLocation().add(bugDir))) {
                rc.fill(rc.getLocation().add(bugDir));
            } else if(rc.canMove(bugDir) && rc.getLocation().distanceSquaredTo(avoid) >= distance) { // should check if its passable
                rc.move(bugDir);
            } else {
                bugState = 1;
                closestObstacle = null;
                closestObstacleDist = 10000;
            }
        } else {
            if(rc.getLocation().distanceSquaredTo(destination) < closestObstacleDist){
                closestObstacleDist = rc.getLocation().distanceSquaredTo(destination);
                closestObstacle = rc.getLocation();
            }

            if(rc.getLocation().equals(closestObstacle)){
                bugState = 0;
            }

            for(int i = 0; i < 8; i++) {
                if(fill & rc.canFill(rc.getLocation().add(bugDir))) {
                    rc.fill(rc.getLocation().add(bugDir));
                    break;
                } else if(rc.canMove(bugDir) && rc.getLocation().distanceSquaredTo(avoid) >= distance){
                    rc.move(bugDir);
                    bugDir = bugDir.rotateRight();
                    bugDir = bugDir.rotateRight();
                    break;
                } else {
                    bugDir = bugDir.rotateLeft();
                }
            }
        }
    }

    public static MapLocation findClosestLocation(MapLocation me, List<MapLocation> otherLocs) {
        MapLocation closest = null;
        int minDist = Integer.MAX_VALUE;
        for (MapLocation loc : otherLocs) {
            int dist = me.distanceSquaredTo(loc);
            if (dist < minDist) {
                minDist = dist;
                closest = loc;
            }
        }
        return closest;
    }

    public static int findClosestDistance(MapLocation me, List<MapLocation> otherLocs) {
        return findClosestLocation(me, otherLocs).distanceSquaredTo(me);
    }
}
