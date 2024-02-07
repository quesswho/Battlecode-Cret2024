package cretplayer2_3;

import battlecode.common.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

public class Minion {

    private static MicroDirection bestMicro;
    private static final int STATE_OFFENSIVE = 1;
    private static final int STATE_HOLDING = 2;
    private static final int STATE_DEFENSIVE = 3;
    private static int state;
    private static boolean shouldPlanStepAttack = false;
    private static int myTotalStrength, oppTotalStrength;
    static final int HEALING_CUTOFF = 151;
    public static final int ATTACK_DISTANCE = 4;
    public static final int HEAL_DISTANCE = 4;
    public static int DAMAGE = 150; // TODO: Compute additional damage from levels
    public static final int BASE_DAMAGE = 150;
    public static final int DAMAGE_UPGRADE = 60;
    public static final int BASE_HEAL = 150;
    public static final int HEAL_UPGRADE = 50;
    public static int VISION_DIS = 25;
    static RobotInfo[] nearbyEnemies;
    static RobotInfo[] nearbyTeammates;
    static int teamStrength;
    static int lastAttackRound = 0;
    static RobotInfo leader = null;
    static RobotInfo chaseTarget = null;
    static RobotInfo attackTarget = null;

    static RobotInfo healTarget = null;
    static RobotInfo cachedLeader = null;
    static MapLocation closestEnemy = null;
    static boolean instantkill = false;
    static boolean allyToHeal = false;
    static MapLocation cachedEnemyLocation = null;
    public static int flagLast = -1;
    static int cachedRound = 0;
    static int cachedLeaderRound = -1000;
    static int closeFriendsSize = 0;
    static final boolean FOLLOW_FLAG = true;

    static int actionCooldown = 0;

    private static void sense(RobotController rc) throws GameActionException {
        nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        nearbyTeammates = rc.senseNearbyRobots(-1, rc.getTeam());

        actionCooldown = rc.getActionCooldownTurns();

        leader = null;
        chaseTarget = null;
        attackTarget = null;
        healTarget = null;
        teamStrength = actionCooldown==0?1:0;
        instantkill = false;
        closeFriendsSize = 0;

        for (RobotInfo robot : nearbyTeammates) {
            if (leader == null || robot.getHealth() > leader.getHealth()) {
                leader = robot;
            }
            teamStrength += 1;
            if (robot.location.distanceSquaredTo(rc.getLocation()) <= 8){
                closeFriendsSize++;
            }
        }

        int minDist = Integer.MAX_VALUE;
        for (RobotInfo robot : nearbyEnemies) {
            teamStrength -= 1;
            if (robot.location.distanceSquaredTo(rc.getLocation()) > ATTACK_DISTANCE) {
                chaseTarget = robot;
            }
            int dist = rc.getLocation().distanceSquaredTo(robot.location);
            if(dist < minDist) {
                minDist = dist;
                closestEnemy = robot.location;
            }
        }


        attackTarget = getBestTarget(rc);
        healTarget = getBestHealTarget(rc);
    }

    public static void runMinion(RobotController rc) throws GameActionException {
        sense(rc);
        micro(rc);

        macro(rc);

        if (rc.isActionReady()){
            sense(rc);
            micro(rc);
        }

        if (leader != null){
            cachedLeader = leader;
            cachedLeaderRound = rc.getRoundNum();
        }

        // Sense if enemy flag is captured
        if(flagLast > 0 && !rc.hasFlag() && Arrays.asList(RobotPlayer.spawnLocs).contains(rc.getLocation())) {
            Communication.capturedFlag(rc, flagLast);
        }

        FlagInfo[] flag = rc.senseNearbyFlags(0,rc.getTeam().opponent());
        if(flag.length > 0 && rc.hasFlag()) {
            flagLast = flag[0].getID();
        } else {
            flagLast = -1;
        }


    }

    private static void micro(RobotController rc) throws GameActionException {
        if (!rc.isActionReady())
            return;
        if (nearbyEnemies.length > 0) {
            RobotPlayer.indicator += "micro,";
            tryAttack(rc);
            if (nearbyEnemies.length > 4 && rc.getCrumbs() > 1000) {
                if (rc.canBuild(TrapType.STUN, rc.getLocation())) {
                    rc.build(TrapType.STUN, rc.getLocation());
                }
            }
            bestMicro = getBestMicro(rc);
            if (bestMicro != null) {
                boolean canHeal = (state == STATE_DEFENSIVE) ||
                        (bestMicro.canAttackNext == 0 && (state == STATE_HOLDING || bestMicro.canBeAttackedNext == 0));
                // we should only heal before moving if we have nothing better to do after moving
                if (canHeal && bestMicro.canAttack == 0 && bestMicro.canHealHigh == 0) {
                    tryHeal(rc);
                }

                Pathfinder.tryMoveDir(rc, bestMicro.dir);
                closestEnemy = bestMicro.closestEnemyLoc;
                tryAttack(rc);


                if (canHeal) {
                    tryHeal(rc);
                }
                return;
            }
        }
        tryHeal(rc);
        if (rc.isActionReady() && allyToHeal) {
            RobotPlayer.indicator += "healtarget,";
            Pathfinder.moveToward(rc, healTarget.location);
            tryHeal(rc);
        }
    }

