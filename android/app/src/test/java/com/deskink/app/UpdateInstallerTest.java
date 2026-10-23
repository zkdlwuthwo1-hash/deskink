package com.deskink.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.*;
import com.deskink.core.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class UpdateInstallerTest {
    @Test public void onlyANewerSameAppSignedByTheInstalledPublisherCanOpenTheSystemInstaller() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        PackageManager manager=activity.getPackageManager();
        PackageInfo installed=manager.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES|PackageManager.GET_PROVIDERS|PackageManager.GET_META_DATA);
        installed.versionCode=5; installed.signatures=new Signature[]{new Signature(new byte[]{1,2,3})};
        Shadows.shadowOf(manager).installPackage(installed);
        File directory=new File(activity.getCacheDir(),"updates"); directory.mkdirs();
        File apk=new File(directory,"DeskInk.apk"); try(FileOutputStream out=new FileOutputStream(apk)) { out.write(1); }
        PackageInfo candidate=new PackageInfo(); candidate.packageName=activity.getPackageName(); candidate.versionCode=6; candidate.versionName="0.6.0";
        candidate.signatures=installed.signatures;
        Shadows.shadowOf(manager).setPackageArchiveInfo(apk.getAbsolutePath(),candidate);
        AppUpdate offer=AppUpdate.parse(Ink.object("version_code",6,"version","0.6.0","size",1,"sha256","0".repeat(64),
            "url","https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.6.0/DeskInk-Android.apk").toString(),5);
        Intent intent=UpdateInstaller.intent(activity,apk,offer);
        assertEquals(Intent.ACTION_VIEW,intent.getAction()); assertEquals("content",intent.getData().getScheme());
        assertEquals(activity.getPackageName()+".updates",intent.getData().getAuthority());
        assertEquals("application/vnd.android.package-archive",intent.getType());
        assertTrue((intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
        candidate.signatures=new Signature[]{new Signature(new byte[]{9,9,9})};
        assertThrows(IOException.class,() -> UpdateInstaller.intent(activity,apk,offer));
        candidate.signatures=installed.signatures; candidate.packageName="another.app";
        assertThrows(IOException.class,() -> UpdateInstaller.intent(activity,apk,offer));
        candidate.packageName=installed.packageName; candidate.versionCode=5;
        assertThrows(IOException.class,() -> UpdateInstaller.intent(activity,apk,offer));
    }
}
