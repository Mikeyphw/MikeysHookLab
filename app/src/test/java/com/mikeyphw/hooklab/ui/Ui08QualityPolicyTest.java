package com.mikeyphw.hooklab.ui;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Ui08QualityPolicyTest {
    @Test public void touchTargetsAreAtLeast48Dp(){
        assertTrue(Ui08QualityPolicy.isTouchTargetLargeEnough(48,48));
        assertFalse(Ui08QualityPolicy.isTouchTargetLargeEnough(47,48));
        assertFalse(Ui08QualityPolicy.isTouchTargetLargeEnough(48,47));
    }
    @Test public void layoutBoundsRemainValidAtRequiredBreakpoints(){
        for(int dp:new int[]{320,360,600,839,840,1024,1920}){
            int content=Ui08QualityPolicy.contentWidth(dp);
            assertTrue(content>0);assertTrue(content<=dp);
        }
        assertEquals(320,Ui08QualityPolicy.contentWidth(320));
        assertEquals(760,Ui08QualityPolicy.contentWidth(840));
        assertTrue(UiLayoutPolicy.useRail(840));
        assertFalse(UiLayoutPolicy.useRail(839));
    }
    @Test public void actualBundledDayAndNightTextContrastsExceed45(){
        assertTrue(Ui08QualityPolicy.readableNormalText(0x182321,0xf4f6f3));
        assertTrue(Ui08QualityPolicy.readableNormalText(0x546660,0xf4f6f3));
        assertTrue(Ui08QualityPolicy.readableNormalText(0xf0f4f1,0x0d1214));
        assertTrue(Ui08QualityPolicy.readableNormalText(0x9ca9a7,0x0d1214));
    }
    @Test public void whiteOnWhiteIsNotClaimedAccessible(){
        assertFalse(Ui08QualityPolicy.readableNormalText(0xffffff,0xffffff));
        assertEquals(1.0,Ui08QualityPolicy.contrast(0,0),0.00001);
    }
    @Test public void knownWhiteBlackContrastIs21(){
        assertEquals(21.0,Ui08QualityPolicy.contrast(0xffffff,0x000000),0.00001);
    }
}
