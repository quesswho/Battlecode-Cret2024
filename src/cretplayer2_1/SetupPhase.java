package cretplayer2_1;

import battlecode.common.*;

import java.awt.*;
import java.nio.file.Path;
import java.util.Map;

public class SetupPhase {
    
    public static void runSetup(RobotController rc) throws GameActionException {
        switch(RobotPlayer.role) {
            case MINION:
                setupExplore(rc);
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
            setupExplore(rc);
        }
    }

    private static void runGuardian(RobotController rc) throws GameActionException {
        if(rc.getRoundNum() == 199) {
            RobotPlayer.flagGoal[RobotPlayer.guardianID] = rc.getLocation();
            return;
        }
        if(!rc.hasFlag()) {
            FlagInfo[] flags = rc.senseNearbyFlags(-1, rc.getTeam());
            for (FlagInfo flag : flags) {

                Pathfinder.moveTowards(rc, flag.getLocation(), true);
                if(rc.canPickupFlag(flag.getLocation())) rc.pickupFlag(flag.getLocation());
            }
        } else {
            if(RobotPlayer.guardianID != 0) {
                if(RobotPlayer.flagGoal[0].distanceSquaredTo(rc.getLocation()) < 7) {
                    RobotPlayer.flagGoal[RobotPlayer.guardianID] = RobotPlayer.flagGoal[RobotPlayer.guardianID].add(RobotPlayer.flagGoal[0].directionTo(rc.getLocation()));
                }

                Pathfinder.bugNavOne(rc, RobotPlayer.flagGoal[RobotPlayer.guardianID], RobotPlayer.flagGoal[0], 7, true);
            } else {
                Pathfinder.bugNavOne(rc, RobotPlayer.flagGoal[RobotPlayer.guardianID], true);
            }
        }
    }

    private static void setupExplore(RobotController rc) throws GameActionException {
        MapInfo[] mapinfo = rc.senseNearbyMapInfos(2);
        for(MapInfo info : mapinfo) {
            if(info.isDam()) return;
        }
        Pathfinder.explore(rc);
    }
}
