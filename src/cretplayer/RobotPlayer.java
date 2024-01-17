package cretplayer;

import battlecode.common.*;

import java.util.*;

public strictfp class RobotPlayer {

    public static Random rng = null;
    static int id = -1;
    static int roundNum = 0;

    static int attack = 0;
    static int build = 0;
    static int heal = 0;

    static Team team;
    static Team opponentTeam;
    public static void run(RobotController rc) throws GameActionException {
        while (true) {

            roundNum++;

            try {
                if(id == -1) { // Get all base information
                    id = rc.getID();
                    team = rc.getTeam();
                    opponentTeam = team == Team.A ? Team.B : Team.A;
                    rng = new Random(id);
                }
                if(trySpawn(rc)) {
                    roundNum = rc.getRoundNum();
                    continue;
                }
                if(roundNum <= GameConstants.SETUP_ROUNDS) SetupPhase.run(rc);
                else MainPhase.run(rc);

            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                Clock.yield();
            }
        }
    }

    private static boolean trySpawn(RobotController rc) throws GameActionException {
        if (!rc.isSpawned()){
            MapLocation[] spawnArr = rc.getAllySpawnLocations();
            List<MapLocation> spawnList = Arrays.asList(spawnArr);
            Collections.shuffle(spawnList);
            spawnList.toArray(spawnArr);
            for(int i = 0; i < spawnArr.length; i++) {
                if (rc.canSpawn(spawnArr[i])) {
                    rc.spawn(spawnArr[i]);
                    return true;
                }
            }
            System.out.println("Could not spawn because of blockage!");
            return true; // Could not spawn anywhere
        }
        return false;
    }
}
