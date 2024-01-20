package cretplayer2_1;

import battlecode.common.*;

import java.util.Map;

public class SetupPhase {

    private static final int EXPLORE_ROUNDS = 150;
    
    public static void runSetup(RobotController rc) throws GameActionException {

        if(rc.getRoundNum() < EXPLORE_ROUNDS) {
            Pathfinder.explore(rc);
            switch(RobotPlayer.role) {
                case MINION:
                    Pathfinder.explore(rc);
                    break;
                case BUILDER:
                    runBuilder(rc);
                    break;
                case GUARDIAN:
                    runGuardian(rc);
                    break;
            }
        } else {
            if(RobotPlayer.role != Role.GUARDIAN) {
                MapLocation middle = new MapLocation(RobotPlayer.mapWidth/2, RobotPlayer.mapHeight/2);
                Pathfinder.moveTowards(rc, middle, true);
            } else {
                runGuardian(rc);
            }
        }
    }

    private static void runBuilder(RobotController rc) throws GameActionException {
        // Level up builder speciality
        MapLocation buildLoc = rc.getLocation().add(RobotPlayer.directions[RobotPlayer.random.nextInt(8)]);
        if(rc.canDig(buildLoc)) {
            rc.dig(buildLoc);
        }
        Pathfinder.explore(rc);
    }

    private static void runGuardian(RobotController rc) throws GameActionException {
        FlagInfo[] flags = rc.senseNearbyFlags(-1,rc.getTeam());
        for(FlagInfo flag : flags) {
            if(RobotPlayer.flagIDs[RobotPlayer.guardianID] == flag.getID()) {
                Pathfinder.moveTowards(rc, flag.getLocation(), true);
            }
        }
    }
}
