package defensive;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.ArrayList;

public class FlagPlacement {

    private static final int NEAREST_CORNER_WALL_PRECISION = 120;
    public static boolean MOVE_FLAG = true;
    private static MapLocation corner;
    static MapLocation centerSpawn;

    public static ArrayList<MapLocation> centers = new ArrayList<>();

    /*
        Find spawn centers and the middle spawn location
     */
    public static void computeLayout(RobotController rc) throws GameActionException {
        int tempx=0, tempy=0;
        // Center iff adjacent to 8 other spawn locations
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        for (int i = 0; i < spawnLocs.length; i++) {
            int adjCount = 0;
            for (int j = 0; j < spawnLocs.length; j++) {
                if (spawnLocs[j].isAdjacentTo(spawnLocs[i])) {
                    adjCount++;
                }
            }
            if (adjCount == 9) {
                centers.add(spawnLocs[i]);
                tempx += spawnLocs[i].x;
                tempy += spawnLocs[i].y;
            }
        }

        // Find the middle spawn
        int minDist = Integer.MAX_VALUE;
        for(MapLocation c1 : centers) {
            int tempSum = 0;
            for(MapLocation c2 : centers) {
                if(c1==c2) continue;
                tempSum += c1.distanceSquaredTo(c2);
            }
            if(tempSum < minDist) {
                minDist = tempSum;
                centerSpawn = c1;
            }
        }
    }

    public static void computeFlagGoal(RobotController rc) throws GameActionException {

        corner = closestCorner(centerSpawn);

        ArrayList<Direction> wallsCorners = new ArrayList<>();
        for(int i = 0; i < 3; i++) {
            Direction wall = closestWallOrCorner(centers.get(i));
            wallsCorners.add(wall);
        }

        // Count number of maximal rotations to reach each spawn
        int count = 0;
        for(Direction dir1 : wallsCorners) {
            int max = 0;
            for(Direction dir2 : wallsCorners) {
                int temp = Math.min(Math.abs(dir1.ordinal() - dir2.ordinal()), Math.abs(8 - dir1.ordinal() + dir2.ordinal()));
                if(temp > max) {
                    max = temp;
                }
            }
            count += max;
        }

        System.out.println("Maximal rotations: " + count);
        // If map is too fucked up we dont bother moving flag
        if(count > 12) {
            MOVE_FLAG = false;
            return;
        }



        int distanceBetweenFlags = 8;
        Direction wallCornerDir = closestWallOrCorner(centerSpawn);
        MapLocation wallCorner = closestWallOrCornerLoc(centerSpawn);
        ArrayList<MapLocation> flagLocations = new ArrayList<>();
        flagLocations.add(corner);
        switch(wallCornerDir) {
            case WEST:
            case EAST:
                flagLocations.add(new MapLocation(wallCorner.x, wallCorner.y+distanceBetweenFlags));
                flagLocations.add(new MapLocation(wallCorner.x, wallCorner.y-distanceBetweenFlags));
                setFlagGoals(rc, flagLocations);
                break;
            case NORTH:
            case SOUTH:
                flagLocations.add(new MapLocation(wallCorner.x+distanceBetweenFlags, wallCorner.y));
                flagLocations.add(new MapLocation(wallCorner.x-distanceBetweenFlags, wallCorner.y));
                setFlagGoals(rc, flagLocations);
                break;
            case SOUTHWEST:
                flagLocations.add(new MapLocation(wallCorner.x+distanceBetweenFlags, wallCorner.y));
                flagLocations.add(new MapLocation(wallCorner.x, wallCorner.y+distanceBetweenFlags));
                setFlagGoals(rc, flagLocations);
                break;
            case NORTHWEST:
                flagLocations.add(new MapLocation(wallCorner.x+distanceBetweenFlags, wallCorner.y));
                flagLocations.add(new MapLocation(wallCorner.x, wallCorner.y-distanceBetweenFlags));
                setFlagGoals(rc, flagLocations);
                break;
            case SOUTHEAST:
                flagLocations.add(new MapLocation(wallCorner.x-distanceBetweenFlags, wallCorner.y));
                flagLocations.add(new MapLocation(wallCorner.x, wallCorner.y+distanceBetweenFlags));
                setFlagGoals(rc, flagLocations);
                break;
            case NORTHEAST:
                flagLocations.add(new MapLocation(wallCorner.x-distanceBetweenFlags, wallCorner.y));
                flagLocations.add(new MapLocation(wallCorner.x, wallCorner.y-distanceBetweenFlags));
                setFlagGoals(rc, flagLocations);
                break;
        }
    }

