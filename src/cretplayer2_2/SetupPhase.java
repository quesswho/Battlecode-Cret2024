package cretplayer2_2;

import battlecode.common.*;

import java.util.HashSet;

public class SetupPhase {


    static final boolean MOVE_FLAG = false;
    public static void runSetup(RobotController rc) throws GameActionException {
        switch(RobotPlayer.role) {
            case MINION:
                runExplore(rc);
                break;
            case BUILDER:
                runBuilder(rc);
                break;
            case GUARDIAN:
                runGuardian(rc);
                break;
        }
    }

    private static void runBuilder(RobotController rc) throws GameActionException {
        // Level up builder speciality
        if(rc.getLevel(SkillType.BUILD) != 6) {
            MapLocation buildLoc = rc.getLocation().add(RobotPlayer.directions[RobotPlayer.random.nextInt(8)]);
            if(rc.canDig(buildLoc)) {
                rc.dig(buildLoc);
            }
           Pathfinder.explore(rc);
        } else {
            runExplore(rc);
        }
    }

    private static void runGuardian(RobotController rc) throws GameActionException {
        if(rc.getRoundNum() == 199) {
            Communication.clearSetup(rc);
            RobotPlayer.flagSpawnLocation[RobotPlayer.guardianID] = rc.getLocation();
            return;
        }
        FlagInfo[] flags = rc.senseNearbyFlags(-1, rc.getTeam());
        if(!rc.hasFlag()) {
            for (FlagInfo flag : flags) {
                 if(rc.canPickupFlag(flag.getLocation())) {
                    rc.pickupFlag(flag.getLocation());
                    break;
                }
                Direction dir = Pathfinder.bugNavTwo(rc, flag.getLocation(), true);
                if(dir != null) {
                    rc.move(dir);
                    break;
                }
            }
        } else {
            if(MOVE_FLAG) {
                Direction dir = bugNavTwo(rc, RobotPlayer.flagGoal[RobotPlayer.guardianID], flags);
                if (dir == null) {
                    rc.setIndicatorString("Cant find a place to walk to!" + bugState);
                    return;
                }

                rc.move(dir);
            }
        }
    }

    private static boolean nearFlag(RobotController rc, Direction dir, FlagInfo[] flags) {
        for (FlagInfo flag : flags) {
            int dist = rc.getLocation().add(dir).distanceSquaredTo(flag.getLocation());
            if(flag.getLocation().equals(rc.getLocation())) continue;
            if(dist < 49) return true;
        }
        return false;
    }

    private static MapLocation prevDest = null;
    private static HashSet<MapLocation> line = null;
    private static int obstacleStartDist = 0;
    private static int bugState = 0; // 0 head to target, 1 circle obstacle
    private static Direction bugDir = null;

    private static Direction bugNavTwo(RobotController rc, MapLocation destination,FlagInfo[] flags) throws GameActionException{
        if(rc.getLocation().equals(destination)) return null;
        if(!destination.equals(prevDest)) {
            prevDest = destination;
            line = createLine(rc.getLocation(), destination);
        }

        for(MapLocation loc : line) {
            rc.setIndicatorDot(loc, 255, 0, 0);
        }

        if(bugState == 0) {
            bugDir = rc.getLocation().directionTo(destination);
            if(rc.canFill(rc.getLocation().add(bugDir))) {
                rc.fill(rc.getLocation().add(bugDir));
            } else if(rc.canMove(bugDir) && !nearFlag(rc, bugDir, flags)){
                return bugDir;
            } else {
                bugState = 1;
                obstacleStartDist = rc.getLocation().distanceSquaredTo(destination);
                bugDir = rc.getLocation().directionTo(destination);
            }
        }

        if(bugState == 1) {
            if(line.contains(rc.getLocation()) && rc.getLocation().distanceSquaredTo(destination) < obstacleStartDist) {
                bugState = 0;
            }

            for(int i = 0; i < 9; i++){
                if(rc.canFill(rc.getLocation().add(bugDir))) {
                    rc.fill(rc.getLocation().add(bugDir));
                    return null; // Not really what we want, we should think of a different implementation
                } else if(rc.canMove(bugDir) && !nearFlag(rc, bugDir, flags)){
                    Direction result = bugDir;
                    bugDir = bugDir.rotateRight();
                    bugDir = bugDir.rotateRight();
                    return result;
                } else {
                    bugDir = bugDir.rotateLeft();
                }
            }
        }
        return null;
    }

    private static HashSet<MapLocation> createLine(MapLocation a, MapLocation b) {
        HashSet<MapLocation> locs = new HashSet<>();
        int x = a.x, y = a.y;
        int dx = b.x - a.x;
        int dy = b.y - a.y;
        int sx = (int) Math.signum(dx);
        int sy = (int) Math.signum(dy);
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        int d = Math.max(dx,dy);
        int r = d/2;
        if (dx > dy) {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                x += sx;
                r += dy;
                if (r >= dx) {
                    locs.add(new MapLocation(x, y));
                    y += sy;
                    r -= dx;
                }
            }
        }
        else {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                y += sy;
                r += dx;
                if (r >= dy) {
                    locs.add(new MapLocation(x, y));
                    x += sx;
                    r -= dy;
                }
            }
        }
        locs.add(new MapLocation(x, y));
        return locs;
    }

    private static void runExplore(RobotController rc) throws GameActionException {
        MapInfo[] mapinfo = rc.senseNearbyMapInfos(2);
        for(MapInfo info : mapinfo) {
            if(info.isDam()) return;
        }
        RobotInfo[] nearbyTeammates = rc.senseNearbyRobots(-1, rc.getTeam());
        Direction dir = Pathfinder.exploreDirection(rc);
        if(dir == null) return;
        for(RobotInfo robot : nearbyTeammates) {
            if(robot.hasFlag() && rc.getLocation().add(dir).distanceSquaredTo(robot.getLocation()) < 4 && Pathfinder.isTowards(rc.getLocation(), dir, robot.getLocation())) {
                if(rc.canMove(dir.opposite())) {
                    dir = dir.opposite();
                }
            }
        }
        rc.move(dir);
    }
}
