package com.deskink.app;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.text.InputType;
import com.deskink.core.*;
import java.util.*;
import java.security.SecureRandom;

public final class MainActivity extends Activity {
    private InkCanvas ink;
    private TextureView video;
    private TextView status;
    private LinearLayout welcome;
    private final Map<PenInput.Tool,Button> toolButtons=new EnumMap<>(PenInput.Tool.class);
    private boolean inkVisible=true;
    private final MonitorDocuments documents=new MonitorDocuments();
    private volatile UsbClient client;
    private volatile VideoDecoder decoder;
    private volatile int runId;
    private Surface surface;
    private UsbClient.Binding binding;
    private AlertDialog pairingDialog;
    private SharedPreferences preferences;
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        FrameLayout workspace=new FrameLayout(this);
        video=new TextureView(this); video.setTag("video"); video.setOpaque(false); workspace.addView(video,new FrameLayout.LayoutParams(-1,-1));
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
        preferences=getSharedPreferences("usb-pairing",MODE_PRIVATE);
        ink.transformChanged=this::transformVideo;
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h) { attachSurface(texture); transformVideo(); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h) { transformVideo(); }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                VideoDecoder d=decoder; if(d!=null) d.surface(null);
                if(surface!=null) { surface.release(); surface=null; } return true;
            }
            public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
        });
        immersive();
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
    private void immersive() {
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null) { controller.hide(WindowInsets.Type.systemBars()); controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
        } else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if(focused) immersive(); }
    private void attachSurface(SurfaceTexture texture) {
        if(surface!=null) surface.release(); surface=new Surface(texture);
        VideoDecoder d=decoder; if(d!=null) d.surface(surface);
    }
    private void transformVideo() {
        if(binding==null||video.getWidth()==0||video.getHeight()==0) return;
        Matrix matrix=new Matrix();
        matrix.setScale(binding.config.width*ink.viewport.scale()/video.getWidth(),binding.config.height*ink.viewport.scale()/video.getHeight());
        matrix.postTranslate(ink.viewport.x(),ink.viewport.y()); video.setTransform(matrix);
    }
    @Override public void onStart() {
        super.onStart(); final int generation=++runId;
        long device=preferences.getLong("device",0);
        if(device==0) { device=new SecureRandom().nextLong()&Long.MAX_VALUE; if(device==0) device=1; preferences.edit().putLong("device",device).apply(); }
        List<byte[]> tokens=new ArrayList<>();
        for(String encoded:preferences.getString("tokens","").split(",")) {
            if(!encoded.matches("[0-9a-f]{32}")) continue;
            byte[] bytes=new byte[16]; for(int i=0;i<16;i++) bytes[i]=(byte)Integer.parseInt(encoded.substring(i*2,i*2+2),16); tokens.add(bytes);
        }
        decoder=new VideoDecoder(() -> { UsbClient c=client; if(c!=null) c.requestKeyframe(); },() -> { });
        if(video.isAvailable()) attachSurface(video.getSurfaceTexture());
        UsbClient session=new UsbClient(9877,device,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels,tokens,new UsbClient.Listener() {
            public void state(UsbClient.State state) { runOnUiThread(() -> { if(generation==runId) showState(state); }); }
            public void stream(UsbClient.Binding next) {
                if(generation!=runId) return;
                VideoDecoder d=decoder; if(d!=null) d.configure(next.config);
                runOnUiThread(() -> {
                    if(generation!=runId) return;
                    binding=next; String token=hex(next.token); rememberToken(token);
                    InkDocument document=documents.select(token,next.host);
                    UsbClient owner=client; ink.bind(document,c -> owner!=null&&owner.ink(next,c),next.config.width,next.config.height);
                    inkVisible=document.visible; welcome.setVisibility(View.GONE); video.setVisibility(View.VISIBLE);
                    status.setText(getString(R.string.connected,next.host,next.config.width,next.config.height));
                    if(pairingDialog!=null) pairingDialog.dismiss(); transformVideo();
                });
            }
            public void frame(FrameAssembler.Frame frame) { VideoDecoder d=decoder; if(generation==runId&&d!=null) d.offer(frame); }
        });
        client=session;
        Thread start=new Thread(() -> {
            try { session.start(); }
            catch(java.io.IOException e) { runOnUiThread(() -> { if(generation==runId) status.setText(R.string.not_connected); }); }
        },"DeskInk USB start"); start.setDaemon(true); start.start();
    }
    private static String hex(byte[] token) {
        char[] alphabet="0123456789abcdef".toCharArray(); StringBuilder text=new StringBuilder(32);
        for(byte b:token) text.append(alphabet[(b&255)>>4]).append(alphabet[b&15]); return text.toString();
    }
    private void rememberToken(String token) {
        List<String> saved=new ArrayList<>(Arrays.asList(preferences.getString("tokens","").split(",")));
        saved.removeIf(s -> s.equals(token)||s.isEmpty()); saved.add(0,token);
        while(saved.size()>2) saved.remove(saved.size()-1); preferences.edit().putString("tokens",String.join(",",saved)).apply();
    }
    private void showState(UsbClient.State state) {
        if(state==UsbClient.State.CONNECTED) return;
        int message;
        switch(state) {
            case PAIRING: message=R.string.pair_required; break;
            case RATE_LIMITED: message=R.string.rate_limited; break;
            case BUSY: message=R.string.busy; break;
            case INCOMPATIBLE: message=R.string.incompatible; break;
            default: message=R.string.waiting;
        }
        status.setText(message); binding=null; ink.disconnect(); welcome.setVisibility(View.VISIBLE); video.setVisibility(View.INVISIBLE);
        if(state==UsbClient.State.PAIRING) pair();
    }
    private void pair() {
        if(pairingDialog!=null&&pairingDialog.isShowing()) return;
        EditText code=new EditText(this); code.setInputType(InputType.TYPE_CLASS_NUMBER); code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        code.setHint("000000"); code.setTextSize(28); code.setGravity(Gravity.CENTER); code.setContentDescription(getString(R.string.pair_hint));
        pairingDialog=new AlertDialog.Builder(this).setTitle(R.string.pair).setMessage(R.string.pair_hint).setView(code)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.connect,null).create();
        pairingDialog.setOnShowListener(ignored -> pairingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value=code.getText().toString();
            if(!value.matches("[0-9]{6}")||Integer.parseInt(value)==0) { code.setError(getString(R.string.invalid_code)); return; }
            UsbClient c=client; if(c==null) { code.setError(getString(R.string.not_connected)); return; }
            c.pair(Integer.parseInt(value)); pairingDialog.dismiss();
        })); pairingDialog.show();
    }
    @Override public void onStop() {
        ++runId; ink.disconnect(); UsbClient previous=client; client=null; if(previous!=null) previous.close();
        VideoDecoder d=decoder; decoder=null; if(d!=null) d.close();
        if(surface!=null) { surface.release(); surface=null; }
        if(pairingDialog!=null) pairingDialog.dismiss();
        binding=null; super.onStop();
    }
}
