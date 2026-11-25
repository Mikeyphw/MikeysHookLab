package com.mikeyphw.hooklab.ui;

import static org.junit.Assert.*;
import org.junit.Test;

public final class UiLayoutPolicyTest {
    @Test public void phoneAndTabletThreshold() {
        for (int width : new int[] {320, 360, 480, 600, 759})
            assertFalse(UiLayoutPolicy.useRail(width));
        for (int width : new int[] {760, 761, 1024, 1200})
            assertTrue(UiLayoutPolicy.useRail(width));
    }
    @Test public void contentNeverOverflowsAvailableWidth() {
        for (int width : new int[] {320, 360, 600, 839, 840, 900, 1200, 2000}) {
            int available = width - (UiLayoutPolicy.useRail(width) ? UiLayoutPolicy.RAIL_WIDTH_DP : 0);
            assertTrue(UiLayoutPolicy.contentWidthDp(width,1f) <= available);
            assertTrue(UiLayoutPolicy.contentWidthDp(width,1f) <= UiLayoutPolicy.MAX_CONTENT_WIDTH_DP
                || !UiLayoutPolicy.useRail(width));
        }
    }
    @Test public void compactOrLargeTextStacksActions() {
        for(int width:new int[]{320,360,390,393,600,840}) {
            assertTrue(UiLayoutPolicy.stackActions(width,1.6f));
            assertTrue(UiLayoutPolicy.stackActions(width,2f));
            assertEquals(width<360,UiLayoutPolicy.stackActions(width,1f));
        }
    }
    @Test public void adaptiveRailKeepsMoreSpaceForLargeLabels(){
        assertEquals(88,UiLayoutPolicy.railWidthDp(1f));assertEquals(112,UiLayoutPolicy.railWidthDp(1.6f));assertEquals(144,UiLayoutPolicy.railWidthDp(2f));
        assertTrue(840-UiLayoutPolicy.railWidthDp(2f)>600);
        for(int width:new int[]{600,760,840,1100,2000})for(float scale:new float[]{1f,1.6f,2f}){
            int available=width-(UiLayoutPolicy.useRail(width)?UiLayoutPolicy.railWidthDp(scale):0);
            int expected=UiLayoutPolicy.useRail(width)?Math.min(900,available):available;
            assertEquals(expected,UiLayoutPolicy.contentWidthDp(width,scale));
        }
        for(boolean rail:new boolean[]{false,true})for(boolean ime:new boolean[]{false,true}){
            assertFalse(UiLayoutPolicy.showNavigation(true,rail,ime));
            assertEquals(rail||!ime,UiLayoutPolicy.showNavigation(false,rail,ime));
        }
    }
    @Test public void guttersStayReadable() {
        assertEquals(12, UiLayoutPolicy.contentInsetDp(320));
        assertEquals(20, UiLayoutPolicy.contentInsetDp(390));
        assertEquals(20, UiLayoutPolicy.contentInsetDp(600));
        assertEquals(32, UiLayoutPolicy.contentInsetDp(760));
    }
}
