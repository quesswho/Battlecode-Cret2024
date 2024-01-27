package cretplayer2_2;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

public class MainPhase {

    public static void runMainPhase(RobotController rc) throws GameActionException {
        Communication.updateRobot(rc);

        FlagInfo[] allFlags = rc.senseNearbyFlags(-1);
        for (FlagInfo flag : allFlags) {
            Communication.updateFlagInfo(rc, flag);
        }

        ArrayList<MapLocation> flagLocations = Communication.getEnemyFlagLocations(rc);

        MapLocation closestFlag = Pathfinder.findClosestLocation(rc.getLocation(), flagLocations);
        if (closestFlag != null) {
            if (rc.canPickupFlag(closestFlag))
                rc.pickupFlag(closestFlag);
        }

        // Prioritize actions based on role
        switch(RobotPlayer.role) {
            case MINION:
                Minion.runMinion(rc);
                break;
            case BUILDER:
                Builder.runBuilder(rc);
                break;
            case GUARDIAN:
                Guardian.runGuardian(rc);
                break;
        }
    }
}
