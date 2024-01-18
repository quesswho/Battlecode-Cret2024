package cretplayer1_1;

import battlecode.common.*;

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

    static MapLocation robotLoc;
    public static void run(RobotController rc) throws GameActionException {

        //////////////
        // Get info //
        //////////////

        robotLoc = RobotPlayer.robotLoc;
        List<MapLocation> fillable = new ArrayList<>();
        List<Direction> movableDir = new ArrayList<>();
        List<MapLocation> buildExplosive = new ArrayList<>();

        for(int i = 0; i < 8; i++) {
            MapLocation loc = robotLoc.add(directions[i]);
            if(rc.canMove(directions[i])) {
                movableDir.add(directions[i]);
            }
            if(rc.canFill(loc)) {
                fillable.add(loc);
            }
            if(rc.canBuild(TrapType.EXPLOSIVE, loc)) {
                buildExplosive.add(loc);
            }
        }
        MapLocation[] crumbs = rc.senseNearbyCrumbs(-1);

        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        MapLocation closestToHome = null;
        int dist = 100000000;
        for (MapLocation loc : spawnLocs) {
            if (dist > robotLoc.distanceSquaredTo(loc)) {
                closestToHome = loc;
                dist = robotLoc.distanceSquaredTo(loc);
            }
        }
        assert closestToHome != null; // Should not happen

        int totalCrumbs = rc.getCrumbs();

        ////////////////////////////////
        // Compute move probabilities //
        ////////////////////////////////

        List<Task> moveList = new ArrayList<>();
        List<Task> actionList = new ArrayList<>();
        moveList.add(new Task(1, Type.NONE, robotLoc));
        for (Direction dir : movableDir) {
            Task task = new Task(Type.MOVE, robotLoc.add(dir));
            task.value = 1;

            if(robotLoc.distanceSquaredTo(closestToHome) < task.location.distanceSquaredTo(closestToHome)) task.value += 10;

            for(MapLocation crumb : crumbs) {
                if(dir.equals(robotLoc.directionTo(crumb))) task.value += 40;
            }

            moveList.add(task);
        }

        for(MapLocation fill : fillable) {
            Task task = new Task(Type.FILL, fill);
            task.value += 1;
            for(MapLocation crumb : crumbs) {
                if(robotLoc.directionTo(crumb).equals(fill.directionTo(crumb))) task.value += 10;
            }
            moveList.add(task);
        }


        //////////////////////////////////
        // Compute action probabilities //
        //////////////////////////////////

        actionList.add(new Task(1, Type.NONE, robotLoc));
        for(MapLocation buildBoom : buildExplosive) {
            Task build = new Task(Type.BUILD_BOMB, buildBoom);
            if (buildBoom.distanceSquaredTo(closestToHome) <= 4) { // Protect spawn
                build.value += 0.1 * Math.min(250, (1000 - totalCrumbs)) * 0.001;
            }

            actionList.add(build);
        }

        /*for(MapLocation crumb : crumbs) {
            Direction dir = robotLoc.directionTo(crumb);

        }*/

        ////////////////////////////
        // Choose move and action //
        ////////////////////////////

        RobotPlayer.MoveAction(rc, moveList, actionList);
    }
}
