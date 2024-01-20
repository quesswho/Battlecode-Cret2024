package cretplayer2;

import java.util.HashSet;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Pathfinder {

    private static Direction dir;

    public static void moveTowards(RobotController rc, MapLocation loc, boolean fill) throws GameActionException {
        Direction dir = rc.getLocation().directionTo(loc);
        if(fill & rc.canFill(rc.getLocation().add(dir))) rc.fill(rc.getLocation().add(dir));
        bugNavZero(rc, loc);
    }

    public static void explore(RobotController rc) throws GameActionException {
        if(rc.isMovementReady()) {
            MapLocation[] crumbLocs = rc.senseNearbyCrumbs(-1);
            if(crumbLocs.length > 0) {
                moveTowards(rc, crumbLocs[0], false);
            }

            if(dir == null || !rc.canMove(dir)) {
                dir = RobotPlayer.directions[RobotPlayer.random.nextInt(8)];
            }
            if(rc.canMove(dir)) rc.move(dir);
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
    private static int bugState = 0; // 0 head to target, 1 circle obstacle
    private static MapLocation closestObstacle = null;
    private static int closestObstacleDist = 10000;
    private static Direction bugDir = null;

    public static void bugNavOne(RobotController rc, MapLocation destination, boolean fill) throws GameActionException{
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
            if(rc.getLocation().equals(closestObstacle)){
                bugState = 0;
            }

            if(rc.getLocation().distanceSquaredTo(destination) < closestObstacleDist){
                closestObstacleDist = rc.getLocation().distanceSquaredTo(destination);
                closestObstacle = rc.getLocation();
            }

            for(int i = 0; i < 8; i++) {
                if(fill & rc.canFill(rc.getLocation().add(bugDir))) {
                    rc.fill(rc.getLocation().add(bugDir));
                    bugDir = bugDir.rotateRight();
                    bugDir = bugDir.rotateRight();
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
}
