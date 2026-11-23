package com.mikeyphw.hooklab.ui;

/** Deterministic source-side layout/accessibility thresholds.
 * Real TalkBack, font-scale and contrast qualification still require a device.
 */
public final class Ui08QualityPolicy {
    private Ui08QualityPolicy() { }
    public static final int MIN_TOUCH_DP=48;
    public static final int MIN_NORMAL_TEXT_CONTRAST_X10=45;
    public static int contentWidth(int dp) {return UiLayoutPolicy.contentWidthDp(dp);}
    public static boolean isTouchTargetLargeEnough(int widthDp,int heightDp){
        return widthDp>=MIN_TOUCH_DP && heightDp>=MIN_TOUCH_DP;
    }
    private static double srgb(int channel){
        if(channel<0||channel>255)throw new IllegalArgumentException("Invalid color channel");
        double c=channel/255.0;
        return c<=0.04045?c/12.92:Math.pow((c+0.055)/1.055,2.4);
    }
    public static double luminance(int rgb){
        return 0.2126*srgb((rgb>>16)&255)+0.7152*srgb((rgb>>8)&255)+
                0.0722*srgb(rgb&255);
    }
    public static double contrast(int foreground,int background){
        double a=luminance(foreground),b=luminance(background);
        return (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05);
    }
    public static boolean readableNormalText(int fg,int bg){
        return contrast(fg,bg)>=MIN_NORMAL_TEXT_CONTRAST_X10/10.0;
    }
}