    public static void tryHeal(RobotController rc) throws GameActionException {
        if (!rc.isActionReady())
            return;
        RobotInfo healingTarget = null;
        double bestScore = -Double.MAX_VALUE;
        for (RobotInfo r: rc.senseNearbyRobots(GameConstants.HEAL_RADIUS_SQUARED, rc.getTeam())) {
            if (r.health == GameConstants.DEFAULT_HEALTH)
                continue;
            double score = getHealingTargetScore(r);
            if (score > bestScore) {
                bestScore = score;
                healingTarget = r;
            }
        }
        if (healingTarget != null && rc.canHeal(healingTarget.location))
            rc.heal(healingTarget.location);
    }

    private static MicroDirection getBestMicro(RobotController rc) throws GameActionException {
        oppTotalStrength = 0;
        myTotalStrength = rc.getHealth();
        // we should plan step attack if we can attack next turn
        shouldPlanStepAttack = rc.getActionCooldownTurns() < 20;

        MicroDirection[] micros = new MicroDirection[9];
        for (int i = 8; --i >= 0;)
            micros[i] = new MicroDirection(rc, RobotPlayer.directions[i]);
        micros[8] = new MicroDirection(rc, Direction.CENTER);
        RobotInfo[] enemies = nearbyEnemies;
        for (int i = enemies.length; --i >= 0 && Clock.getBytecodesLeft() >= 10000;) {
            RobotInfo enemy = enemies[i];
            oppTotalStrength += enemy.health;
            micros[0].updateEnemy(rc, enemy);
            micros[1].updateEnemy(rc, enemy);
            micros[2].updateEnemy(rc, enemy);
            micros[3].updateEnemy(rc, enemy);
            micros[4].updateEnemy(rc, enemy);
            micros[5].updateEnemy(rc, enemy);
            micros[6].updateEnemy(rc, enemy);
            micros[7].updateEnemy(rc, enemy);
            micros[8].updateEnemy(rc, enemy);
        }
        RobotInfo[] allies = nearbyTeammates;
        for (int i = allies.length;
             --i >= 0 && Clock.getBytecodesLeft() >= 10000;) {
            RobotInfo ally = allies[i];
            myTotalStrength += ally.health;
            micros[0].updateAlly(rc, ally);
            micros[1].updateAlly(rc, ally);
            micros[2].updateAlly(rc, ally);
            micros[3].updateAlly(rc, ally);
            micros[4].updateAlly(rc, ally);
            micros[5].updateAlly(rc, ally);
            micros[6].updateAlly(rc, ally);
            micros[7].updateAlly(rc, ally);
            micros[8].updateAlly(rc, ally);
        }
        int lowBar = 450;
        int highBar = 700;
        // be more aggressive on smaller map
        // (mostly just to deal with camel_case style rush so that we get to late game safely)
        if (RobotPlayer.mapHeight * RobotPlayer.mapWidth < 1600 && rc.getRoundNum() < 500) {
            highBar = 550;
            lowBar = 350;
        }
        if (rc.getHealth() < lowBar) {
            state = STATE_DEFENSIVE;
        }  else {
            boolean hold = rc.getHealth() < highBar;
            if (rc.getLevel(SkillType.HEAL)>3 &&
                    Math.sqrt(rc.getLocation().distanceSquaredTo(Pathfinder.findClosestLocation(rc.getLocation(), Arrays.asList(RobotPlayer.spawnLocs)))) > (RobotPlayer.mapHeight + RobotPlayer.mapWidth) / 12.0)
                // healers don't be offensive unless our base/flag threatened
                hold = true;
            if (hold) {
                state = STATE_HOLDING;
            } else {
                state = STATE_OFFENSIVE;
            }
        }

        MicroDirection micro = micros[8];
        int canBeAttacked = 0;
        for (int i = 0; i < 8; ++i) {
            canBeAttacked |= micros[i].canBeAttackedNext;
            if (micros[i].isBetterThan(rc, micro)) micro = micros[i];
        }
        // if all directions are not attackable by enemy and that we can't reach any enemy according to vision range BFS
        // ignore the enemy and give control to macro
        if (canBeAttacked == 0) {
            return null;
        }
        if (micro.needFill == 1) {
            // if a fill is needed, fill and then recalc where to move
            MapLocation fillLoc = rc.getLocation().add(micro.dir);
            if (rc.canFill(fillLoc)) {
                rc.fill(fillLoc);
                micro = micros[8];
                for (int i = 0; i < 8; ++i) {
                    micros[i].resetAfterFill(rc);
                    if (micros[i].isBetterThan(rc, micro)) micro = micros[i];
                }
                return micro;
            } else {
                //Debug.failFast("impossible");
            }
        }
        return micro;
    }

