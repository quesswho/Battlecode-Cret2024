package defensive;

import battlecode.common.*;

import java.util.ArrayList;

public class SetupPhase {


    static MapLocation damLocation = null;
    public static void runSetup(RobotController rc) throws GameActionException {
        Communication.updateRobot(rc);

        FlagInfo[] allFlags = rc.senseNearbyFlags(-1, RobotPlayer.team);
        for (FlagInfo flag : allFlags) {
            Communication.updateFlagInfo(rc, flag);
        }

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
            if(rc.canDig(buildLoc) && !Pathfinder.evenSquare(buildLoc)) {
                rc.dig(buildLoc);
            }
            Pathfinder.explore(rc);
        } else {
            runExplore(rc);
        }
    }

    private static void runGuardian(RobotController rc) throws GameActionException {
        if(rc.getRoundNum() == 2) {
            FlagPlacement.computeFlagGoal(rc);
            return;
        }

        if(rc.getRoundNum() == 200) {
            Communication.clearSetup(rc);

            if(rc.senseNearbyFlags(0).length > 0) {
                int value = Communication.locationToInt(rc.getLocation());
                rc.writeSharedArray(Communication.ALLY_FLAG_IDX + RobotPlayer.guardianID*4+1, value);
            } else {
                System.out.println("Flag was not placed in correct location!");
            }
            return;
        }
        FlagInfo[] flags = rc.senseNearbyFlags(-1, rc.getTeam());
        if(!rc.hasFlag()) {
            for (FlagInfo flag : flags) {
                 if(rc.canPickupFlag(flag.getLocation())) {
                    rc.pickupFlag(flag.getLocation());
                    break;
                }
                Direction dir = Pathfinder.directionToward(rc, flag.getLocation());
                if(dir != null) {
                    rc.move(dir);
                    break;
                }
            }
        } else {
            if(FlagPlacement.MOVE_FLAG) {
                moveGuardian(rc);
            }
        }
    }



    private static void moveGuardian(RobotController rc) throws GameActionException {
        ArrayList<MapLocation> flagLocations = Communication.getAllyFlagLocations(rc);

        Direction dir = Pathfinder.directionToward(rc, RobotPlayer.flagGoal[RobotPlayer.guardianID]);
        if (dir == null) {
            RobotPlayer.indicator += "Cant find a place to walk to!";
            return;
        }

        Direction[] dirs = {dir, dir.rotateLeft(), dir.rotateRight(),
                dir.rotateLeft().rotateLeft(), dir.rotateRight().rotateRight(),
                dir.rotateLeft().rotateLeft().rotateLeft(), dir.rotateRight().rotateRight().rotateRight()};

        for(Direction moveDir : dirs) {
            boolean canMove = true;
            for(MapLocation flag : flagLocations) {
                if(flag.equals(rc.getLocation())) continue;
                if(!rc.canMove(moveDir) || rc.getLocation().add(moveDir).distanceSquaredTo(flag) <= 81 || Pathfinder.isTowards(rc.getLocation(), moveDir, flag)) {
                    canMove = false;
                    break;
                }
            }
            if(canMove) {
                rc.move(dir);
                break;
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

    private static void runExplore(RobotController rc) throws GameActionException {
        if(rc.getRoundNum() > 150 && damLocation != null) {
            Pathfinder.moveToward(rc, damLocation);
            return;
        }

        MapInfo[] mapinfo = rc.senseNearbyMapInfos(2);
        for(MapInfo info : mapinfo) {
            if(info.isWater()) {
                if(rc.canDig(info.getMapLocation())) rc.dig(info.getMapLocation());
            }
            if(info.isDam()) {
                damLocation = info.getMapLocation();
                break;
            }
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