    private static Direction closestWall(MapLocation loc) {
        int minDist = Integer.MAX_VALUE;
        Direction result = null;
        if(loc.x < minDist) {
            result = Direction.WEST;
            minDist = loc.x;
        }
        if(RobotPlayer.mapWidth - loc.x - 1 < minDist) {
            result = Direction.EAST;
            minDist = RobotPlayer.mapWidth - loc.x - 1;
        }
        if(loc.y < minDist) {
            result = Direction.NORTH;
            minDist = loc.y;
        }
        if(RobotPlayer.mapHeight - loc.y - 1 < minDist) {
            result = Direction.SOUTH;
        }
        return result;
    }

    private static MapLocation closestCorner(MapLocation loc) {
        MapLocation corner = null;
        int dist = loc.distanceSquaredTo(new MapLocation(0,0));
        int minDist = Integer.MAX_VALUE;
        if(dist < minDist) {
            minDist = dist;
            corner = new MapLocation(0,0);
        }
        dist = loc.distanceSquaredTo(new MapLocation(0, RobotPlayer.mapHeight-1));
        if(dist < minDist) {
            minDist = dist;
            corner = new MapLocation(0, RobotPlayer.mapHeight-1);
        }
        dist = loc.distanceSquaredTo(new MapLocation(RobotPlayer.mapWidth-1,0));
        if(dist < minDist) {
            minDist = dist;
            corner = new MapLocation(RobotPlayer.mapWidth-1,0);
        }
        dist = loc.distanceSquaredTo(new MapLocation(RobotPlayer.mapWidth-1, RobotPlayer.mapHeight-1));
        if(dist < minDist) {
            corner = new MapLocation(RobotPlayer.mapWidth-1, RobotPlayer.mapHeight-1);
        }
        return corner;
    }

    private static Direction closestCornerDirection(MapLocation loc) {
        Direction corner = null;
        int dist = loc.distanceSquaredTo(new MapLocation(0,0));
        int minDist = Integer.MAX_VALUE;
        if(dist < minDist) {
            minDist = dist;
            corner = Direction.SOUTHWEST;
        }
        dist = loc.distanceSquaredTo(new MapLocation(0, RobotPlayer.mapHeight-1));
        if(dist < minDist) {
            minDist = dist;
            corner = Direction.NORTHWEST;
        }
        dist = loc.distanceSquaredTo(new MapLocation(RobotPlayer.mapWidth-1,0));
        if(dist < minDist) {
            minDist = dist;
            corner = Direction.SOUTHEAST;
        }
        dist = loc.distanceSquaredTo(new MapLocation(RobotPlayer.mapWidth-1, RobotPlayer.mapHeight-1));
        if(dist < minDist) {
            corner = Direction.NORTHEAST;
        }
        return corner;
    }

    private static Direction closestWallOrCorner(MapLocation loc) {

        Direction dir = null;
        Direction result = null;
        int minDist = Integer.MAX_VALUE;

        int southWest = loc.distanceSquaredTo(new MapLocation(0,0));
        if(southWest < minDist) {
            minDist = southWest;
            dir = Direction.SOUTHWEST;
            result = Direction.SOUTHWEST;
        }

        int northWest = loc.distanceSquaredTo(new MapLocation(0, RobotPlayer.mapHeight-1));
        if(northWest < minDist) {
            minDist = northWest;
            dir = Direction.NORTHWEST;
            result = Direction.NORTHWEST;
        }

        int southEast = loc.distanceSquaredTo(new MapLocation(RobotPlayer.mapWidth-1,0));
        if(southEast < minDist) {
            minDist = southEast;
            dir = Direction.SOUTHEAST;
            result = Direction.SOUTHEAST;
        }

        int northEast = loc.distanceSquaredTo(new MapLocation(RobotPlayer.mapWidth-1, RobotPlayer.mapHeight-1));
        if(northEast < minDist) {
            dir = Direction.NORTHEAST;
            result = Direction.NORTHEAST;
        }

        if((dir == Direction.SOUTHWEST || dir == Direction.NORTHWEST) && Math.abs(southWest - northWest) < NEAREST_CORNER_WALL_PRECISION) {
            result = Direction.WEST;
        }

        if((dir == Direction.SOUTHEAST || dir == Direction.NORTHEAST) && Math.abs(southEast - northEast) < NEAREST_CORNER_WALL_PRECISION) {
            result = Direction.EAST;
        }
        if((dir == Direction.SOUTHEAST || dir == Direction.SOUTHWEST) && Math.abs(southEast - southWest) < NEAREST_CORNER_WALL_PRECISION) {

            result = Direction.SOUTH;
        }
        if((dir == Direction.NORTHEAST || dir == Direction.NORTHWEST) && Math.abs(northEast - northWest) < NEAREST_CORNER_WALL_PRECISION) {
            result = Direction.NORTH;
        }

        return result;
    }

