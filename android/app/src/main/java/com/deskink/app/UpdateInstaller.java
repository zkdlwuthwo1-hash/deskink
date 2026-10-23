package com.deskink.app;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import androidx.core.content.FileProvider;
import com.deskink.core.AppUpdate;
import java.io.*;
import java.util.*;

/** Validates update identity before granting the platform installer file access. */
final class UpdateInstaller {
    @SuppressWarnings("deprecation")
    static Intent intent(Context context,File apk,AppUpdate offer) throws IOException {
        try {
            PackageManager pm=context.getPackageManager();
            PackageInfo installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
            PackageInfo candidate=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES);
            if(candidate==null||!context.getPackageName().equals(candidate.packageName)
                    ||version(candidate)!=offer.versionCode||version(candidate)<=version(installed)
                    ||!offer.version.equals(candidate.versionName)||!sameSigners(installed.signatures,candidate.signatures)) {
                throw new IOException("The downloaded APK is not a newer DeskInk release signed by this publisher");
            }
            return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(FileProvider.getUriForFile(context,context.getPackageName()+".updates",apk),"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(PackageManager.NameNotFoundException|IllegalArgumentException error) { throw new IOException("Cannot verify the app update",error); }
    }
    @SuppressWarnings("deprecation")
    static long version(PackageInfo info) { return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode; }
    private static boolean sameSigners(Signature[] installed,Signature[] candidate) {
        return installed!=null&&candidate!=null&&installed.length>0&&installed.length==candidate.length
            &&new HashSet<>(Arrays.asList(installed)).equals(new HashSet<>(Arrays.asList(candidate)));
    }
}
