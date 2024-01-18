package cretplayer1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.ArrayList;
import java.util.List;

public class SetupPhase {
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
        List<MapLocation> fillable = new ArrayList<>();
        List<Direction> movableDir = new ArrayList();
        for(int i = 0; i < 8; i++) {
            MapLocation loc = robotLoc.add(directions[i]);
            if(rc.canMove(directions[i])) {
                movableDir.add(directions[i]);
            } else if(rc.canFill(loc)) {
                fillable.add(loc);
            }
        }
        MapLocation[] crumbs = rc.senseNearbyCrumbs(9);

        // Compute move

        if(crumbs.length > 0) { // Get crumb
            Direction dir = rc.getLocation().directionTo(crumbs[RobotPlayer.id % crumbs.length]);
            if(movableDir.contains(dir)) {
                rc.move(dir);
            } else if(!fillable.isEmpty()) {
                rc.fill(fillable.get(RobotPlayer.rng.nextInt(fillable.size())));
            }
        } else {
            if(!movableDir.isEmpty()) {
                rc.move(movableDir.get(RobotPlayer.rng.nextInt(movableDir.size())));
            }
        }
    }
}
