package dev.livelyeyes.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GazeMathTest {
    @Test
    void facingSouthTheCharactersRightIsWest() {
        // yaw 0 faces +Z (south); the character's right hand points to -X (west)
        assertEquals(0, GazeMath.gaze(0, 0, 0, 0, 5));
        assertEquals(-1, GazeMath.gaze(0, 0, 0, -3, 5), "target on the character's right: texture left");
        assertEquals(1, GazeMath.gaze(0, 0, 0, 3, 5), "target on the character's left: texture right");
    }

    @Test
    void behindTheHeadIsIgnored() {
        assertEquals(ExpressionController.NO_TARGET, GazeMath.gaze(0, 0, 0, 0, -5));
        assertEquals(ExpressionController.NO_TARGET, GazeMath.gaze(0, 0, 0, 5, 0));
    }

    @Test
    void followsTheYaw() {
        // yaw 90 faces -X (west); its right is -Z (north)
        assertEquals(0, GazeMath.gaze(90, 0, 0, -5, 0));
        assertEquals(-1, GazeMath.gaze(90, 0, 0, -5, -3));
        assertEquals(1, GazeMath.gaze(90, 0, 0, -5, 3));
    }
}