    // We may attack twice in a round bc level
    private static void tryAttack(RobotController rc) throws GameActionException {
        if (!rc.isActionReady())
            return;
        RobotInfo bestTarget = null;
        double bestScore = -Double.MAX_VALUE;
        for (RobotInfo r : rc.senseNearbyRobots(GameConstants.ATTACK_RADIUS_SQUARED, rc.getTeam().opponent())) {
            double score = getAttackTargetScore(rc, r);
            if (score > bestScore) {
                bestScore = score;
                bestTarget = r;
            }
        }
        if (bestTarget != null && rc.canAttack(bestTarget.location)) {
            rc.attack(bestTarget.location);
            if (bestTarget.health <= rc.getAttackDamage()) {
                //deadTarget = bestTarget.location;
            }
            if (rc.isActionReady())
                tryAttack(rc);
        }
    }

    private static void macro(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady())
            return;


        if (!rc.hasFlag()) {
            MapLocation[] crumbLocs = rc.senseNearbyCrumbs(-1);
            if(crumbLocs.length > 0) {
                MapLocation closestCrumb = Pathfinder.findClosestLocation(rc.getLocation(), Arrays.asList(crumbLocs));
                if(rc.getLocation().distanceSquaredTo(closestCrumb) <= 16) {
                    Pathfinder.moveToward(rc, closestCrumb);
                }
            }

            if (closeFriendsSize < 3 && (rc.getRoundNum() - lastAttackRound) < 10) {
                if (rc.isMovementReady() && leader != null ) {
                    RobotPlayer.indicator += "group,";
                    if (!rc.getLocation().isAdjacentTo(leader.location)) {
                        RobotPlayer.indicator += "following leader,";
                        Pathfinder.follow(rc, leader.location);
                    } else if (rc.getHealth() < leader.health) { // allowing healthier target to move away first
                        RobotPlayer.indicator += "stop";
                        rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
                        return;
                    }
                    rc.setIndicatorLine(rc.getLocation(), leader.location, 0, 255, 0);
                } else if (rc.isMovementReady()
                        && leader == null
                        && cachedLeader != null
                        && rc.getRoundNum() - cachedLeaderRound < 6
                        && !rc.getLocation().isAdjacentTo(cachedLeader.location)){
                    RobotPlayer.indicator += String.format("cacheGroup%s,",cachedLeader.location);
                    Pathfinder.follow(rc, cachedLeader.location);
                    rc.setIndicatorLine(rc.getLocation(), cachedLeader.location, 0, 255, 0);
                }
            }
            runFindFlags(rc, nearbyTeammates);
        } else {
            // if we have the flag, move towards the closest ally spawn zone
            MapLocation closestSpawn = Pathfinder.findClosestLocation(rc.getLocation(), Arrays.asList(RobotPlayer.spawnLocs));
            Direction dir = Pathfinder.directionToward(rc, closestSpawn);
            if(dir != null) {
                rc.move(dir);
            }
        }
    }

    private static void chase(RobotController rc, MapLocation location) throws GameActionException{
        Direction forwardDir = rc.getLocation().directionTo(location);
        Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;

        // pick a direction to chase to minimize the number of enemies that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir) && rc.getLocation().add(dir).distanceSquaredTo(location) <= ATTACK_DISTANCE) {
                int canSee = 0;
                for (RobotInfo enemy : nearbyEnemies){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemy.location);
                    if (newDis <= VISION_DIS) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                //} else if (minCanSee == canSee) {  // TODO: Test without diagonal
                    } else if (minCanSee == canSee && Pathfinder.isDiagonal(bestDir) && !Pathfinder.isDiagonal(dir)) {  // TODO: Test without diagonal
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null) {
            RobotPlayer.indicator += String.format("chase%s,", location);
            rc.move(bestDir);
        } else {
            RobotPlayer.indicator += "failchase,";
        }
    }
    private static void fallback(RobotController rc, MapLocation location) throws GameActionException {
        Direction backDir = rc.getLocation().directionTo(location).opposite();
        Direction[] dirs = {Direction.CENTER, backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        // pick a direction to move back to minimize the number of enemies that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                int canSee = 0;
                for (RobotInfo enemy : nearbyEnemies){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemy.location);
                    if (newDis <= VISION_DIS) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                } else if (minCanSee == canSee && Pathfinder.isDiagonal(bestDir) && !Pathfinder.isDiagonal(dir)) { // TODO: Test without diagonal
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null && bestDir != Direction.CENTER){
            RobotPlayer.indicator += "kite,";
            rc.move(bestDir);
        }
    }

    public static void runFindFlags(RobotController rc, RobotInfo[] nearbyTeammates) throws GameActionException {

        // Help out if flag is taken nearby
        ArrayList<MapLocation> allyFlags = Communication.getAllyFlagLocations(rc);
        for(MapLocation allyLoc : allyFlags) {
            if(!Arrays.asList(RobotPlayer.flagSpawnLocation).contains(allyLoc)) { // If flag is taken by enemy
                if(rc.getLocation().distanceSquaredTo(allyLoc) < 200) {
                //if(rc.getID() % 2 == 0) {
                    Direction dir = Pathfinder.directionToward(rc, allyLoc);
                    if(dir != null) {
                        for(MapLocation spa : RobotPlayer.flagSpawnLocation) {
                            rc.setIndicatorDot(allyLoc, 244, 0, 255);
                        }
                        rc.move(dir);
                        rc.setIndicatorDot(allyLoc, 244, 0, 0);
                        RobotPlayer.indicator += "Defending flag!";
                        return;
                    }
                }
            }
        }

        // move towards the closest enemy flag (including broadcast locations)
        ArrayList<MapLocation> flagLocations = Communication.getEnemyFlagLocations(rc);
        ArrayList<MapLocation> flagSpawnLocations = Communication.getEnemyFlagSpawnLocations(rc);

        // Runs until we find the first enemy flag spawn location
        if (flagSpawnLocations.isEmpty()) {
            MapLocation[] broadcastLocs = rc.senseBroadcastFlagLocations();
            for (MapLocation flagLoc : broadcastLocs) {
                // Jitter the broadcast location to allow for some exploration
                MapLocation newLoc = flagLoc;
                for (int i = 0; i < 5; i++) {
                    newLoc = newLoc.add(RobotPlayer.directions[RobotPlayer.random.nextInt(8)]);
                }
                flagLocations.add(newLoc);

            }
        }

        if(FOLLOW_FLAG) {
            MapLocation closestFlag = Pathfinder.findClosestLocation(rc.getLocation(), flagLocations);
            if(closestFlag != null) {

                Direction dir = Pathfinder.directionToward(rc, closestFlag);
                for(RobotInfo teammate : nearbyTeammates) {
                    if(teammate.hasFlag() && flagLocations.contains(teammate.getLocation())) { // Our teammate has the flag
                        //if(dir != null) {
                            //RobotPlayer.indicator += "Following flag holder";
                            if(rc.getLocation().distanceSquaredTo(teammate.location) <= 4) {
                                Pathfinder.tryMoveDir(rc, teammate.getLocation().directionTo(rc.getLocation()));
                            } else {
                                Pathfinder.follow(rc, closestFlag);
                            }

                            /*if(rc.getLocation().directionTo(closestFlag).equals(teammate.getLocation().directionTo(closestFlag))) {
                                if(rc.canMove(rc.getLocation().directionTo(closestFlag))) {
                                    rc.move(rc.getLocation().directionTo(closestFlag));
                                }
                            }*/
                            /*int dist = rc.getLocation().add(dir).distanceSquaredTo(closestFlag);
                            if(dist > 4) {
                                rc.move(dir);
                            } else { // Too close means we move away
                                if(rc.canMove(dir.opposite())) {
                                    rc.move(dir.opposite());
                                }
                            }*/
                        //}
                    }
                }


                if(dir != null) {
                    if(rc.canMove(dir)) rc.move(dir);
                }
            } else {
                // if there are no known enemy flags, explore randomly
                RobotPlayer.indicator += "Exploring";
                Pathfinder.explore(rc);
            }
        }
    }

    private static RobotInfo getBestTarget(RobotController rc) throws GameActionException {
        int minHitReqired = Integer.MAX_VALUE;
        RobotInfo rv = null;
        int minDis = Integer.MAX_VALUE;
        RobotInfo self = rc.senseRobotAtLocation(rc.getLocation());
        for (RobotInfo enemy : nearbyEnemies) {
            int dis = enemy.location.distanceSquaredTo(rc.getLocation());
            if (dis > ATTACK_DISTANCE) {
                continue;
            }
            if(rv==null) rv = enemy;
            // Prioritize enemy with flags
            // TODO: We may exit out early and not attack enemy with a flag
            if(enemy.hasFlag) return enemy;

            int totalDamage = computeDamage(self);

            if (enemy.getHealth() <= totalDamage) {
                return enemy;
            }

            // Compute how much damage we can do collectively

            for (RobotInfo friend : nearbyTeammates) {
                int friendEnemyDis = friend.location.distanceSquaredTo(enemy.location);
                if (friendEnemyDis <= ATTACK_DISTANCE) {
                    totalDamage += computeDamage(friend);
                }
            }

            int hitRequired = (int) Math.ceil((double) enemy.getHealth() / totalDamage);
            if (hitRequired < minHitReqired) {
                minHitReqired = hitRequired;
                rv = enemy;
                minDis = enemy.location.distanceSquaredTo(rc.getLocation());
            } else if (hitRequired == minHitReqired) {
                if (dis < minDis) { // Prioritize closer enemies
                    rv = enemy;
                    minDis = dis;
                }
            }
            if(hitRequired == 1) instantkill = true;
        }
        return rv;
    }

    private static RobotInfo getBestHealTarget(RobotController rc) throws GameActionException {
        allyToHeal = false;
        double bestHealingTargetScore = -Double.MAX_VALUE;
        RobotInfo result = null;
        for (RobotInfo friend : rc.senseNearbyRobots(GameConstants.HEAL_RADIUS_SQUARED, rc.getTeam())) {
            if (friend.getHealth() <= 900) {
                allyToHeal = true;
            }
            double score = getHealingTargetScore(friend);
            if (score > bestHealingTargetScore) {
                bestHealingTargetScore = score;
                result = friend;
            }
        }

        /*int maxDamage = 0;
        RobotInfo result = null;
        int minDis = Integer.MAX_VALUE;
        RobotInfo self = rc.senseRobotAtLocation(rc.getLocation());
        for (RobotInfo friend : nearbyTeammates) {
            if(friend.health >= 1000) continue;
            int dis = friend.location.distanceSquaredTo(rc.getLocation());
            if (dis > HEAL_DISTANCE) {
                continue;
            }

            if(result==null) result = friend;
            // Prioritize enemy with flags
            if(friend.hasFlag) return friend;


            // Compute how much damage we can do collectively
            // TODO: Cache damage of each teammate and self
            int totalDamage = computeDamage(self);
            for (RobotInfo enemy : nearbyEnemies) {
                int friendEnemyDis = friend.location.distanceSquaredTo(enemy.location);
                if (friendEnemyDis <= ATTACK_DISTANCE) {
                    totalDamage += computeDamage(friend);
                }
            }

            if (totalDamage >= 1000) {
                maxDamage = 1000;
                if (dis < minDis) { // Prioritize closer friends
                    result = friend;
                    minDis = dis;
                }
            } else if (totalDamage >= maxDamage) {
                maxDamage = totalDamage;
                result = friend;
            }
        }*/
        return result;
    }

    public static double getHealingTargetScore(RobotInfo r) {
        if (r.health == GameConstants.DEFAULT_HEALTH)
            return -1e9;
        return getRobotScore(r);
    }
    static double getAttackTargetScore(RobotController rc, RobotInfo r) {
        double score = 0;
        if (r.health <= rc.getAttackDamage()) // prioritize anything we can kill
            score += 1e9;
        if (r.hasFlag()) {
            score += 1e8;
        }
        int timeToKill = (r.getHealth() + rc.getAttackDamage() - 1) / rc.getAttackDamage();
        score += getRobotScore(r) / timeToKill;
        return score;
    }


    private static double getRobotScore(RobotInfo r) {
        double attackScore = 0;
        switch (r.getAttackLevel()) { // according to DPS
            case 1: attackScore += 1.05 / 0.95 - 1; break;
            case 2: attackScore += 1.07 / 0.93 - 1; break;
            case 3: attackScore += 1.1 / 0.9 - 1; break;
            case 4: attackScore += 1.3 / 0.8 - 1; break;
            case 5: attackScore += 1.35 / 0.65 - 1; break;
            case 6: attackScore += 1.6 / 0.4 - 1; break;
        }
        double buildScore = 0;
        switch (r.getBuildLevel()) { // according to saved cost
            case 1: buildScore += 0.1; break;
            case 2: buildScore += 0.15; break;
            case 3: buildScore += 0.2; break;
            case 4: buildScore += 0.3; break;
            case 5: buildScore += 0.4; break;
            case 6: buildScore += 0.5; break;
        }
        double healScore = 0;
        switch (r.getHealLevel()) { // according to DPS
            case 1: healScore += 1.03 / 0.95 - 1; break;
            case 2: healScore += 1.05 / 0.9 - 1; break;
            case 3: healScore += 1.07 / 0.85 - 1; break;
            case 4: healScore += 1.1 / 0.85 - 1; break;
            case 5: healScore += 1.15 / 0.85 - 1; break;
            case 6: healScore += 1.25 / 0.75 - 1; break;
        }
        return 0.01 + attackScore + buildScore * 4 + healScore;
    }
    static int computeDamage(RobotInfo robot) {

        int level = robot.attackLevel;
        int base = BASE_DAMAGE;
        if(RobotPlayer.attackUpgrade) {
            base += DAMAGE_UPGRADE;
        }
        int jail = RobotPlayer.jailedPenalty ? 1 : 0;

        switch(level) {
            case 0:
                return base-jail;
            case 1:
                return (int) Math.round((base-2*jail)*1.05);
            case 2:
                return (int) Math.round((base-2*jail)*1.07);
            case 3:
                return (int) Math.round((base-5*jail)*1.1);
            case 4:
                return (int) Math.round((base-5*jail)*1.3);
            case 5:
                return (int) Math.round((base-10*jail)*1.35);
            case 6:
                return (int) Math.round((base-12*jail)*1.6);
        }
        System.out.println("Could not calculate attack damage!");
        return 150;
    }

    static int computeHeal(RobotInfo robot) {
        int level = robot.healLevel;
        int base = BASE_HEAL;
        if(RobotPlayer.healingUpgrade) {
            base += HEAL_UPGRADE;
        }
        int jail = RobotPlayer.jailedPenalty ? 1 : 0;

        switch(level) {
            case 0:
                return base-jail;
            case 1:
                return (int) Math.round((base-5*jail)*1.03);
            case 2:
                return (int) Math.round((base-5*jail)*1.05);
            case 3:
                return (int) Math.round((base-10*jail)*1.07);
            case 4:
                return (int) Math.round((base-10*jail)*1.1);
            case 5:
                return (int) Math.round((base-15*jail)*1.15);
            case 6:
                return (int) Math.round((base-18*jail)*1.25);
        }
        System.out.println("Could not calculate attack damage!");
        return 150;
    }

    static class MicroDirection {
        // xsquare micro: evaluate/update each direction separately
        // reference https://github.com/IvanGeffner/Battlecode23/blob/master/fortytwo/MicroAttacker.java
        Direction dir;
        MapLocation loc;
        int canMove;
        int blockTeammate;
        int needFill;
        int canAttack;
        int canKill;
        int numAttackRange;
        int canBeAttackedNext;
        int canAttackNext;
        int numAttackRangeNext;
        int allyCloseCnt;
        int minDistanceToEnemy = 99999999;
        int minDistanceToAlly = 99999999;
        int canHealLow, canHealHigh; // high means healing non-healer, low means healer
        int disToHealerHigh = 9999999;
        int disToHealer = 9999999;
        MapLocation closestEnemyLoc;

        public MicroDirection(RobotController rc, Direction dir) throws GameActionException {
            this.dir = dir;
            this.loc = rc.getLocation().add(dir);
            if (dir == Direction.CENTER || rc.canMove(dir)) {
                canMove = 1;
            }
            // allow micro water filling when many friendly units around
            else if (rc.canFill(this.loc) && nearbyTeammates.length > 5) {
                canMove = 1;
                needFill = 1;
            }
        }

        void resetAfterFill(RobotController rc) {
            canMove = dir == Direction.CENTER || rc.canMove(dir)? 1 : 0;
            needFill= 0;
            canAttack = 0;
            canAttackNext = 0;
            shouldPlanStepAttack = false;
            canKill = 0;
            canHealLow = 0;
            canHealHigh = 0;
        }

        void updateEnemy(RobotController rc, RobotInfo enemy) throws GameActionException {
            if (canMove == 0) return;
            int dis = loc.distanceSquaredTo(enemy.location);
            if (dis <= GameConstants.ATTACK_RADIUS_SQUARED) {
                canBeAttackedNext = 1;
                numAttackRange++;
                numAttackRangeNext++;
                if (rc.isActionReady() && (needFill == 0 || nearbyTeammates.length - nearbyEnemies.length > 5)) {
                    canAttack = 1;
                    canAttackNext = 1;
                    if (enemy.health <= rc.getAttackDamage() || enemy.hasFlag)
                        canKill = 1;
                }
            } else if (dis <= 10) {
                if (canAttackNext == 0 && shouldPlanStepAttack) {
                    canAttackNext = checkCanAttack(rc, loc, enemy.location);
                }
                if (canBeAttackedNext == 0) {
                    canBeAttackedNext = checkCanAttack(rc, enemy.location, loc);
                }
                numAttackRangeNext++;
            }
            if (dis < minDistanceToEnemy) {
                minDistanceToEnemy = dis;
                closestEnemyLoc = enemy.location;
            }
        }

        int checkCanAttack(RobotController rc, MapLocation fromLoc, MapLocation toLoc) throws GameActionException {
            Direction dir = fromLoc.directionTo(toLoc);
            MapLocation x = fromLoc.add(dir);
            if (x.isWithinDistanceSquared(toLoc, GameConstants.ATTACK_RADIUS_SQUARED) && rc.canSenseLocation(x) && rc.sensePassability(x) && rc.senseRobotAtLocation(x) == null) {
                return 1;
            }
            x = fromLoc.add(dir.rotateRight());
            if (x.isWithinDistanceSquared(toLoc, GameConstants.ATTACK_RADIUS_SQUARED) && rc.canSenseLocation(x) && rc.sensePassability(x) && rc.senseRobotAtLocation(x) == null) {
                return 1;
            }
            x = fromLoc.add(dir.rotateLeft());
            if (x.isWithinDistanceSquared(toLoc, GameConstants.ATTACK_RADIUS_SQUARED) && rc.canSenseLocation(x) && rc.sensePassability(x) && rc.senseRobotAtLocation(x) == null) {
                return 1;
            }
            return 0;
        }

        void updateAlly(RobotController rc, RobotInfo ally) throws GameActionException {
            if (canMove == 0) return;
            int dis = loc.distanceSquaredTo(ally.location);
            if (dis <= 2 && blockTeammate == 0
                    && (ally.health < Math.min(rc.getHealth(), 700) && state != STATE_DEFENSIVE || ally.hasFlag)) {
                // If I am moving adjacent to a teammate, and we are blocking that teammate's way out from the enemy
                // that teammate must be allowed to have another way out of the enemy
                Direction dirOut = closestEnemyLoc.directionTo(ally.location);
                Direction blockedDir = ally.location.directionTo(loc);
                if (blockedDir.equals(dirOut) || blockedDir.equals(dirOut.rotateLeft()) || blockedDir.equals(dirOut.rotateRight())) {
                    // we are blocking the teammate's way out
                    ok: {
                        MapLocation a = ally.location.add(dirOut);
                        if (rc.onTheMap(a) && rc.sensePassability(a) && (rc.senseRobotAtLocation(a) == null || a.equals(rc.getLocation())) && !a.equals(loc)) {
                            break ok;
                        }
                        a = ally.location.add(dirOut.rotateLeft());
                        if (rc.onTheMap(a) && rc.sensePassability(a) && (rc.senseRobotAtLocation(a) == null || a.equals(rc.getLocation())) && !a.equals(loc)) {
                            break ok;
                        }
                        a = ally.location.add(dirOut.rotateLeft());
                        if (rc.onTheMap(a) && rc.sensePassability(a) && (rc.senseRobotAtLocation(a) == null || a.equals(rc.getLocation())) && !a.equals(loc)) {
                            break ok;
                        }
                        blockTeammate = 1;
                        //Debug.setIndicatorDot(Debug.MICRO, loc, 255, 0, 0);
                    }
                }
            }
            if (ally.getHealth() < 870) {
                // first priority is to heal a non-healer
                if (dis <= GameConstants.HEAL_RADIUS_SQUARED && rc.isActionReady()) {
                    canHealHigh = 1;
                }
                disToHealerHigh = Math.min(disToHealerHigh, dis);
            }  else if (ally.health < 870) {
                if (dis <= GameConstants.HEAL_RADIUS_SQUARED  && rc.isActionReady()) {
                    canHealLow = 1;
                }
                disToHealer = Math.min(disToHealer, dis);
            }
            if (dis < minDistanceToAlly) {
                minDistanceToAlly = dis;
            }
        }

        boolean isBetterThan(RobotController rc, MicroDirection other) {
            if (canMove != other.canMove) return canMove > other.canMove;
            if (blockTeammate != other.blockTeammate) return blockTeammate < other.blockTeammate;
            if (state != STATE_OFFENSIVE && needFill != other.needFill) // don't fill unless offensive
                return needFill < other.needFill;
            switch (state) {
                case STATE_DEFENSIVE:
                    // play safe
                    if (numAttackRange - canAttack != other.numAttackRange - other.canAttack)
                        return numAttackRange - canAttack < other.numAttackRange - other.canAttack;
                    if (numAttackRangeNext - canKill != other.numAttackRangeNext - other.canKill) {
                        return numAttackRangeNext - canKill < other.numAttackRangeNext - other.canKill;
                    }
                    if (canAttack != other.canAttack)
                        return canAttack > other.canAttack;
                    if (canKill != other.canKill)
                        return canKill > other.canKill;

                    if (canHealHigh != other.canHealHigh)
                        return canHealHigh > other.canHealHigh;
                    if (canHealLow != other.canHealLow)
                        return canHealLow > other.canHealLow;
                    if (disToHealerHigh != other.disToHealerHigh)
                        return disToHealerHigh < other.disToHealerHigh;
                    if (disToHealer != other.disToHealer)
                        return disToHealer < other.disToHealer;
                    if (minDistanceToAlly != other.minDistanceToAlly)
                        return minDistanceToAlly < other.minDistanceToAlly;
                    return minDistanceToEnemy >= other.minDistanceToEnemy;

                case STATE_HOLDING:
                    // if we can step attack anyone, do it
                    if (numAttackRange - canAttack != other.numAttackRange - other.canAttack)
                        return numAttackRange - canAttack < other.numAttackRange - other.canAttack;
                    if (canAttack != other.canAttack)
                        return canAttack > other.canAttack;
                    if (canKill != other.canKill)
                        return canKill > other.canKill;

                    if (numAttackRangeNext != other.numAttackRangeNext) {
                        return numAttackRangeNext < other.numAttackRangeNext;
                    }
                    if (canHealHigh != other.canHealHigh)
                        return canHealHigh > other.canHealHigh;
                    if (canHealLow != other.canHealLow)
                        return canHealLow > other.canHealLow;
                    if (disToHealerHigh != other.disToHealerHigh)
                        return disToHealerHigh < other.disToHealerHigh;
                    if (disToHealer != other.disToHealer)
                        return disToHealer < other.disToHealer;
                    if (allyCloseCnt != other.allyCloseCnt) {
                        return allyCloseCnt > other.allyCloseCnt;
                    }
                    return minDistanceToEnemy <= other.minDistanceToEnemy;


                case STATE_OFFENSIVE:
                    // when surrounding enemy at a tight choke point, someone needs to go in
                    // this prevents getting stuck with enemy in the following bad equilibrium:
                    /* 0 is space, w is wall, 1/2 are the 2 teams
                    no one will go forward because that risks getting attacked by 2 enemies
                    1ww22
                    1w022
                    110w2
                    11ww2
                     */
                    if (myTotalStrength > 900 && myTotalStrength - oppTotalStrength > 500) {
                        if (canAttack != other.canAttack)
                            return canAttack > other.canAttack;
                    }
                    if (numAttackRange - canAttack != other.numAttackRange - other.canAttack)
                        return numAttackRange - canAttack < other.numAttackRange - other.canAttack;
                    if (canAttack != other.canAttack)
                        return canAttack > other.canAttack;
                    if (canKill != other.canKill)
                        return canKill > other.canKill;

                    if (shouldPlanStepAttack) {
                        // if can attack next turn, want to have as few target as possible, but at least 1
                        if (canAttackNext != other.canAttackNext)
                            return canAttackNext > other.canAttackNext;
                        if (canAttack == 1) {
                            if (canBeAttackedNext != other.canBeAttackedNext)
                                return canBeAttackedNext < other.canBeAttackedNext;
                        }
                        if (numAttackRangeNext != other.numAttackRangeNext) {
                            if (numAttackRangeNext == 0) return false;
                            if (other.numAttackRangeNext == 0) return true;
                            return numAttackRangeNext < other.numAttackRangeNext;
                        }
                    } else {
                        if (canBeAttackedNext != other.canBeAttackedNext)
                            return canBeAttackedNext < other.canBeAttackedNext;
                        if (canBeAttackedNext > 0) {
                            if (numAttackRangeNext != other.numAttackRangeNext)
                                return numAttackRangeNext < other.numAttackRangeNext;
                        }
                    }
                    if (allyCloseCnt != other.allyCloseCnt) {
                        return allyCloseCnt > other.allyCloseCnt;
                    }
                    return minDistanceToEnemy <= other.minDistanceToEnemy;
            }
            return false;
        }
    }
}
