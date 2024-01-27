package cretplayer2_2;

import battlecode.common.*;

public class Guardian {
    public static void runGuardian(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyTeammates) throws GameActionException {
        // attack enemies, prioritizing enemies that have your flag
        for (RobotInfo robot : nearbyEnemies) {
            if (robot.hasFlag()) {
                Pathfinder.moveToward(rc, robot.getLocation());
                if (rc.canAttack(robot.getLocation()))
                    rc.attack(robot.getLocation());
            }
        }
        for (RobotInfo robot : nearbyEnemies) {
            if (rc.canAttack(robot.getLocation())) {
                rc.attack(robot.getLocation());
            }
        }

        // Always move towards flag location
        MapLocation homeLoc = RobotPlayer.flagSpawnLocation[RobotPlayer.guardianID];
        rc.setIndicatorDot(homeLoc, 0, 0, 255);

        // If we are not at the location then return
        if (!rc.getLocation().equals(homeLoc)) {
            Pathfinder.moveToward(rc, homeLoc);
        } else {
            if (rc.canBuild(TrapType.STUN, rc.getLocation())){
                rc.build(TrapType.STUN, rc.getLocation());
            }
            if (rc.canBuild(TrapType.WATER, rc.getLocation().add(rc.getLocation().directionTo(RobotPlayer.middle)))){
                rc.build(TrapType.WATER, rc.getLocation().add(rc.getLocation().directionTo(RobotPlayer.middle)));
            }
        }

        // Warn teammates if there are enemies nearby
        /*MapLocation curWarning = Communication.getLocation(rc, Communication.FLAG_WARNING_IDX);
        if (nearbyEnemies.length > 0) {
            if (curWarning == null)
                Communication.updateWarningInfo(rc, homeLoc, Communication.FLAG_WARNING_IDX);
        } else if (curWarning != null && curWarning.equals(homeLoc)) {
            Communication.updateWarningInfo(rc, null, Communication.FLAG_WARNING_IDX);
        }*/
    }
}
