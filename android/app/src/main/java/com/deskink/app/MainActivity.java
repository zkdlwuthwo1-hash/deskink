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
    private volatile AutoConnectSession client;
    private boolean lanMode;
    private volatile VideoDecoder decoder;
    private volatile int runId;
    private Surface surface;
    private UsbClient.Binding binding;
    private AlertDialog pairingDialog,networkDialog;
    private SharedPreferences preferences;
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        workspace=new WorkspaceView(this,this::pair,this::network);
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
        if(networkDialog!=null&&networkDialog.isShowing()) new DeskInkUi(this).finishSheet(networkDialog);
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
        super.onStart();
        if(preferences.getBoolean("auto-paused",false)) {
            workspace.showConnection(getString(R.string.app_name),getString(R.string.auto_paused),false);
        } else startSession(null,9876);
    }
    private void stopTransport() {
        ink.disconnect(); binding=null;
        AutoConnectSession previous=client; client=null; if(previous!=null) previous.close();
        VideoDecoder previousDecoder=decoder; decoder=null; if(previousDecoder!=null) previousDecoder.close();
    }
    private void startSession(String address,int port) {
        final int generation=++runId; stopTransport(); lanMode=address!=null;
        workspace.showConnection(getString(R.string.app_name),getString(lanMode?R.string.network_connecting:R.string.waiting),false);
        long device=preferences.getLong("device",0);
        if(device==0) { device=new SecureRandom().nextLong()&Long.MAX_VALUE; if(device==0) device=1; preferences.edit().putLong("device",device).apply(); }
        PcPairingStore pairingStore=new PcPairingStore(new java.io.File(getFilesDir(),"pc-registrations").toPath());
        List<PcRegistration> tokens;
        synchronized(pairingStorageLock) {
            try { tokens=pairingStore.load(preferences.getString("tokens","")); }
            catch(java.io.IOException error) {
                workspace.showConnection(getString(R.string.app_name),getString(R.string.registration_load_failed),false);
                Toast.makeText(this,getString(R.string.registration_load_failed),Toast.LENGTH_LONG).show();
                return;
            }
        }
        decoder=new VideoDecoder(() -> { AutoConnectSession c=client; if(c!=null) c.requestKeyframe(); },() -> { });
        if(video.isAvailable()) attachSurface(video.getSurfaceTexture());
        AutoConnectSession.Listener listener=new AutoConnectSession.Listener() {
            public void transport(boolean lan) { runOnUiThread(() -> {
                if(generation!=runId) return;
                lanMode=lan; binding=null; ink.disconnect();
                if(pairingDialog!=null) pairingDialog.dismiss();
                workspace.showConnection(getString(R.string.app_name),getString(lan?R.string.network_connecting:R.string.waiting),false);
            }); }
            public void remember(AutoConnectSession.Endpoint endpoint) {
                synchronized(pairingStorageLock) {
                    if(generation!=runId) return;
                    SharedPreferences.Editor edit=preferences.edit();
                    if(endpoint==null) edit.remove("auto-lan-address").remove("auto-lan-port").remove("auto-lan-registration");
                    else edit.putString("auto-lan-address",endpoint.address).putInt("auto-lan-port",endpoint.port)
                        .putString("auto-lan-registration",endpoint.registration.encode());
                    edit.commit();
                }
            }
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
                AutoConnectSession owner=client; if(owner==null||!owner.owns(next)) return;
                final boolean lan=owner.isLan();
                VideoDecoder d=decoder; if(d!=null) d.configure(next.config);
                runOnUiThread(() -> {
                    if(generation!=runId||!owner.owns(next)) return;
                    binding=next; String token=hex(next.token);
                    InkDocument document=documents.select(token,next.host);
                    ink.bind(document,c -> owner.ink(next,c),next.config.width,next.config.height);
                    ink.editingEnabled((next.hostCaps&0x0800)!=0);
                    workspace.showConnection(next.host,getString(lan?R.string.network_details:R.string.usb_details,next.config.width,next.config.height),true);
                    if(pairingDialog!=null) pairingDialog.dismiss(); transformVideo();
                });
            }
            public UsbClient.DecodeStats decoding() {
                VideoDecoder d=decoder; return d==null?new UsbClient.DecodeStats(0,0,0):d.statistics();
            }
            public void frame(FrameAssembler.Frame frame) { VideoDecoder d=decoder; if(generation==runId&&d!=null) d.offer(frame); }
            public void failure(UdpClient.Failure failure) { runOnUiThread(() -> {
                if(generation!=runId) return;
                int message=failure==UdpClient.Failure.INK_DELAY?R.string.network_ink_delay:R.string.network_unreachable;
                workspace.showConnection(getString(R.string.app_name),getString(message),false);
                Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();
            }); }
        };
        AutoConnectSession.Endpoint remembered=null;
        try {
            PcRegistration registration=PcRegistration.decode(preferences.getString("auto-lan-registration",""));
            if(registration!=null) remembered=new AutoConnectSession.Endpoint(preferences.getString("auto-lan-address",""),
                preferences.getInt("auto-lan-port",9876),registration);
        } catch(IllegalArgumentException invalid) { /* An incomplete saved endpoint never becomes a guessed address. */ }
        final AutoConnectSession session=new AutoConnectSession(9877,device,getResources().getDisplayMetrics().widthPixels,
            getResources().getDisplayMetrics().heightPixels,tokens,remembered,listener);
        client=session;
        Thread start=new Thread(() -> {
            try { session.start(); if(address!=null) session.connectLan(address,port); }
            catch(java.io.IOException|IllegalArgumentException e) {
                session.close();
                runOnUiThread(() -> { if(generation==runId) workspace.showConnection(getString(R.string.app_name),getString(address!=null?R.string.network_unreachable:R.string.not_connected),false); });
            }
        },"DeskInk connection start"); start.setDaemon(true); start.start();
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
            default: message=lanMode?R.string.network_disconnected:R.string.waiting;
        }
        binding=null; ink.disconnect(); workspace.showConnection(getString(R.string.app_name),getString(message),false);
        if(state==UsbClient.State.PAIRING) pair();
    }
    private void network() {
        if(networkDialog!=null&&networkDialog.isShowing()) return;
        workspace.closeSheet(); DeskInkUi ui=new DeskInkUi(this);
        LinearLayout content=ui.sheetContent(R.string.network,R.drawable.ic_wifi);
        // Keep the address above the longer help in short windows and at large font sizes.
        EditText address=networkField(content,ui,R.string.network_address,"network.address",preferences.getString("lan-address",""));
        address.setHint("192.168.1.20"); address.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        EditText port=networkField(content,ui,R.string.network_port,"network.port",Integer.toString(preferences.getInt("lan-port",9876)));
        port.setKeyListener(DigitsKeyListener.getInstance("0123456789")); port.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        TextView error=ui.text("",13,DeskInkUi.DANGER,false); error.setTag("network.error"); error.setVisibility(View.GONE);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); content.addView(error); content.addView(ui.space(12));
        TextView hint=ui.text(R.string.network_hint,14,DeskInkUi.MUTED,false); hint.setLineSpacing(dp(4),1);
        content.addView(hint); content.addView(ui.space(12));
        content.addView(ui.text(R.string.network_note,12,DeskInkUi.MUTED,false));
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setView(ui.scroll(content))
            .setNegativeButton(R.string.network_usb,(dialog,which) -> {
                preferences.edit().putBoolean("auto-paused",false).apply(); startSession(null,9876);
            }).setPositiveButton(R.string.connect,null);
        if(client!=null) builder.setNeutralButton(R.string.network_disconnect,(dialog,which) -> {
            preferences.edit().putBoolean("auto-paused",true).apply();
            ++runId; stopTransport(); workspace.showConnection(getString(R.string.app_name),getString(R.string.auto_paused),false);
        });
        networkDialog=builder.create();
        networkDialog.setOnShowListener(ignored -> networkDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String host=address.getText().toString().trim(); int number;
            try { number=Integer.parseInt(port.getText().toString()); UdpClient.endpoint(host,number); }
            catch(IllegalArgumentException invalid) { error.setText(R.string.network_invalid); error.setVisibility(View.VISIBLE); return; }
            preferences.edit().putString("lan-address",host).putInt("lan-port",number).putBoolean("auto-paused",false).apply();
            networkDialog.dismiss();
            AutoConnectSession owner=client; if(owner==null||owner.isClosed()) startSession(host,number); else owner.connectLan(host,number);
        }));
        networkDialog.show(); ui.finishSheet(networkDialog);
        networkDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private EditText networkField(LinearLayout content,DeskInkUi ui,int label,String tag,String value) {
        TextView title=ui.text(label,12,DeskInkUi.TEXT,true); content.addView(title); content.addView(ui.space(8));
        EditText field=new EditText(this); field.setTag(tag); field.setId(View.generateViewId()); title.setLabelFor(field.getId());
        field.setText(value); field.setSingleLine(true); field.setInputType(InputType.TYPE_CLASS_PHONE);
        field.setImeOptions(EditorInfo.IME_ACTION_NEXT|EditorInfo.IME_FLAG_NO_FULLSCREEN|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setTextSize(18); field.setTextColor(DeskInkUi.TEXT); field.setHintTextColor(DeskInkUi.MUTED);
        field.setTypeface(getResources().getFont(R.font.geist_mono)); field.setPadding(dp(12),dp(12),dp(12),dp(12));
        field.setBackground(ui.surface(DeskInkUi.CANVAS,12,DeskInkUi.BORDER)); field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        content.addView(field,new LinearLayout.LayoutParams(-1,-2)); content.addView(ui.space(16)); return field;
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
            AutoConnectSession c=client; if(c==null) { error.setText(lanMode?R.string.network_unreachable:R.string.not_connected); error.setVisibility(View.VISIBLE); return; }
            c.pair(Integer.parseInt(value)); pairingDialog.dismiss();
        }));
        code.setOnEditorActionListener((view,action,event) -> { if(action==EditorInfo.IME_ACTION_DONE) { pairingDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); return true; } return false; });
        pairingDialog.show(); ui.finishSheet(pairingDialog);
        pairingDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    @Override public void onStop() {
        ++runId; stopTransport();
        if(surface!=null) { surface.release(); surface=null; }
        if(pairingDialog!=null) pairingDialog.dismiss();
        if(networkDialog!=null) networkDialog.dismiss();
        workspace.closeSheet(); binding=null; super.onStop();
    }
}
