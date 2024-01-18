package cretplayer1_1;

import battlecode.common.MapLocation;

enum Type {
    NONE,MOVE,ATTACK,HEAL,PICKUP_FLAG,BUILD_BOMB,FILL
}

public class Task {

    Task(double value, Type type, MapLocation loc) {
        this.value = value;
        this.type = type;
        this.location = loc;
    }

    Task(Type type, MapLocation loc) {
        this.type = type;
        this.location = loc;
    }
    double value = 0;
    Type type;
    MapLocation location;
}