    private static MapLocation closestWallOrCornerLoc(MapLocation loc) {

        Direction dir = null;
        Direction result = null;
        MapLocation location = null;
        int minDist = Integer.MAX_VALUE;

        int southWest = loc.distanceSquaredTo(new MapLocation(0,0));
        if(southWest < minDist) {
            minDist = southWest;
            dir = Direction.SOUTHWEST;
            location = new MapLocation(0,0);
        }

        int northWest = loc.distanceSquaredTo(new MapLocation(0, RobotPlayer.mapHeight-1));
        if(northWest < minDist) {
            minDist = northWest;
            dir = Direction.NORTHWEST;
            location = new MapLocation(0, RobotPlayer.mapHeight-1);
        }

        int southEast = loc.distanceSquaredTo(new MapLocation(RobotPlayer.mapWidth-1,0));
        if(southEast < minDist) {
            minDist = southEast;
            dir = Direction.SOUTHEAST;
            location = new MapLocation(RobotPlayer.mapWidth-1,0);
        }

        int northEast = loc.distanceSquaredTo(new MapLocation(RobotPlayer.mapWidth-1, RobotPlayer.mapHeight-1));
        if(northEast < minDist) {
            dir = Direction.NORTHEAST;
            location = new MapLocation(RobotPlayer.mapWidth-1, RobotPlayer.mapHeight-1);
        }

        if((dir == Direction.SOUTHWEST || dir == Direction.NORTHWEST) && Math.abs(southWest - northWest) < NEAREST_CORNER_WALL_PRECISION) {
            location = new MapLocation(0,(RobotPlayer.mapHeight-1)/2);
        }

        if((dir == Direction.SOUTHEAST || dir == Direction.NORTHEAST) && Math.abs(southEast - northEast) < NEAREST_CORNER_WALL_PRECISION) {
            location = new MapLocation(RobotPlayer.mapWidth-1,(RobotPlayer.mapHeight-1)/2);
        }

        if((dir == Direction.SOUTHEAST || dir == Direction.SOUTHWEST) && Math.abs(southEast - southWest) < NEAREST_CORNER_WALL_PRECISION) {
            location = new MapLocation((RobotPlayer.mapWidth-1)/2,0);
        }
        if((dir == Direction.NORTHEAST || dir == Direction.NORTHWEST) && Math.abs(northEast - northWest) < NEAREST_CORNER_WALL_PRECISION) {
            location = new MapLocation((RobotPlayer.mapWidth-1)/2,0);
        }

        return location;
    }

    private static void setFlagGoals(RobotController rc, ArrayList<MapLocation> locs) throws GameActionException {
        ArrayList<MapLocation> flagLocations = Communication.getAllyFlagLocations(rc); // Current guardian locations
        if(flagLocations.size() != 3 && locs.size() != 3) {
            System.out.println("Could not find all ally flags!");
            return;
        }
        for(int i = 0; i < 3; i++) {
            MapLocation loc = null;
            int minDist = Integer.MAX_VALUE;
            int k = 0;
            for(int j = 0; j < locs.size(); j++) {
                if(locs.get(j) != null) {
                    int dist = locs.get(j).distanceSquaredTo(flagLocations.get(i));
                    if(minDist > dist) {
                        minDist = dist;
                        loc = locs.get(j);
                        k = j;
                    }
                }
            }
            locs.remove(k);
            RobotPlayer.flagGoal[i] = loc;
        }
    }
}
