package cretplayer1_1;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.sqrt;

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

    static MapLocation robotLoc;
    static List<Direction> movableDir = new ArrayList<>();
    static List<MapLocation> attackLocs = new ArrayList<>();
    static int[][] walls;
    static int numWalls = 0;
    static RobotController rcr;

    public static void run(RobotController rc) throws GameActionException, Exception {

        //////////////
        // Get info //
        //////////////
        rcr = rc;
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();

        robotLoc = RobotPlayer.robotLoc;

        movableDir.clear();
        attackLocs.clear();
        walls = new int[9][9];

        MapInfo[] mapinfo = rc.senseNearbyMapInfos();

        boolean insideEnemyTerritory = false;
        for(MapInfo info : mapinfo) {
            MapLocation loc = info.getMapLocation();
            if(info.isWall()) {
                walls[loc.x-robotLoc.x+4][loc.y-robotLoc.y+4] = 1;
                numWalls++;
            }
            if(info.getTeamTerritory() == RobotPlayer.opponentTeam) {
                insideEnemyTerritory = true;
            }
        }

        List<MapLocation> fillable = new ArrayList<>();
        List<MapLocation> buildExplosive = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            MapLocation loc = robotLoc.add(directions[i]);
            if (rc.canAttack(loc)) {
                attackLocs.add(loc);
            }
            if (rc.canMove(directions[i])) {
                movableDir.add(directions[i]);
            } else {
                walls[loc.x-robotLoc.x+4][loc.y-robotLoc.y+4] = 1;
            }

            if(rc.canBuild(TrapType.EXPLOSIVE, loc)) {
                buildExplosive.add(loc);
            }
            if(rc.canFill(loc)) {
                fillable.add(loc);
            }
        }

        for(int i = 0; i < 9; i++) {
            for(int j = 0; j < 9; j++) {
                if(walls[i][j]==1) {
                    rc.setIndicatorDot(new MapLocation(i+robotLoc.x-4, j+robotLoc.y-4), 0, 255, 0);
                }
            }
        }




        RobotInfo[] robots = rc.senseNearbyRobots();
        List<RobotInfo> enemies = new ArrayList<>();
        List<RobotInfo> friends = new ArrayList<>();
        int tempallyx = robotLoc.x;
        int tempallyy = robotLoc.y;
        int tempenemyx = robotLoc.x;
        int tempenemyy = robotLoc.y;
        for (RobotInfo robot : robots) {
            if (robot.team == RobotPlayer.team) {
                friends.add(robot);
                tempallyx += robot.getLocation().x;
                tempallyy += robot.getLocation().y;
            } else {
                enemies.add(robot);
                tempenemyx += robot.getLocation().x;
                tempenemyy += robot.getLocation().y;
            }
        }
        MapLocation average_ally = new MapLocation(tempallyx / (friends.size() + 1), tempallyy / (friends.size() + 1));
        MapLocation average_enemy = new MapLocation(tempenemyx / (enemies.size() + 1), tempenemyy / (enemies.size() + 1));

        List<RobotInfo> healable = new ArrayList<>();
        for (RobotInfo friend : friends) {
            if (rc.canHeal(friend.location)) {
                healable.add(friend);
            }
        }

        FlagInfo[] flags = rc.senseNearbyFlags(-1, RobotPlayer.opponentTeam);
        MapLocation flagDirection = null;
        if (flags.length > 0) {
            Direction dir = directionTo(flags[0].getLocation());

            if (dir != null) {
                flagDirection = robotLoc.add(dir);

            }

        }
        boolean hasFlag = rc.hasFlag();

        MapLocation closestToHome = null;
        int dist = 100000000;
        for (MapLocation loc : spawnLocs) {
            if (dist > robotLoc.distanceSquaredTo(loc)) {
                closestToHome = loc;
                dist = robotLoc.distanceSquaredTo(loc);
            }
        }
        assert closestToHome != null; // Should not happen

        // TODO: Compute levels for each speciality

        int crumbs = rc.getCrumbs();

        ////////////////////////////////
        // Compute move probabilities //
        ////////////////////////////////

        List<Task> moveList = new ArrayList<>();
        List<Task> actionList = new ArrayList<>();
        for (Direction dir : movableDir) {
            Task m = new Task(Type.MOVE, robotLoc.add(dir));
            m.value = 1;
            if (hasFlag) {
                if (robotLoc.directionTo(m.location).equals(directionTo(closestToHome))) m.value += 100;
            } else {
                if(flagDirection != null) {
                    if (flagDirection.equals(m.location) && !flags[0].isPickedUp()) m.value += 200;
                }
                if(!insideEnemyTerritory && robotLoc.distanceSquaredTo(closestToHome) < m.location.distanceSquaredTo(closestToHome)) m.value += 10;
                if(!insideEnemyTerritory && robotLoc.distanceSquaredTo(RobotPlayer.middle) < m.location.distanceSquaredTo(RobotPlayer.middle)) m.value += 25;
                m.value += (20 - average_ally.distanceSquaredTo(m.location)) * 0.25;
            }

            moveList.add(m);
        }

        for(MapLocation fill : fillable) { // Fill where every we would like to go
            Task task = new Task(1, Type.FILL, fill);
            if (hasFlag) {
                if (robotLoc.directionTo(task.location).equals(directionTo(closestToHome))) task.value += 100;
            } else {
                if(flagDirection != null) {
                    if(robotLoc.distanceSquaredTo(flagDirection)<task.location.distanceSquaredTo(flagDirection)) task.value += 20;
                }
                //if(robotLoc.distanceSquaredTo(closestToHome) < task.location.distanceSquaredTo(closestToHome)) task.value += 10;

                task.value += (average_ally.distanceSquaredTo(task.location)) * 10;
            }
            moveList.add(task);
        }


        //////////////////////////////////
        // Compute action probabilities //
        //////////////////////////////////

        Task noaction = new Task(1, Type.NONE, robotLoc);
        if(flagDirection != null) {
            if(flagDirection.distanceSquaredTo(robotLoc)<=2) noaction.value += 100;
        }
        actionList.add(noaction);


        if (rc.canPickupFlag(robotLoc)) {
            rc.setIndicatorString("Picking up flag!");
            actionList.add(new Task(100000, Type.PICKUP_FLAG, robotLoc));
        }

        for (RobotInfo heal : healable) {
            Task h = new Task(Type.HEAL, heal.location);
            h.value += (1000 - heal.health)*5+1000;
            if (heal.hasFlag()) h.value *= 50; // Very important to heal ducks with flag
            // TODO: Should be more likely to heal robots with higher level
            actionList.add(h);

        }

        for (RobotInfo enemy : enemies) {
            if (robotLoc.distanceSquaredTo(enemy.getLocation()) <= 2 && attackLocs.contains(enemy.getLocation())) { // If enemy is within range
                Task attack = new Task(Type.ATTACK, enemy.getLocation());
                // TODO: Consider enemy territory see: Actions -> Attacking: https://releases.battlecode.org/specs/battlecode24/1.2.5/specs.md.html
                if (enemy.health - 150 <= 0) { // Enemy dies TODO: Consider attacking level for damage given
                    attack.value += 100;
                }
                attack.value += (1000 - enemy.health) * 0.1+10;

                actionList.add(attack);
            }
        }

        for(MapLocation buildBoom : buildExplosive) {
            Task build = new Task(Type.BUILD_BOMB, buildBoom);
            if(buildBoom.distanceSquaredTo(closestToHome) <= 4) { // Protect spawn
                build.value += 0.1 * Math.min(250,(1000 - crumbs))*0.001;
            }
            if(hasFlag) {
                if(robotLoc.distanceSquaredTo(closestToHome) >= buildBoom.distanceSquaredTo(closestToHome)) {
                    build.value += 500; // More likely to place behind
                } else {
                    build.value += 50;
                }
            }
            build.value += 0.001 * Math.min(250,(1000 - crumbs))*0.001;
            actionList.add(build);
        }



        ////////////////////////////
        // Choose move and action //
        ////////////////////////////
        RobotPlayer.MoveAction(rc, moveList, actionList);
    }
    /*
        Calculate what direction we can move to towards a location
    */
    public static Direction directionTo(MapLocation loc) throws Exception {
        if(robotLoc.equals(loc)) return null;
        return PathFinder.getDirection(walls, robotLoc, loc);
    }
}