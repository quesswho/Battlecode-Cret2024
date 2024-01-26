package cretplayer2_2;

import battlecode.common.*;

import java.util.HashSet;
import java.util.List;

public class Pathfinder {

    private static Direction dir;

    private static MapLocation prevDest = null;
    private static HashSet<MapLocation> line = null;
    private static int obstacleStartDist = 0;
    private static int bugState = 0; // 0 head to target, 1 circle obstacle
    private static Direction bugDir = null;

    public static Direction exploreDirection(RobotController rc) throws GameActionException {
        if(rc.isMovementReady()) {
            MapLocation[] crumbLocs = rc.senseNearbyCrumbs(-1);
            if(crumbLocs.length > 0) {
                Direction temp = bugNavTwo(rc, crumbLocs[0], true);
                if(temp != null) {
                    return temp;
                }
            }

            if(dir == null || !rc.canMove(dir)) {
                dir = RobotPlayer.directions[RobotPlayer.random.nextInt(8)];
            }

            if(rc.canMove(dir)) return dir;
            else if(rc.canFill(rc.getLocation().add(dir))) rc.fill(rc.getLocation().add(dir));
        }
        return null;
    }

    public static void explore(RobotController rc) throws GameActionException {
        Direction dir = exploreDirection(rc);
        if(dir != null) {
            rc.move(dir);
        }
    }


    public static Direction bugNavTwo(RobotController rc, MapLocation destination, boolean fill) throws GameActionException {
        if(!rc.isMovementReady()) return null;
        if(rc.getLocation().equals(destination)) return null;

        if(!destination.equals(prevDest)) {
            bugState = 0;
            prevDest = destination;
            line = createLine(rc.getLocation(), destination);
        }

        for(MapLocation loc : line) {
            rc.setIndicatorDot(loc, 255, 0, 0);
        }

        if(bugState == 0) {
            bugDir = rc.getLocation().directionTo(destination);
            if(fill & rc.canFill(rc.getLocation().add(bugDir))) {
                rc.fill(rc.getLocation().add(bugDir));
            } else if(rc.canMove(bugDir)){
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
                if(fill & rc.canFill(rc.getLocation().add(bugDir))) {
                    rc.fill(rc.getLocation().add(bugDir));
                    return null; // Not really what we want, we should think of a different implementation
                } else if(rc.canMove(bugDir)){
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

    public static boolean isTowards(MapLocation start, Direction dir, MapLocation end) {
        return start.add(dir).distanceSquaredTo(end) < start.distanceSquaredTo(end);
    }
}
