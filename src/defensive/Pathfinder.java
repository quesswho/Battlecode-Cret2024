package defensive;

import battlecode.common.*;

import java.util.List;

public class Pathfinder {

    private static Direction dir;

    public static Direction exploreDirection(RobotController rc) throws GameActionException {
        if(rc.isMovementReady()) {
            MapLocation[] crumbLocs = rc.senseNearbyCrumbs(-1);
            if(crumbLocs.length > 0) {
                Direction temp = directionToward(rc, crumbLocs[0]);
                if(temp != null) {
                    return temp;
                }
            }

            if(dir == null || !rc.canMove(dir)) {
                dir = RobotPlayer.directions[RobotPlayer.random.nextInt(8)];
            }
            boolean dirCanPass = rc.canMove(dir);
            boolean dirCanFill = rc.canFill(rc.getLocation().add(dir));

            boolean dirRightCanPass = rc.canMove(dir.rotateRight());
            boolean dirLeftCanPass = rc.canMove(dir.rotateLeft());

            if(rc.canMove(dir)) {
                return dir;
            } if (dirCanPass && rc.canMove(dir)) {
                return dir;
            } else if (dirRightCanPass && rc.canMove(dir.rotateRight())) {
                return dir.rotateRight();
            } else if (dirLeftCanPass && rc.canMove(dir.rotateLeft())) {
                return dir.rotateLeft();
            } else if(evenSquare(rc.getLocation().add(dir)) && dirCanFill) {
                rc.fill(rc.getLocation().add(dir));
                if(rc.canMove(dir)) return dir;
            }
            else if(rc.canFill(rc.getLocation().add(dir))) rc.fill(rc.getLocation().add(dir));
        }
        return null;
    }

    public static void explore(RobotController rc) throws GameActionException {
        Direction dir = exploreDirection(rc);
        if(dir != null) {
            rc.move(dir);
        }
    }

    static void tryMoveDir(RobotController rc, Direction dir) throws GameActionException {
        if (rc.isMovementReady() && dir != Direction.CENTER) {
            if (rc.canMove(dir) && canPass(rc, dir)) {
                rc.move(dir);
            } else if (rc.canMove(dir.rotateRight()) && canPass(rc, dir.rotateRight(), dir)) {
                rc.move(dir.rotateRight());
            } else if (rc.canMove(dir.rotateLeft()) && canPass(rc, dir.rotateLeft(), dir)) {
                rc.move(dir.rotateLeft());
            } else {
                randomMove(rc);
            }
        }
    }
    static void follow(RobotController rc, MapLocation location) throws GameActionException {
        tryMoveDir(rc, rc.getLocation().directionTo(location));
    }
    static Direction randomMove(RobotController rc) throws GameActionException {
        int starting_i = FastMath.rand256() % RobotPlayer.directions.length;
        for (int i = starting_i; i < starting_i + 8; i++) {
            Direction dir = RobotPlayer.directions[i % 8];
            if(rc.canFill(rc.getLocation().add(dir))) {
                rc.fill(rc.getLocation().add(dir));
            } else if (rc.canMove(dir)) {
                return dir;
            }
        }
        return null;
    }

    static int getSteps(MapLocation a, MapLocation b) {
        int xdif = a.x - b.x;
        int ydif = a.y - b.y;
        if (xdif < 0) xdif = -xdif;
        if (ydif < 0) ydif = -ydif;
        if (xdif > ydif) return xdif;
        else return ydif;
    }

    private static final int PRV_LENGTH = 60;
    private static Direction[] prv = new Direction[PRV_LENGTH];
    private static int pathingCnt = 0;
    private static MapLocation lastPathingTarget = null;
    private static MapLocation lastLocation = null;
    private static int stuckCnt = 0;
    private static int lastPathingTurn = 0;
    private static int currentTurnDir = FastMath.rand256() % 2;
    public static int disableTurnDirRound = 0;

    private static Direction[] prv_ = new Direction[PRV_LENGTH];
    private static int pathingCnt_ = 0;
    static int MAX_DEPTH = 15;

    static void moveToward(RobotController rc, MapLocation location) throws GameActionException {
        Direction dir = directionToward(rc, location);
        if(dir != null) {
            rc.move(dir);
        }
    }

