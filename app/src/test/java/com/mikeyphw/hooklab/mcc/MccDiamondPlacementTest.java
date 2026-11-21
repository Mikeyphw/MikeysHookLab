package com.mikeyphw.hooklab.mcc;

import org.junit.Test;
import static org.junit.Assert.*;

public class MccDiamondPlacementTest {
    @Test public void fallbackOnMissingSemanticNode() {
        assertArrayEquals(new int[]{240,68},MccDiamondPlacement.position(320,600,72,40,8,68,null));
    }
    @Test public void preferLeftOfDiamondWithoutOverlap() {
        assertArrayEquals(new int[]{120,80},MccDiamondPlacement.position(320,600,72,40,8,68,new int[]{200,80,250,120}));
    }
    @Test public void useRightIfLeftTooNarrow() {
        assertArrayEquals(new int[]{68,80},MccDiamondPlacement.position(320,600,72,40,8,68,new int[]{5,80,60,120}));
    }
    @Test public void clampOutOfBoundsCoordinate() {
        assertArrayEquals(new int[]{240,8},MccDiamondPlacement.position(320,600,72,40,8,68,new int[]{400,-200,500,-150}));
    }
    @Test public void tinyViewNeverGeneratesNegativePosition() {
        assertArrayEquals(new int[]{8,8},MccDiamondPlacement.position(20,20,72,40,8,68,null));
    }
}
