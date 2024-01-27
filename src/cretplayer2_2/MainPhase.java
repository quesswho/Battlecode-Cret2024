package cretplayer2_2;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

public class MainPhase {

    public static void runMainPhase(RobotController rc) throws GameActionException {
        Communication.updateRobot(rc);

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyTeammates = rc.senseNearbyRobots(-1, rc.getTeam());

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
                Builder.runBuilder(rc, nearbyEnemies, nearbyTeammates);
                break;
            case GUARDIAN:
                Guardian.runGuardian(rc, nearbyEnemies, nearbyTeammates);
                break;
        }
    }
}