    static Direction directionToward(RobotController rc, MapLocation location) throws GameActionException {
        Direction result = null;
        // reset queue when target location changes or there's gap in between calls
        if (!location.equals(lastPathingTarget) || lastPathingTurn < RobotPlayer.roundCount - 4) {
            pathingCnt = 0;
            stuckCnt = 0;
        }
        //RobotPlayer.indicator += String.format("2%sc%dt%s,", location, pathingCnt, currentTurnDir == 0? "L":"R");
        if (rc.isMovementReady()) {
            // we increase stuck count only if it's a new turn (optim for empty carriers)
            if (rc.getLocation().equals(lastLocation)) {
                if (RobotPlayer.roundCount != lastPathingTurn) {
                    stuckCnt++;
                }
            } else {
                lastLocation = rc.getLocation();
                stuckCnt = 0;
            }
            lastPathingTarget = location;
            lastPathingTurn = RobotPlayer.roundCount;
            if (stuckCnt >= 3) {
                RobotPlayer.indicator += "stuck reset";
                pathingCnt = 0;
                return randomMove(rc);
            }

            if (pathingCnt == 0) {
                //if free of obstacle: try go directly to target
                Direction dir = rc.getLocation().directionTo(location);
                MapLocation loc = rc.getLocation().add(dir);
                boolean dirCanFill = rc.canFill(loc);
                boolean dirCanPass = canPass(rc, dir);

                MapLocation rightLoc = rc.getLocation().add(dir.rotateRight());
                boolean dirRightCanPass = canPass(rc, dir.rotateRight(), dir);
                boolean dirRightCanFill = rc.canFill(rightLoc);

                MapLocation leftLoc = rc.getLocation().add(dir.rotateLeft());
                boolean dirLeftCanPass = canPass(rc, dir.rotateLeft(), dir);
                boolean dirLeftCanFill = rc.canFill(leftLoc);

                if (dirCanFill || dirCanPass || dirRightCanPass || dirLeftCanPass || dirRightCanFill || dirLeftCanFill) {

                    if (dirCanPass && rc.canMove(dir)) {
                        return dir;
                    } else if (dirRightCanPass && rc.canMove(dir.rotateRight())) {
                        return dir.rotateRight();
                    } else if (dirLeftCanPass && rc.canMove(dir.rotateLeft())) {
                        return dir.rotateLeft();
                    } else if(evenSquare(loc) && dirCanFill) {
                        rc.fill(loc);
                        if(rc.canMove(dir)) return dir;
                    } else if(evenSquare(rightLoc) && dirRightCanFill) {
                        rc.fill(rightLoc);
                        if(rc.canMove(dir.rotateRight())) return dir.rotateRight();
                    } else if(evenSquare(leftLoc) && dirLeftCanFill) {
                        rc.fill(leftLoc);
                        if(rc.canMove(dir.rotateLeft())) return dir.rotateLeft();
                    } else if(dirCanFill) {
                        rc.fill(loc);
                        if(rc.canMove(dir)) return dir;
                    }
                } else {
                    //encounters obstacle; run simulation to determine best way to go
                    if (rc.getRoundNum() > disableTurnDirRound) {
                        currentTurnDir = getTurnDir(rc, dir, location);
                    }
                    while (!canPass(rc, dir) && pathingCnt != 8) {
//                        rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(dir), 0, 0, 255);
                        if (!rc.onTheMap(rc.getLocation().add(dir))) {
                            currentTurnDir ^= 1;
                            pathingCnt = 0;
                            RobotPlayer.indicator += "edge switch";
                            disableTurnDirRound = rc.getRoundNum() + 100;
                            return result;
                        }
                        prv[pathingCnt] = dir;
                        pathingCnt++;
                        if (currentTurnDir == 0) dir = dir.rotateLeft();
                        else dir = dir.rotateRight();
                    }
                    if(pathingCnt == 8) {
                        RobotPlayer.indicator += "permblocked";
                    } else if (rc.canMove(dir)) {
                        return dir;
                    } else if(rc.canFill(rc.getLocation().add(dir)) && evenSquare(rc.getLocation().add(dir))) {
                        rc.fill(rc.getLocation().add(dir));
                        if (rc.canMove(dir)) return dir;
                    }
                }
            } else {
                //update stack of past directions, move to next available direction
                if (pathingCnt > 1 && canPass(rc, prv[pathingCnt - 2])) {
                    pathingCnt -= 2;
                }
                while (pathingCnt > 0 && canPass(rc, prv[pathingCnt - 1])) {
//                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt - 1]), 0, 255, 0);
                    pathingCnt--;
                }
                if (pathingCnt == 0) {
                    Direction dir = rc.getLocation().directionTo(location);
                    if (!canPass(rc, dir)) {
                        prv[pathingCnt++] = dir;
                    }
                }
                int pathingCntCutOff = Math.min(PRV_LENGTH, pathingCnt + 8); // if 8 then all dirs blocked
                while (pathingCnt > 0 && !canPass(rc, currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight())) {
                    prv[pathingCnt] = currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight();
//                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt]), 255, 0, 0);
                    if (!rc.onTheMap(rc.getLocation().add(prv[pathingCnt]))) {
                        currentTurnDir ^= 1;
                        pathingCnt = 0;
                        RobotPlayer.indicator += "edge switch";
                        disableTurnDirRound = rc.getRoundNum() + 100;
                        return result;
                    }
                    pathingCnt++;
                    if (pathingCnt == pathingCntCutOff) {
                        pathingCnt = 0;
                        RobotPlayer.indicator += "cutoff";
                        return result;
                    }
                }
                Direction moveDir = pathingCnt == 0? prv[pathingCnt] :
                        (currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight());
                if (rc.canMove(moveDir)) {
                    return moveDir;
                } else if(rc.canFill(rc.getLocation().add(moveDir)) && evenSquare(rc.getLocation().add(moveDir))) {
                    rc.fill(rc.getLocation().add(moveDir));
                    if (rc.canMove(moveDir)) return moveDir;
                } else {
                    // a robot blocking us while we are following wall, wait
                    RobotPlayer.indicator += "blocked";
                }
            }
        }
        lastPathingTarget = location;
        lastPathingTurn = RobotPlayer.roundCount;
        return result;
    }

