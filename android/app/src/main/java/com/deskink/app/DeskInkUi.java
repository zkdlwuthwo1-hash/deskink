package com.deskink.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Small native UI primitives shared by the workspace and its dialogs. */
final class DeskInkUi {
    static final int CANVAS=0xff0d0f13, SURFACE=0xff1b1e25, RAISED=0xff242832;
    static final int BORDER=0xff363c48, TEXT=0xfff3f4f7, MUTED=0xffa6acb9;
    static final int ACCENT=0xff8ab4ff, SELECTED=0xff293b58, DANGER=0xffffa4a4;
    private final Context context;

    DeskInkUi(Context context) { this.context=context; }
    int dp(float n) { return Math.round(n*context.getResources().getDisplayMetrics().density); }
    GradientDrawable surface(int color,int radius,int border) {
        GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius));
        if(border!=Color.TRANSPARENT) shape.setStroke(dp(1),border);
        return shape;
    }
    RippleDrawable controlBackground(int fill,int radius) {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected},surface(SELECTED,radius,Color.TRANSPARENT));
        states.addState(new int[]{android.R.attr.state_focused},surface(SELECTED,radius,ACCENT));
        states.addState(new int[]{},surface(fill,radius,Color.TRANSPARENT));
        return new RippleDrawable(ColorStateList.valueOf(0x339dbdff),states,surface(Color.WHITE,radius,Color.TRANSPARENT));
    }
    TextView text(CharSequence value,float size,int color,boolean medium) {
        TextView text=new TextView(context); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(context.getResources().getFont(medium?R.font.geist_medium:R.font.geist_regular));
        text.setIncludeFontPadding(false); return text;
    }
    TextView text(int value,float size,int color,boolean medium) { return text(context.getString(value),size,color,medium); }
    ImageView icon(int resource,int color,int size) {
        ImageView image=new ImageView(context); image.setImageResource(resource); image.setImageTintList(ColorStateList.valueOf(color));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size))); return image;
    }
    LinearLayout column() { LinearLayout layout=new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    LinearLayout row() { LinearLayout layout=new LinearLayout(context); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    ScrollView scroll(View content) {
        ScrollView scroll=new ScrollView(context); scroll.setClipToPadding(false); scroll.addView(content); return scroll;
    }
    View space(int height) { View view=new View(context); view.setLayoutParams(new LinearLayout.LayoutParams(1,dp(height))); return view; }
    Button action(int label,boolean primary,Runnable action) {
        Button button=new Button(context); button.setText(label); button.setTextSize(14); button.setAllCaps(false);
        button.setTypeface(context.getResources().getFont(R.font.geist_medium)); button.setTextColor(primary?CANVAS:TEXT);
        button.setBackground(controlBackground(primary?ACCENT:RAISED,14)); button.setStateListAnimator(null);
        button.setPadding(dp(20),dp(12),dp(20),dp(12)); button.setMinHeight(dp(48));
        button.setOnClickListener(v -> action.run()); return button;
    }
    FrameLayout limited(View child,int maxWidth) {
        FrameLayout frame=new FrameLayout(context) {
            @Override protected void onMeasure(int width,int height) {
                int size=MeasureSpec.getSize(width);
                if(MeasureSpec.getMode(width)==MeasureSpec.UNSPECIFIED) size=dp(maxWidth);
                super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(size,dp(maxWidth)),MeasureSpec.getMode(width)),height);
            }
        };
        frame.addView(child,new FrameLayout.LayoutParams(-1,-2)); return frame;
    }
    LinearLayout sheetContent(int title,int symbol) {
        LinearLayout content=column(); content.setPadding(dp(24),dp(24),dp(24),dp(8));
        LinearLayout heading=row(); heading.addView(icon(symbol,ACCENT,24));
        TextView label=text(title,20,TEXT,true); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMarginStart(dp(12));
        heading.addView(label,lp); content.addView(heading); content.addView(space(20)); return content;
    }
    void finishSheet(AlertDialog dialog) {
        dialog.getWindow().setBackgroundDrawable(surface(SURFACE,24,BORDER));
        int available=context.getResources().getDisplayMetrics().widthPixels-dp(32);
        dialog.getWindow().setLayout(Math.min(dp(440),available),-2);
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE}) {
            Button button=dialog.getButton(which);
            if(button!=null) { button.setAllCaps(false); button.setTextColor(ACCENT); button.setMinHeight(dp(48)); }
        }
    }
}
