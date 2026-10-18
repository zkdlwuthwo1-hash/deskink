package com.deskink.core;

/** One transform for video, ink and inverse pen coordinates. */
public final class Viewport {
    private float width=1,height=1,desktopWidth=1,desktopHeight=1,fit=1,zoom=1,x,y;
    public void resize(float w,float h,float dw,float dh) {
        width=Math.max(1,w); height=Math.max(1,h); desktopWidth=Math.max(1,dw); desktopHeight=Math.max(1,dh);
        fit=Math.min(width/desktopWidth,height/desktopHeight); reset();
    }
    public void reset() { zoom=1; x=(width-desktopWidth*fit)/2; y=(height-desktopHeight*fit)/2; }
    public float scale() { return fit*zoom; }
    public float zoom() { return zoom; }
    public float x() { return x; }
    public float y() { return y; }
    public float[] desktop(float px,float py) { return new float[]{(px-x)/scale(),(py-y)/scale()}; }
    public boolean contains(float px,float py) { float[] p=desktop(px,py); return p[0]>=0&&p[0]<=desktopWidth&&p[1]>=0&&p[1]<=desktopHeight; }
    public void gesture(float ratio,float oldX,float oldY,float newX,float newY) {
        if(!Float.isFinite(ratio)||ratio<=0) return;
        float[] anchor=desktop(oldX,oldY); zoom=Math.max(1,Math.min(4,zoom*ratio));
        x=newX-anchor[0]*scale(); y=newY-anchor[1]*scale();
        x=clampOffset(x,width-desktopWidth*scale()); y=clampOffset(y,height-desktopHeight*scale());
    }
    private static float clampOffset(float value,float spare) { return Math.max(Math.min(0,spare),Math.min(Math.max(0,spare),value)); }
}
