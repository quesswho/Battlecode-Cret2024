package cretplayer1_1;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class PathFinder {
    public static class Point {
        public int x;
        public int y;
        public Point previous;

        public Point(int x, int y, Point previous) {
            this.x = x;
            this.y = y;
            this.previous = previous;
        }

        @Override
        public String toString() { return String.format("(%d, %d)", x, y); }

        @Override
        public boolean equals(Object o) {
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() { return Objects.hash(x, y); }

        public Point offset(int ox, int oy) { return new Point(x + ox, y + oy, this);  }
    }

    public static boolean isWalkable(int[][] map, Point point) {
        if (point.x < 0 || point.x > map.length - 1) return false;
        if (point.y < 0 || point.y > map[0].length - 1) return false;
        return map[point.x][point.y] == 0;
    }
    static int[][] distances;


    /*public static Direction getDirection(int[][] grid, MapLocation startloc, MapLocation endloc) throws Exception {
        Direction dir = startloc.directionTo(endloc);
        Point p = new Point(4,4, null);
        for(int i = 0; i < 8; i++){
            if(isWalkable(grid, p.offset(dir.dx, dir.dy))) {
                return dir;
            } else {
                dir = dir.rotateLeft();
            }
        }
        return null;

    }*/

    public static int bugState = 0;
    static Direction dir = null;
    private static MapLocation closestObstacle = null;
    private static int closestObstacleDist = 10000;
    static Point p = new Point(4,4, null);

    public static Direction bugZero(int[][] grid, MapLocation startloc, MapLocation endloc) throws Exception {
        Direction dir = startloc.directionTo(endloc);
        Point p = new Point(4,4, null);
        for(int i = 0; i < 8; i++){
            if(isWalkable(grid, p.offset(dir.dx, dir.dy))) {
                return dir;
            } else {
                dir = dir.rotateLeft();
            }
        }
        return null;

    }

    public static void resetBug(){
        bugState = 0; // 0 head to target, 1 circle obstacle
        closestObstacle = null;
        closestObstacleDist = 10000;
        dir = null;
    }
    public static Direction bugOne(int[][] grid, MapLocation startloc, MapLocation endloc) throws Exception {
        if(bugState == 0) {
            dir = startloc.directionTo(endloc);

            if(isWalkable(grid, p.offset(dir.dx, dir.dy))) {
                return dir;
            } else {
                bugState = 1;
                closestObstacle = null;
                closestObstacleDist = 10000;
            }
        } else {
            if(startloc.equals(closestObstacle)){
                bugState = 0;
            }

            if(startloc.distanceSquaredTo(endloc) < closestObstacleDist){
                closestObstacleDist = startloc.distanceSquaredTo(endloc);
                closestObstacle = startloc;
            }

            for(int i = 0; i < 9; i++) {
                if(isWalkable(grid, p.offset(dir.dx, dir.dy))) {
                    Direction temp = dir;
                    dir = dir.rotateRight();
                    dir = dir.rotateRight();
                    return temp;
                } else {
                    dir = dir.rotateLeft();
                }
            }
        }
        return null;
    }
}
