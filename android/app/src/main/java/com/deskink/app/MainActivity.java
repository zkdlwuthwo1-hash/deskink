package com.deskink.app;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import com.deskink.core.*;
import java.util.*;

public final class MainActivity extends Activity {
    private InkCanvas ink;
    private TextureView video;
    private TextView status;
    private LinearLayout welcome;
    private final Map<PenInput.Tool,Button> toolButtons=new EnumMap<>(PenInput.Tool.class);
    private boolean inkVisible=true;
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        FrameLayout workspace=new FrameLayout(this);
        video=new TextureView(this); video.setOpaque(true); workspace.addView(video,new FrameLayout.LayoutParams(-1,-1));
        ink=new InkCanvas(this); ink.setTag("ink"); workspace.addView(ink,new FrameLayout.LayoutParams(-1,-1));
        welcome=new LinearLayout(this); welcome.setGravity(Gravity.CENTER); welcome.setOrientation(LinearLayout.VERTICAL); welcome.setPadding(dp(24),0,dp(24),0);
        TextView title=new TextView(this); title.setText(R.string.welcome); title.setTextSize(25); title.setTextColor(Color.WHITE); title.setGravity(Gravity.CENTER); welcome.addView(title);
        TextView hint=new TextView(this); hint.setText(R.string.connect_hint); hint.setTextSize(15); hint.setTextColor(0xffa9b3c4); hint.setGravity(Gravity.CENTER); hint.setPadding(0,dp(20),0,0); welcome.addView(hint);
        workspace.addView(welcome,new FrameLayout.LayoutParams(-1,-1)); root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout connection=new LinearLayout(this); connection.setGravity(Gravity.CENTER_VERTICAL); connection.setPadding(dp(10),0,dp(8),0);
        status=new TextView(this); status.setText(R.string.waiting); status.setTextColor(0xffa9b3c4); status.setTextSize(12);
        connection.addView(status,new LinearLayout.LayoutParams(0,dp(48),1)); status.setGravity(Gravity.CENTER_VERTICAL);
        connection.addView(button(R.string.pair,"pair",this::pair));
        connection.addView(button(R.string.help,"help",() -> new AlertDialog.Builder(this).setTitle(R.string.help).setMessage(R.string.usb_help).setPositiveButton(android.R.string.ok,null).show()));
        root.addView(connection);
        HorizontalScrollView scroll=new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false); scroll.setFillViewport(true);
        LinearLayout toolbar=new LinearLayout(this); toolbar.setGravity(Gravity.CENTER); toolbar.setPadding(dp(6),0,dp(6),dp(4));
        PenInput.Tool[] tools=PenInput.Tool.values(); int[] names={R.string.pen,R.string.highlighter,R.string.eraser,R.string.laser,R.string.vanishing};
        for(int i=0;i<tools.length;i++) { PenInput.Tool tool=tools[i]; Button b=button(names[i],"tool."+tool,() -> ink.select(tool)); toolButtons.put(tool,b); toolbar.addView(b); }
        toolbar.addView(button(R.string.color,"color",this::color)); toolbar.addView(button(R.string.width,"width",this::width));
        toolbar.addView(button(R.string.undo,"undo",() -> ink.command("undo"))); toolbar.addView(button(R.string.redo,"redo",() -> ink.command("redo")));
        toolbar.addView(button(R.string.clear,"clear",() -> ink.command("clear")));
        toolbar.addView(button(R.string.hide,"hide",() -> { inkVisible=!inkVisible; ink.visible(inkVisible); }));
        toolbar.addView(button(R.string.fit,"fit",ink::resetZoom));
        scroll.addView(toolbar); root.addView(scroll); setContentView(root);
        ink.stateChanged=() -> { for(Map.Entry<PenInput.Tool,Button> e:toolButtons.entrySet()) e.getValue().setTextColor(e.getKey()==ink.tool()?0xffff9a64:Color.WHITE); };
        ink.stateChanged.run();
    }
    private Button button(int text,String tag,Runnable action) {
        Button b=new Button(this); b.setText(text); b.setTag(tag); b.setTextSize(12); b.setAllCaps(false);
        b.setContentDescription(getString(text)); b.setTooltipText(getString(text)); b.setMinWidth(dp(48)); b.setMinimumWidth(dp(48));
        b.setMinHeight(dp(48)); b.setPadding(dp(9),0,dp(9),0); b.setOnClickListener(v -> action.run()); return b;
    }
    private void color() {
        int[][] colors={{255,80,40,255},{245,45,65,255},{255,210,35,255},{25,190,100,255},{35,125,255,255},{255,255,255,255},{25,30,40,255}};
        new AlertDialog.Builder(this).setTitle(R.string.color).setItems(R.array.colors,(d,i) -> ink.color(colors[i])).show();
    }
    private void width() {
        new AlertDialog.Builder(this).setTitle(R.string.width).setItems(new String[]{getString(R.string.thin),getString(R.string.medium),getString(R.string.thick)},(d,i) -> ink.width(new float[]{2,4,8}[i])).show();
    }
    private void pair() { Toast.makeText(this,R.string.not_connected,Toast.LENGTH_SHORT).show(); }
}
