package com.deskink.app;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.inputmethod.EditorInfo;
import com.deskink.core.*;
import java.util.*;
import java.security.SecureRandom;

public final class MainActivity extends Activity {
    // A lifecycle restart must load after an older reader's durable save, or
    // reject that reader before it writes. Shared across Activity instances.
    private static final Object pairingStorageLock=new Object();
    private InkCanvas ink;
    private TextureView video;
    private WorkspaceView workspace;
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
        workspace=new WorkspaceView(this,this::pair);
        video=workspace.video; ink=workspace.ink; setContentView(workspace);
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
    private void immersive() {
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null) { controller.hide(WindowInsets.Type.systemBars()); controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
        } else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if(focused) immersive(); }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config); workspace.closeSheet();
        if(pairingDialog!=null&&pairingDialog.isShowing()) new DeskInkUi(this).finishSheet(pairingDialog);
    }
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
        workspace.showConnection(getString(R.string.app_name),getString(R.string.waiting),false);
        long device=preferences.getLong("device",0);
        if(device==0) { device=new SecureRandom().nextLong()&Long.MAX_VALUE; if(device==0) device=1; preferences.edit().putLong("device",device).apply(); }
        PcPairingStore pairingStore=new PcPairingStore(new java.io.File(getFilesDir(),"pc-registrations").toPath());
        List<PcRegistration> tokens;
        synchronized(pairingStorageLock) {
            try { tokens=pairingStore.load(preferences.getString("tokens","")); }
            catch(java.io.IOException error) { tokens=new ArrayList<>(); }
        }
        decoder=new VideoDecoder(() -> { UsbClient c=client; if(c!=null) c.requestKeyframe(); },() -> { });
        if(video.isAvailable()) attachSurface(video.getSurfaceTexture());
        UsbClient session=new UsbClient(9877,device,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels,tokens,new UsbClient.Listener() {
            public boolean savePairings(List<PcRegistration> saved) {
                synchronized(pairingStorageLock) {
                    if(generation!=runId) return false;
                    // USB reader thread; no pen writer lock is held during disk I/O.
                    try { pairingStore.save(saved); return true; }
                    catch(java.io.IOException error) {
                        runOnUiThread(() -> { if(generation==runId) Toast.makeText(MainActivity.this,
                            getString(R.string.registration_save_failed),Toast.LENGTH_LONG).show(); });
                        return false;
                    }
                }
            }
            public void state(UsbClient.State state) { runOnUiThread(() -> { if(generation==runId) showState(state); }); }
            public void stream(UsbClient.Binding next) {
                if(generation!=runId) return;
                VideoDecoder d=decoder; if(d!=null) d.configure(next.config);
                runOnUiThread(() -> {
                    if(generation!=runId) return;
                    binding=next; String token=hex(next.token);
                    InkDocument document=documents.select(token,next.host);
                    UsbClient owner=client; ink.bind(document,c -> owner!=null&&owner.ink(next,c),next.config.width,next.config.height);
                    workspace.showConnection(next.host,getString(R.string.usb_details,next.config.width,next.config.height),true);
                    if(pairingDialog!=null) pairingDialog.dismiss(); transformVideo();
                });
            }
            public UsbClient.DecodeStats decoding() {
                VideoDecoder d=decoder; return d==null?new UsbClient.DecodeStats(0,0,0):d.statistics();
            }
            public void frame(FrameAssembler.Frame frame) { VideoDecoder d=decoder; if(generation==runId&&d!=null) d.offer(frame); }
        });
        client=session;
        Thread start=new Thread(() -> {
            try { session.start(); }
            catch(java.io.IOException e) { runOnUiThread(() -> { if(generation==runId) workspace.showConnection(getString(R.string.app_name),getString(R.string.not_connected),false); }); }
        },"DeskInk USB start"); start.setDaemon(true); start.start();
    }
    private static String hex(byte[] token) {
        char[] alphabet="0123456789abcdef".toCharArray(); StringBuilder text=new StringBuilder(32);
        for(byte b:token) text.append(alphabet[(b&255)>>4]).append(alphabet[b&15]); return text.toString();
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
        binding=null; ink.disconnect(); workspace.showConnection(getString(R.string.app_name),getString(message),false);
        if(state==UsbClient.State.PAIRING) pair();
    }
    private void pair() {
        if(pairingDialog!=null&&pairingDialog.isShowing()) return;
        workspace.closeSheet(); DeskInkUi ui=new DeskInkUi(this);
        LinearLayout content=ui.sheetContent(R.string.pair,R.drawable.ic_monitor);
        TextView hint=ui.text(R.string.pair_hint,14,DeskInkUi.MUTED,false); hint.setLineSpacing(dp(4),1); content.addView(hint); content.addView(ui.space(22));
        TextView label=ui.text(R.string.pair_label,12,DeskInkUi.TEXT,true); content.addView(label); content.addView(ui.space(8));
        EditText code=new EditText(this); code.setTag("pair.code"); code.setId(View.generateViewId()); label.setLabelFor(code.getId());
        code.setInputType(InputType.TYPE_CLASS_NUMBER); code.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)}); code.setSingleLine(true);
        // Keep the PC-code instructions visible in narrow landscape windows.
        code.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_FULLSCREEN|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        code.setHint(R.string.pair_placeholder); code.setTextSize(26); code.setGravity(Gravity.CENTER); code.setLetterSpacing(.14f);
        code.setTypeface(getResources().getFont(R.font.geist_mono)); code.setTextColor(DeskInkUi.TEXT); code.setHintTextColor(DeskInkUi.MUTED);
        code.setPadding(dp(12),dp(16),dp(12),dp(16)); code.setBackground(ui.surface(DeskInkUi.CANVAS,14,DeskInkUi.BORDER));
        code.setContentDescription(getString(R.string.pair_label)); code.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        content.addView(code,new LinearLayout.LayoutParams(-1,-2));
        TextView error=ui.text("",13,DeskInkUi.DANGER,false); error.setTag("pair.error"); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setPadding(0,dp(8),0,0); error.setVisibility(View.GONE); content.addView(error);
        content.addView(ui.space(12)); content.addView(ui.text(R.string.pair_note,12,DeskInkUi.MUTED,false));
        code.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { error.setVisibility(View.GONE); }
            public void afterTextChanged(Editable text) { }
        });
        pairingDialog=new AlertDialog.Builder(this).setView(ui.scroll(content))
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.connect,null).create();
        pairingDialog.setOnShowListener(ignored -> pairingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value=code.getText().toString();
            if(!value.matches("[0-9]{6}")||Integer.parseInt(value)==0) { error.setText(R.string.invalid_code); error.setVisibility(View.VISIBLE); return; }
            UsbClient c=client; if(c==null) { error.setText(R.string.not_connected); error.setVisibility(View.VISIBLE); return; }
            c.pair(Integer.parseInt(value)); pairingDialog.dismiss();
        }));
        code.setOnEditorActionListener((view,action,event) -> { if(action==EditorInfo.IME_ACTION_DONE) { pairingDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); return true; } return false; });
        pairingDialog.show(); ui.finishSheet(pairingDialog);
        pairingDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    @Override public void onStop() {
        ++runId; ink.disconnect(); UsbClient previous=client; client=null; if(previous!=null) previous.close();
        VideoDecoder d=decoder; decoder=null; if(d!=null) d.close();
        if(surface!=null) { surface.release(); surface=null; }
        if(pairingDialog!=null) pairingDialog.dismiss();
        workspace.closeSheet(); binding=null; super.onStop();
    }
}
