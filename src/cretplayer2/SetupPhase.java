package cretplayer2;

import battlecode.common.*;

public class SetupPhase {

    private static final int EXPLORE_ROUNDS = 150;
    
    public static void runSetup(RobotController rc) throws GameActionException {

        if(rc.getRoundNum() < EXPLORE_ROUNDS) {
            Pathfinder.explore(rc);
            return;
        }
        // Find nearby flags and place bombs nearby
        FlagInfo[] flags = rc.senseNearbyFlags(-1);

        FlagInfo targetFlag = null;
        for(FlagInfo flag : flags) {
            if(!flag.isPickedUp()) {
                targetFlag = flag;
                break;
            }
        }

        if(targetFlag != null) {
            Pathfinder.moveTowards(rc, targetFlag.getLocation(), false);
            if(rc.getLocation().distanceSquaredTo(flags[0].getLocation()) < 9) {
                if(rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
                    rc.build(TrapType.EXPLOSIVE, rc.getLocation());
                } else {
                    MapLocation waterLoc = rc.getLocation().add(RobotPlayer.directions[RobotPlayer.random.nextInt(8)]);
                    if(rc.canDig(waterLoc)) rc.dig(waterLoc);
                }
            }
        } else {
            Pathfinder.explore(rc);
        }
    }
}
