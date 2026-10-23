package com.deskink.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import com.deskink.core.PenInput;
import java.util.*;

/** Presentation only. The drawing viewport never extends behind the controls. */
@android.annotation.SuppressLint("ViewConstructor") // Requires its Activity action; never XML-inflated.
final class WorkspaceView extends LinearLayout {
    final TextureView video;
    final InkCanvas ink;
    private final Activity activity;
    private final DeskInkUi ui;
    private final TextView status,detail;
    private final View welcome;
    private final LinearLayout connection;
    private final ImageView connectionIcon;
    private final Map<PenInput.Tool,ToolButton> tools=new EnumMap<>(PenInput.Tool.class);
    private final List<ToolButton> controls=new ArrayList<>();
    private final ToolButton undo,redo,visibility,color,width,fit,shape;
    private int selectedColor;
    private float selectedWidth=4;
    private float selectedEraserRadius=12;
    private final android.content.SharedPreferences drawingPreferences;
    private boolean visible=true;
    private AlertDialog sheet;
    private static final int[][] COLORS={{255,80,40,255},{245,45,65,255},{255,210,35,255},{25,190,100,255},{35,125,255,255},{255,255,255,255},{25,30,40,255},{20,190,200,255},{155,90,235,255},{245,120,180,255},{145,150,160,255},{155,100,60,255}};

