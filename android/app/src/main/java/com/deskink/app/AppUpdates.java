package com.deskink.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import com.deskink.core.AppUpdate;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** Release checks never interrupt drawing. Installation starts only from the update button. */
final class AppUpdates implements AutoCloseable {
    private enum State { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, FAILED }
    private final Activity activity;
    private final BiConsumer<String,Boolean> changed;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> new Thread(r,"DeskInk-updates"));
    private final Handler main=new Handler(Looper.getMainLooper());
    private State state=State.IDLE;
    private AppUpdate offer;
    private File apk;
    private AlertDialog dialog;
    private boolean closed,waitingPermission;
    private long checkedAt;
    private final Runnable periodic=new Runnable() {
        @Override public void run() { automaticCheck(); main.postDelayed(this,6*60*60*1000L); }
    };
    AppUpdates(Activity activity,BiConsumer<String,Boolean> changed) { this.activity=activity; this.changed=changed; }
    void start() { main.removeCallbacks(periodic); periodic.run(); }
    void stop() { main.removeCallbacks(periodic); dismiss(); }
    void resume() {
        if(waitingPermission) {
            waitingPermission=false;
            if(activity.getPackageManager().canRequestPackageInstalls()) install(); else show();
        }
    }
    private void automaticCheck() {
        // Native UI tests and unsigned developer builds never contact a release server.
        if((activity.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0) return;
        if(state==State.IDLE||(state!=State.AVAILABLE&&state!=State.READY&&SystemClock.elapsedRealtime()-checkedAt>6*60*60*1000L)) check();
    }
    void show() {
        if(state==State.IDLE) check();
        renderDialog();
    }
    void check() {
        if(closed||state==State.CHECKING||state==State.DOWNLOADING) return;
        state=State.CHECKING; notifyState();
        worker.execute(() -> {
            try {
                long version=UpdateInstaller.version(activity.getPackageManager().getPackageInfo(activity.getPackageName(),0));
                AppUpdate next=AppUpdate.check(version);
                main.post(() -> { if(closed) return; offer=next; state=next==null?State.CURRENT:State.AVAILABLE; checkedAt=SystemClock.elapsedRealtime(); notifyState(); });
            } catch(Exception error) { fail(); }
        });
    }
    private void download() {
        if(offer==null||state!=State.AVAILABLE) return;
        AppUpdate next=offer; state=State.DOWNLOADING; notifyState();
        worker.execute(() -> {
            File partial=null;
            try {
                File directory=new File(activity.getCacheDir(),"updates");
                if(!directory.isDirectory()&&!directory.mkdirs()) throw new IOException("Cannot create update cache");
                partial=new File(directory,"update.part"); next.download(partial);
                // Archive identity is checked before it becomes available to the installer.
                UpdateInstaller.intent(activity,partial,next);
                File complete=new File(directory,"DeskInk.apk");
                java.nio.file.Files.move(partial.toPath(),complete.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                main.post(() -> { if(closed) return; apk=complete; state=State.READY; notifyState(); });
            } catch(Exception error) { if(partial!=null) partial.delete(); fail(); }
        });
    }
    private void install() {
        if(state!=State.READY||apk==null||offer==null) return;
        if(!activity.getPackageManager().canRequestPackageInstalls()) {
            dismiss();
            dialog=new AlertDialog.Builder(activity).setTitle(R.string.update_permission_title).setMessage(R.string.update_permission_detail)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.update_open_settings,(d,w) -> {
                    try {
                        waitingPermission=true;
                        activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));
                    } catch(ActivityNotFoundException error) { waitingPermission=false; state=State.FAILED; notifyState(); }
                }).create();
            dialog.show(); new DeskInkUi(activity).finishSheet(dialog); return;
        }
        try { Intent intent=UpdateInstaller.intent(activity,apk,offer); dismiss(); activity.startActivity(intent); }
        catch(Exception error) { state=State.FAILED; notifyState(); Toast.makeText(activity,R.string.update_failed,Toast.LENGTH_LONG).show(); }
    }
    private void fail() { main.post(() -> { if(closed) return; state=State.FAILED; checkedAt=SystemClock.elapsedRealtime(); notifyState(); }); }
    private int message() {
        switch(state) {
            case CHECKING: return R.string.update_checking;
            case CURRENT: return R.string.update_current;
            case AVAILABLE: return R.string.update_available;
            case DOWNLOADING: return R.string.update_downloading;
            case READY: return R.string.update_ready;
            case FAILED: return R.string.update_failed;
            default: return R.string.update_intro;
        }
    }
    private void notifyState() {
        changed.accept(activity.getString(message()),state==State.AVAILABLE||state==State.READY);
        if(dialog!=null&&dialog.isShowing()) renderDialog();
    }
    private void renderDialog() {
        if(closed||activity.isFinishing()||activity.isDestroyed()) return;
        dismiss();
        String detail=activity.getString(message());
        if(offer!=null&&(state==State.AVAILABLE||state==State.READY)) detail="DeskInk "+offer.version+"\n\n"+detail;
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle(R.string.update_title).setMessage(detail).setNegativeButton(android.R.string.cancel,null);
        if(state==State.AVAILABLE) builder.setPositiveButton(R.string.update_download,(d,w)->download());
        else if(state==State.READY) builder.setPositiveButton(R.string.update_install,(d,w)->install());
        else if(state!=State.CHECKING&&state!=State.DOWNLOADING) builder.setPositiveButton(R.string.update_check,(d,w)->check());
        dialog=builder.create(); dialog.show(); new DeskInkUi(activity).finishSheet(dialog);
        // Keep the progress/result visible when a user explicitly opened this sheet.
        if(state==State.AVAILABLE) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> download());
        else if(state==State.READY) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> install());
        else if(state!=State.CHECKING&&state!=State.DOWNLOADING) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> check());
    }
    private void dismiss() { if(dialog!=null) { dialog.dismiss(); dialog=null; } }
    @Override public void close() { closed=true; stop(); worker.shutdownNow(); }
}