    private static final int BYTECODE_CUTOFF = 3000;
    static int getTurnDir(RobotController rc, Direction direction, MapLocation target) throws GameActionException{
        //int ret = getCenterDir(direction);
        MapLocation now = rc.getLocation();
        int moveLeft = 0;
        int moveRight = 0;

        pathingCnt_ = 0;
        Direction dir = direction;
        while (!canPass(rc, now.add(dir), dir) && pathingCnt_ != 8) {
            prv_[pathingCnt_] = dir;
            pathingCnt_++;
            dir = dir.rotateLeft();
            if (pathingCnt_ > 8) {
                break;
            }
        }
        now = now.add(dir);

        int byteCodeRem = Clock.getBytecodesLeft();
        if (byteCodeRem < BYTECODE_CUTOFF)
            return FastMath.rand256() % 2;
        //simulate turning left
        while (pathingCnt_ > 0) {
            moveLeft++;
            if (moveLeft > MAX_DEPTH) {
                break;
            }
            if (Clock.getBytecodesLeft() < BYTECODE_CUTOFF) {
                moveLeft = -1;
                break;
            }
            while (pathingCnt_ > 0 && canPass(rc, now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 1])) {
                pathingCnt_--;
            }
            if (pathingCnt_ > 1 && canPass(rc, now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 2])) {
                pathingCnt_-=2;
            }
            while (pathingCnt_ > 0 && !canPass(rc, now.add(prv_[pathingCnt_ - 1].rotateLeft()), prv_[pathingCnt_ - 1].rotateLeft())) {
                prv_[pathingCnt_] = prv_[pathingCnt_ - 1].rotateLeft();
                pathingCnt_++;
                if (pathingCnt_ > 8) {
                    moveLeft = -1;
                    break;
                }
            }
            if (pathingCnt_ > 8 || pathingCnt_ == 0) {
                break;
            }
            Direction moveDir = pathingCnt_ == 0 ? prv_[pathingCnt_] : prv_[pathingCnt_ - 1].rotateLeft();
            now = now.add(moveDir);
        }
        MapLocation leftend = now;
        pathingCnt_ = 0;
        now = rc.getLocation();
        dir = direction;
        //simulate turning right
        while (!canPass(rc, dir) && pathingCnt_ != 8) {
            prv_[pathingCnt_] = dir;
            pathingCnt_++;
            dir = dir.rotateRight();
            if (pathingCnt_ > 8) {
                break;
            }
        }
        now = now.add(dir);

        while (pathingCnt_ > 0) {
            moveRight++;
            if (moveRight > MAX_DEPTH) {
                break;
            }
            if (Clock.getBytecodesLeft() < BYTECODE_CUTOFF) {
                moveRight = -1;
                break;
            }
            while (pathingCnt_ > 0 && canPass(rc, now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 1])) {
                pathingCnt_--;
            }
            if (pathingCnt_ > 1 && canPass(rc, now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 2])) {
                pathingCnt_-=2;
            }
            while (pathingCnt_ > 0 && !canPass(rc, now.add(prv_[pathingCnt_ - 1].rotateRight()), prv_[pathingCnt_ - 1].rotateRight())) {
                prv_[pathingCnt_] = prv_[pathingCnt_ - 1].rotateRight();
                pathingCnt_++;
                if (pathingCnt_ > 8) {
                    moveRight = -1;
                    break;
                }
            }
            if (pathingCnt_ > 8 || pathingCnt_ == 0) {
                break;
            }
            Direction moveDir = pathingCnt_ == 0? prv_[pathingCnt_] : prv_[pathingCnt_ - 1].rotateRight();
            now = now.add(moveDir);
        }
        MapLocation rightend = now;
        //find best direction
        if (moveLeft == -1 || moveRight == -1) return FastMath.rand256() % 2;
        if (moveLeft + getSteps(leftend, target) <= moveRight + getSteps(rightend, target)) return 0;
        else return 1;

    }

    static boolean canPass(RobotController rc, MapLocation loc, Direction targetDir) throws GameActionException {
        if(loc.equals(rc.getLocation())) return true;
        if(!rc.onTheMap(loc)) return false;
        if(rc.getLocation().distanceSquaredTo(loc) < 25) {
            if(!rc.sensePassability(loc)) return false;
        }
        if (!rc.canSenseLocation(loc)) return true;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        if (robot == null)
            return true;
        return false;
//        return FastMath.rand256() % 4 == 0; // rng doesn't seem to help
    }

    static boolean canPass(RobotController rc, Direction dir, Direction targetDir) throws GameActionException {
        MapLocation loc = rc.getLocation().add(dir);
        if (!rc.onTheMap(loc)) return false;
        if(!rc.sensePassability(loc)) return false;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        // anchoring carriers don't yield to other robots
        if (robot == null)
            return true;
        return FastMath.rand256() % 4 == 0; // Does rng help here? Each rng is 10 bytecode btw
    }

    static boolean canPass(RobotController rc, Direction dir) throws GameActionException {
        return canPass(rc, dir, dir);
    }

    public static MapLocation findClosestLocation(MapLocation me, List<MapLocation> otherLocs) {
        MapLocation closest = null;
        int minDist = Integer.MAX_VALUE;
        for (MapLocation loc : otherLocs) {
            int dist = me.distanceSquaredTo(loc);
            if (dist < minDist) {
                minDist = dist;
                closest = loc;
            }
        }
        return closest;
    }

    public static int findClosestDistance(MapLocation me, List<MapLocation> otherLocs) {
        return findClosestLocation(me, otherLocs).distanceSquaredTo(me);
    }

    public static boolean isTowards(MapLocation start, Direction dir, MapLocation end) {
        return start.add(dir).distanceSquaredTo(end) < start.distanceSquaredTo(end);
    }

    static boolean isDiagonal(Direction dir) {
        return dir.dx * dir.dy != 0;
    }

    static boolean evenSquare(MapLocation loc) {
        return ((loc.x + loc.y) & 1) != 0;
    }
}