    WorkspaceView(Activity activity,Runnable pair,Runnable network) {
        super(activity); this.activity=activity; ui=new DeskInkUi(activity);
        drawingPreferences=activity.getSharedPreferences("drawing-tools",0);
        float savedRadius=drawingPreferences.getFloat("eraser-radius",12);
        selectedEraserRadius=savedRadius==24||savedRadius==48?savedRadius:12;
        setOrientation(VERTICAL); setBackgroundColor(DeskInkUi.CANVAS);
        LinearLayout header=ui.row(); header.setPadding(ui.dp(14),ui.dp(4),ui.dp(12),ui.dp(4));
        connection=ui.row(); connection.setTag("pair"); connection.setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6));
        connection.setBackground(ui.controlBackground(DeskInkUi.SURFACE,20)); connection.setMinimumHeight(ui.dp(48));
        connection.setFocusable(true); connection.setOnClickListener(v -> pair.run());
        connectionIcon=ui.icon(R.drawable.ic_usb,DeskInkUi.MUTED,20); connection.addView(connectionIcon);
        LinearLayout labels=ui.column(); status=ui.text(R.string.app_name,13,DeskInkUi.TEXT,true);
        detail=ui.text(R.string.waiting,11,DeskInkUi.MUTED,false);
        for(TextView text:new TextView[]{status,detail}) { text.setSingleLine(); text.setEllipsize(TextUtils.TruncateAt.END); labels.addView(text); }
        LayoutParams labelsParams=new LayoutParams(0,-2,1); labelsParams.setMarginStart(ui.dp(10)); connection.addView(labels,labelsParams);
        FrameLayout connectionArea=new FrameLayout(activity);
        connectionArea.addView(ui.limited(connection,300),new FrameLayout.LayoutParams(-1,-2));
        header.addView(connectionArea,new LayoutParams(0,-2,1));
        ToolButton lan=new ToolButton(activity,R.string.network_short,R.string.network,R.drawable.ic_wifi,"network",network);
        lan.compact(); header.addView(lan,new LayoutParams(ui.dp(56),ui.dp(48)));
        ink=new InkCanvas(activity); ink.setTag("ink");
        ink.eraserRadius(selectedEraserRadius);
        ink.shapeCorrection(drawingPreferences.getBoolean("shape-correction",true));
        fit=new ToolButton(activity,R.string.fit_short,R.string.fit,R.drawable.ic_fit,"fit",ink::resetZoom);
        fit.compact();
        header.addView(fit,new LayoutParams(ui.dp(56),ui.dp(48)));
        ToolButton help=new ToolButton(activity,R.string.help_short,R.string.help,R.drawable.ic_help,"help",this::help);
        help.compact();
        header.addView(help,new LayoutParams(ui.dp(56),ui.dp(48)));
        addView(header,new LayoutParams(-1,-2));

        FrameLayout workspace=new FrameLayout(activity); workspace.setTag("workspace");
        video=new TextureView(activity); video.setTag("video"); video.setOpaque(false);
        workspace.addView(video,new FrameLayout.LayoutParams(-1,-1)); workspace.addView(ink,new FrameLayout.LayoutParams(-1,-1));
        welcome=welcome(pair,network); workspace.addView(welcome,new FrameLayout.LayoutParams(-1,-1));
        addView(workspace,new LayoutParams(-1,0,1));

        LinearLayout dock=ui.row(); dock.setTag("toolbar"); dock.setGravity(Gravity.CENTER); dock.setPadding(ui.dp(6),ui.dp(6),ui.dp(6),ui.dp(6));
        int[] names={R.string.pen,R.string.highlighter,R.string.eraser,R.string.laser,R.string.fade_short,R.string.lasso};
        int[] descriptions={R.string.pen,R.string.highlighter,R.string.eraser,R.string.laser,R.string.vanishing,R.string.lasso_hint};
        int[] icons={R.drawable.ic_pen,R.drawable.ic_highlighter,R.drawable.ic_eraser,R.drawable.ic_laser,R.drawable.ic_fade,R.drawable.ic_lasso};
        PenInput.Tool[] values=PenInput.Tool.values();
        for(int i=0;i<values.length;i++) {
            PenInput.Tool tool=values[i]; ToolButton button=tool(dock,names[i],descriptions[i],icons[i],"tool."+tool,() -> ink.select(tool)); tools.put(tool,button);
        }
        shape=tool(dock,R.string.shape_short,R.string.shape_hint,R.drawable.ic_shapes,"shape",() -> {
            ink.shapeCorrection(!ink.shapeCorrection()); drawingPreferences.edit().putBoolean("shape-correction",ink.shapeCorrection()).apply(); refreshControls();
        });
        divider(dock);
        color=tool(dock,R.string.color,R.string.color,R.drawable.ic_pen,"color",this::colors); color.swatch(colorValue(0));
        width=tool(dock,R.string.width,R.string.width,R.drawable.ic_pen,"width",this::widths); width.thickness(selectedWidth);
        divider(dock);
        undo=tool(dock,R.string.undo_short,R.string.undo,R.drawable.ic_undo,"undo",() -> ink.command("undo"));
        redo=tool(dock,R.string.redo_short,R.string.redo,R.drawable.ic_redo,"redo",() -> ink.command("redo"));
        visibility=tool(dock,R.string.hide_short,R.string.hide,R.drawable.ic_eye,"hide",() -> ink.visible(!ink.inkVisible()));
        tool(dock,R.string.clear_short,R.string.clear,R.drawable.ic_clear,"clear",() -> ink.command("clear"));
        HorizontalScrollView scroll=new HorizontalScrollView(activity); scroll.setTag("toolbar.scroll");
        scroll.setHorizontalScrollBarEnabled(false); scroll.setFillViewport(true); scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setBackground(ui.surface(DeskInkUi.SURFACE,22,DeskInkUi.BORDER)); scroll.setClipToOutline(true);
        scroll.addView(dock,new FrameLayout.LayoutParams(-2,-2));
        FrameLayout footer=new FrameLayout(activity); footer.setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(8));
        footer.addView(ui.limited(scroll,720),new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_HORIZONTAL)); addView(footer);
        ink.stateChanged=this::refreshControls; refreshControls();
        // Preserve camera/caption-bar space in tablet desktop and multi-window modes.
        setOnApplyWindowInsetsListener((view,insets) -> {
            int left=0,top=0,right=0,bottom=0;
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.displayCutout()|WindowInsets.Type.systemBars());
                left=safe.left; top=safe.top; right=safe.right; bottom=safe.bottom;
            } else if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null) {
                DisplayCutout cutout=insets.getDisplayCutout(); left=cutout.getSafeInsetLeft(); top=cutout.getSafeInsetTop();
                right=cutout.getSafeInsetRight(); bottom=cutout.getSafeInsetBottom();
            }
            setPadding(left,top,right,bottom); return insets;
        });
        showConnection(activity.getString(R.string.app_name),activity.getString(R.string.waiting),false);
    }
    private ToolButton tool(LinearLayout dock,int label,int description,int icon,String tag,Runnable action) {
        ToolButton button=new ToolButton(activity,label,description,icon,tag,action);
        controls.add(button); dock.addView(button,new LayoutParams(-2,-2)); return button;
    }
    private void divider(LinearLayout dock) {
        View line=new View(activity); line.setBackgroundColor(DeskInkUi.BORDER);
        LayoutParams lp=new LayoutParams(ui.dp(1),ui.dp(28)); lp.setMargins(ui.dp(6),0,ui.dp(6),0); dock.addView(line,lp);
    }
    private View welcome(Runnable pair,Runnable network) {
        ScrollView scroll=new ScrollView(activity); scroll.setTag("welcome"); scroll.setFillViewport(true);
        scroll.setBackgroundColor(DeskInkUi.CANVAS); scroll.setVerticalScrollBarEnabled(false);
        FrameLayout center=new FrameLayout(activity); center.setPadding(ui.dp(28),ui.dp(28),ui.dp(28),ui.dp(24));
        LinearLayout content=ui.column();
        TextView wordmark=ui.text("DeskInk",14,DeskInkUi.ACCENT,true); wordmark.setLetterSpacing(.05f); content.addView(wordmark);
        content.addView(ui.space(16));
        TextView title=ui.text(R.string.welcome_title,32,DeskInkUi.TEXT,true); title.setLineSpacing(ui.dp(3),1); content.addView(title);
        content.addView(ui.space(14)); TextView intro=ui.text(R.string.welcome_detail,15,DeskInkUi.MUTED,false); intro.setLineSpacing(ui.dp(5),1); content.addView(intro);
        content.addView(ui.space(28));
        LinearLayout steps=ui.column(); steps.setPadding(ui.dp(18),ui.dp(4),ui.dp(18),ui.dp(4)); steps.setBackground(ui.surface(DeskInkUi.SURFACE,20,DeskInkUi.BORDER));
        step(steps,"1",R.string.setup_usb,R.string.setup_usb_detail);
        step(steps,"2",R.string.setup_pc,R.string.setup_pc_detail);
        step(steps,"3",R.string.setup_pair,R.string.setup_pair_detail);
        content.addView(steps); content.addView(ui.space(20));
        Button button=ui.action(R.string.pair,true,pair); button.setTag("welcome.pair");
        content.addView(button,new LayoutParams(-1,-2)); content.addView(ui.space(10));
        Button wireless=ui.action(R.string.network,false,network); wireless.setTag("welcome.network");
        content.addView(wireless,new LayoutParams(-1,-2)); content.addView(ui.space(20));
        hint(content,R.drawable.ic_zoom,R.string.gesture_zoom); content.addView(ui.space(10)); hint(content,R.drawable.ic_button,R.string.gesture_erase);
        center.addView(ui.limited(content,500),new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        scroll.addView(center,new ScrollView.LayoutParams(-1,-1)); return scroll;
    }
    private void step(LinearLayout parent,String number,int title,int description) {
        LinearLayout row=ui.row(); row.setPadding(0,ui.dp(14),0,ui.dp(14)); row.setGravity(Gravity.TOP);
        TextView badge=ui.text(number,12,DeskInkUi.ACCENT,true); badge.setGravity(Gravity.CENTER); badge.setBackground(ui.surface(DeskInkUi.SELECTED,11,Color.TRANSPARENT));
        row.addView(badge,new LayoutParams(ui.dp(30),ui.dp(30)));
        LinearLayout labels=ui.column(); labels.addView(ui.text(title,14,DeskInkUi.TEXT,true)); labels.addView(ui.space(5));
        TextView text=ui.text(description,12,DeskInkUi.MUTED,false); text.setLineSpacing(ui.dp(3),1); labels.addView(text);
        LayoutParams lp=new LayoutParams(0,-2,1); lp.setMarginStart(ui.dp(14)); row.addView(labels,lp); parent.addView(row);
    }
    private void hint(LinearLayout parent,int icon,int text) {
        LinearLayout row=ui.row(); row.addView(ui.icon(icon,DeskInkUi.MUTED,18));
        LayoutParams lp=new LayoutParams(-2,-2); lp.setMarginStart(ui.dp(10)); row.addView(ui.text(text,12,DeskInkUi.MUTED,false),lp); parent.addView(row);
    }
    void showConnection(String title,String subtitle,boolean connected) {
        status.setText(title); detail.setText(subtitle); connectionIcon.setImageTintList(ColorStateList.valueOf(connected?DeskInkUi.ACCENT:DeskInkUi.MUTED));
        connection.setClickable(!connected); connection.setFocusable(!connected);
        connection.setContentDescription(connected?activity.getString(R.string.connected_accessibility,title):activity.getString(R.string.pair));
        connection.setTooltipText(connected?title:activity.getString(R.string.pair));
        welcome.setVisibility(connected?GONE:VISIBLE); video.setVisibility(connected?VISIBLE:INVISIBLE); refreshControls();
        if(!connected) closeSheet();
    }
    private void refreshControls() {
        boolean connected=ink.connected();
        for(ToolButton button:controls) button.setEnabled(connected);
        tools.get(PenInput.Tool.LASSO).setEnabled(connected&&ink.editingEnabled()); shape.setEnabled(connected&&ink.editingEnabled()); shape.setSelected(ink.shapeCorrection());
        shape.setContentDescription(activity.getString(ink.editingEnabled()?R.string.shape_hint:R.string.editing_update_required));
        fit.setEnabled(connected); undo.setEnabled(connected&&ink.canUndo()); redo.setEnabled(connected&&ink.canRedo());
        for(Map.Entry<PenInput.Tool,ToolButton> entry:tools.entrySet()) entry.getValue().setSelected(entry.getKey()==ink.tool());
        if(visible!=ink.inkVisible()) {
            visible=ink.inkVisible(); visibility.label(visible?R.string.hide_short:R.string.show_short,visible?R.string.hide:R.string.show,visible?R.drawable.ic_eye:R.drawable.ic_eye_off);
        }
        color.setContentDescription(activity.getString(R.string.color_current,getResources().getStringArray(R.array.colors)[selectedColor]));
        width.setContentDescription(activity.getString(R.string.width_current,activity.getString(widthName(selectedWidth))));
    }
    private static int colorValue(int index) { int[] color=COLORS[index]; return Color.rgb(color[0],color[1],color[2]); }
    private static int widthName(float value) { return value==2?R.string.thin:value==8?R.string.thick:R.string.medium; }
    private void colors() {
        LinearLayout content=ui.sheetContent(R.string.color,R.drawable.ic_pen); content.addView(ui.text(R.string.color_hint,13,DeskInkUi.MUTED,false)); content.addView(ui.space(18));
        String[] names=getResources().getStringArray(R.array.colors);
        // Four columns remain reachable in narrow multi-window mode.
        for(int start=0;start<COLORS.length;start+=4) {
            LinearLayout row=ui.row();
            for(int i=start;i<Math.min(start+4,COLORS.length);i++) {
                final int index=i; LinearLayout choice=ui.column(); choice.setGravity(Gravity.CENTER); choice.setMinimumHeight(ui.dp(70));
                choice.setTag("color."+i); choice.setFocusable(true); choice.setClickable(true); choice.setSelected(i==selectedColor);
                choice.setContentDescription(names[i]); choice.setBackground(ui.controlBackground(Color.TRANSPARENT,14));
                FrameLayout swatch=new FrameLayout(activity); GradientDrawable circle=ui.surface(colorValue(i),16,0xffa6acb9); swatch.setBackground(circle);
                if(i==selectedColor) { ImageView check=ui.icon(R.drawable.ic_check,i==5||i==2?DeskInkUi.CANVAS:Color.WHITE,20); swatch.addView(check,new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER)); }
                choice.addView(swatch,new LayoutParams(ui.dp(30),ui.dp(30))); TextView name=ui.text(names[i],11,DeskInkUi.TEXT,false); name.setGravity(Gravity.CENTER); name.setPadding(0,ui.dp(6),0,0); name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); choice.addView(name);
                choice.setOnClickListener(v -> { selectedColor=index; ink.color(COLORS[index]); color.swatch(colorValue(index)); refreshControls(); closeSheet(); });
                row.addView(choice,new LayoutParams(0,-2,1));
            }
            for(int i=Math.min(start+4,COLORS.length);i<start+4;i++) row.addView(new View(activity),new LayoutParams(0,ui.dp(1),1));
            content.addView(row);
        }
        showSheet(content);
    }
    private void widths() {
        LinearLayout content=ui.sheetContent(R.string.width,R.drawable.ic_pen); content.addView(ui.text(R.string.width_hint,13,DeskInkUi.MUTED,false)); content.addView(ui.space(16));
        for(float value:new float[]{2,4,8}) {
            LinearLayout choice=ui.row(); choice.setTag("width."+(int)value); choice.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));
            choice.setMinimumHeight(ui.dp(58)); choice.setBackground(ui.controlBackground(Color.TRANSPARENT,14)); choice.setSelected(value==selectedWidth);
            choice.setFocusable(true); choice.setClickable(true); choice.setContentDescription(activity.getString(widthName(value)));
            choice.addView(ui.text(widthName(value),14,DeskInkUi.TEXT,true),new LayoutParams(0,-2,1));
            View line=new View(activity); line.setBackground(ui.surface(DeskInkUi.TEXT,4,Color.TRANSPARENT)); choice.addView(line,new LayoutParams(ui.dp(92),ui.dp(value)));
            ImageView check=ui.icon(R.drawable.ic_check,DeskInkUi.ACCENT,20); check.setVisibility(value==selectedWidth?VISIBLE:INVISIBLE);
            LayoutParams lp=new LayoutParams(ui.dp(20),ui.dp(20)); lp.setMarginStart(ui.dp(22)); choice.addView(check,lp);
            choice.setOnClickListener(v -> { selectedWidth=value; ink.width(value); width.thickness(value); refreshControls(); closeSheet(); }); content.addView(choice);
        }
        content.addView(ui.space(16)); content.addView(ui.text(R.string.eraser_size,15,DeskInkUi.TEXT,true));
        content.addView(ui.text(R.string.eraser_hint,12,DeskInkUi.MUTED,false)); content.addView(ui.space(8));
        LinearLayout sizes=ui.row();
        float[] radii={12,24,48}; int[] labels={R.string.small,R.string.medium,R.string.large};
        for(int i=0;i<radii.length;i++) {
            float value=radii[i]; LinearLayout choice=ui.column(); choice.setGravity(Gravity.CENTER);
            choice.setTag("eraser.size."+(int)value); choice.setMinimumHeight(ui.dp(86)); choice.setFocusable(true); choice.setClickable(true);
            choice.setSelected(value==selectedEraserRadius); choice.setContentDescription(activity.getString(R.string.eraser_size)+": "+activity.getString(labels[i]));
            choice.setBackground(ui.controlBackground(value==selectedEraserRadius?DeskInkUi.SELECTED:Color.TRANSPARENT,14));
            FrameLayout preview=new FrameLayout(activity); View circle=new View(activity);
            circle.setBackground(ui.surface(DeskInkUi.SELECTED,24,DeskInkUi.ACCENT));
            int diameter=ui.dp(value/2+8); preview.addView(circle,new FrameLayout.LayoutParams(diameter,diameter,Gravity.CENTER));
            choice.addView(preview,new LayoutParams(-1,ui.dp(40))); choice.addView(ui.text(labels[i],13,DeskInkUi.TEXT,true));
            choice.setOnClickListener(v -> { selectedEraserRadius=value; ink.eraserRadius(value); drawingPreferences.edit().putFloat("eraser-radius",value).apply(); closeSheet(); });
            sizes.addView(choice,new LayoutParams(0,-2,1));
        }
        content.addView(sizes);
        showSheet(content);
    }
    private void help() {
        LinearLayout content=ui.sheetContent(R.string.help,R.drawable.ic_usb); TextView text=ui.text(R.string.usb_help,14,DeskInkUi.MUTED,false); text.setLineSpacing(ui.dp(5),1);
        content.addView(text); showSheet(content);
    }
    private void showSheet(View content) {
        closeSheet(); sheet=new AlertDialog.Builder(activity).setView(ui.scroll(content)).setNegativeButton(android.R.string.ok,null).create(); sheet.show(); ui.finishSheet(sheet);
    }
    void closeSheet() { if(sheet!=null) { sheet.dismiss(); sheet=null; } }
}
