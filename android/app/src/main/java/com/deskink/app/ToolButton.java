package com.deskink.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** Programmatically constructed, never XML-inflated. One accessible, pen-sized target. */
@android.annotation.SuppressLint("ViewConstructor")
final class ToolButton extends LinearLayout {
    private final DeskInkUi ui;
    private final ImageView image;
    private final TextView caption;

    ToolButton(Context context,int label,int description,int icon,String tag,Runnable action) {
        super(context); ui=new DeskInkUi(context); setTag(tag);
        setOrientation(VERTICAL); setGravity(Gravity.CENTER); setPadding(ui.dp(7),ui.dp(5),ui.dp(7),ui.dp(5));
        setMinimumWidth(ui.dp(56)); setMinimumHeight(ui.dp(58)); setFocusable(true); setClickable(true);
        setBackground(ui.controlBackground(Color.TRANSPARENT,12));
        image=ui.icon(icon,DeskInkUi.TEXT,23); addView(image);
        caption=ui.text(label,11,DeskInkUi.TEXT,false); caption.setGravity(Gravity.CENTER); caption.setSingleLine(true);
        caption.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams lp=new LayoutParams(-2,-2); lp.topMargin=ui.dp(5); addView(caption,lp);
        describe(description); setOnClickListener(v -> action.run());
    }
    void describe(int description) { setContentDescription(getContext().getString(description)); setTooltipText(getContentDescription()); }
    void label(int label,int description,int icon) {
        caption.setText(label); image.setImageResource(icon); describe(description);
    }
    void swatch(int color) {
        GradientDrawable shape=ui.surface(color,12,0xffc6c9d2); image.setImageTintList(null); image.setImageDrawable(shape);
    }
    void thickness(float value) {
        GradientDrawable line=ui.surface(DeskInkUi.TEXT,4,Color.TRANSPARENT);
        line.setSize(ui.dp(23),ui.dp(value)); image.setImageTintList(null); image.setImageDrawable(line);
        image.setScaleType(ImageView.ScaleType.CENTER);
    }
    @Override public void setSelected(boolean selected) {
        if(isSelected()==selected) return;
        super.setSelected(selected);
        if(caption!=null) { caption.setTextColor(selected?DeskInkUi.ACCENT:DeskInkUi.TEXT); image.setImageTintList(ColorStateList.valueOf(selected?DeskInkUi.ACCENT:DeskInkUi.TEXT)); }
    }
    @Override public void setEnabled(boolean enabled) {
        if(isEnabled()==enabled) return;
        super.setEnabled(enabled); setAlpha(enabled?1f:.34f);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setClassName(Button.class.getName()); info.setSelected(isSelected());
    }
}
