package io.aether.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReleaseUpdater implements AutoCloseable {
    private static final String TAG = "AetherUpdater";
    private static final String REPO = "0wertyx/Aether-mobile";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final Pattern APK = Pattern.compile("AETHER-Mobile-([1-9][0-9]{0,9})\\.apk");
    private static final long MAX_APK = 256L * 1024 * 1024;
    private static final long CHECK_INTERVAL = 6L * 60 * 60 * 1000;
    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final File cache;
    private boolean resumed, closed, busy, manual, checkedThisLaunch;
    private volatile boolean cancelled;
    private AlertDialog dialog;
    private ProgressBar progress;
    private TextView status;
    private Release pending;
    private File ready;
    private Runnable deferred;
    private long lastAttempt;

    private static final class Release {
        final long code, bytes;
        final String tag, notes, digest, url;
        Release(long code, long bytes, String tag, String notes, String digest, String url) {
            this.code=code; this.bytes=bytes; this.tag=tag; this.notes=notes; this.digest=digest; this.url=url;
        }
    }
    ReleaseUpdater(Activity activity) {
        this.activity=activity;
        cache=new File(activity.getCacheDir(),"updates");
    }

    void resume() {
        resumed=true;
        if (deferred!=null) {
            Runnable action=deferred; deferred=null; action.run();
        } else if (!checkedThisLaunch || System.currentTimeMillis()-lastAttempt>=CHECK_INTERVAL) {
            check(false);
        }
    }
    void pause() { resumed=false; }
    private void present(Runnable action) {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return;
        if (resumed) action.run(); else deferred=action;
    }
    void check(boolean userInitiated) {
        if (closed || busy || dialog!=null) return;
        long now=System.currentTimeMillis();
        long lastSuccess=activity.getPreferences(0).getLong("update_checked",0);
        if (!userInitiated && ((now>=lastSuccess && now-lastSuccess<CHECK_INTERVAL)
                || (checkedThisLaunch && now-lastAttempt<CHECK_INTERVAL))) {
            checkedThisLaunch=true; lastAttempt=now; return;
        }
        checkedThisLaunch=true; lastAttempt=now;
        busy=true; manual=userInitiated;
        if (manual) toast("Checking for updates…");
        worker.execute(()->{
            try {
                PackageInfo installed=installed();
                Release release=findRelease(version(installed));
                activity.runOnUiThread(()->{
                    if (closed) return;
                    busy=false;
                    activity.getPreferences(0).edit().putLong("update_checked",System.currentTimeMillis()).apply();
                    if (release==null) {
                        if(manual) present(()->toast("No newer stable release"));
                        return;
                    }
                    pending=release;
                    present(()->offer(release));
                });
            } catch (Exception e) {
                Log.w(TAG,"Release check failed",e);
                activity.runOnUiThread(()->{if(closed)return;busy=false;if(manual)present(()->toast("Update check failed. Try again later."));});
            }
        });
    }

    private static Release findRelease(long installedCode) throws Exception {
        HttpsURLConnection conn=open(API,true);
        try {
            int response=conn.getResponseCode();
            if(response==404) return null;
            if(response!=200) throw new IOException("Release HTTP "+response);
            JSONObject root;
            try(InputStream input=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1) {
                    if(out.size()+n>2*1024*1024)throw new IOException("Release metadata too large");
                    out.write(buffer,0,n);
                }
                root=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
            if(root.optBoolean("draft")||root.optBoolean("prerelease"))return null;
            JSONArray assets=root.getJSONArray("assets");
            Release best=null;
            for(int i=0;i<assets.length();i++){
                JSONObject asset=assets.getJSONObject(i);
                Matcher match=APK.matcher(asset.optString("name"));
                if(!match.matches()||!"uploaded".equals(asset.optString("state")))continue;
                long code=Long.parseLong(match.group(1));
                long bytes=asset.optLong("size",-1);
                String hash=asset.optString("digest","");
                String url=asset.optString("browser_download_url","");
                if(code<=installedCode||code>Integer.MAX_VALUE||bytes<=0||bytes>MAX_APK||
                        !hash.matches("sha256:[0-9a-fA-F]{64}")||
                        !url.startsWith("https://github.com/"+REPO+"/releases/download/"))continue;
                if(best==null||code>best.code)best=new Release(code,bytes,root.optString("tag_name"),
                        root.optString("body"),hash.substring(7).toLowerCase(Locale.ROOT),url);
            }
            return best;
        } finally {conn.disconnect();}
    }
    private static HttpsURLConnection open(String address,boolean api) throws IOException{
        URL url=new URL(address);
        for(int i=0;i<=5;i++){
            String host=url.getHost().toLowerCase(Locale.ROOT);
            boolean trusted=api?host.equals("api.github.com"):host.equals("github.com")
                    ||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com");
            if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||
                    (url.getPort()!=-1&&url.getPort()!=443)||!trusted)throw new IOException("Untrusted update URL");
            HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent","AETHER-Mobile-Updater");
            c.setRequestProperty("Accept",api?"application/vnd.github+json":"application/octet-stream");
            c.setRequestProperty("Accept-Encoding","identity");
            int response;
            try{response=c.getResponseCode();}catch(IOException e){c.disconnect();throw e;}
            if(response==301||response==302||response==303||response==307||response==308){
                String next=c.getHeaderField("Location");c.disconnect();
                if(next==null)throw new IOException("Missing redirect URL");
                url=new URL(url,next);continue;
            }
            return c;
        }
        throw new IOException("Too many redirects");
    }
    private void offer(Release release) {
        if(dialog!=null||busy||closed)return;
        String notes=release.notes.length()>1800?release.notes.substring(0,1800)+"…":release.notes;
        dialog=new AlertDialog.Builder(activity).setTitle("AETHER "+release.tag)
                .setMessage("New version available ("+release.bytes/1024/1024+" MB).\n\n"+notes)
                .setNegativeButton("Later",null)
                .setPositiveButton("Download",(d,w)->{dialog=null;download(release);}).create();
        final AlertDialog shown=dialog;
        shown.setOnDismissListener(d->{if(dialog==shown)dialog=null;});
        shown.show();
    }
    private void download(Release release){
        if(closed||busy)return;
        busy=true;cancelled=false;
        LinearLayout layout=new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p=(int)(24*activity.getResources().getDisplayMetrics().density);
        layout.setPadding(p,p,p,p);
        status=new TextView(activity);status.setText("Downloading update…");
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);layout.addView(status);layout.addView(progress);
        dialog=new AlertDialog.Builder(activity).setTitle("AETHER Update").setView(layout)
                .setNegativeButton("Cancel",(d,w)->cancelled=true).setCancelable(false).create();
        dialog.show();
        worker.execute(()->{
            File part=null;
            try {
                if(!cache.isDirectory()&&!cache.mkdirs())throw new IOException("Cannot create update cache");
                part=File.createTempFile("download-", ".apk.part",cache);
                MessageDigest hash=MessageDigest.getInstance("SHA-256");
                HttpsURLConnection c=open(release.url,false);
                long written=0;int previous=-1;
                try{
                    if(c.getResponseCode()!=200)throw new IOException("APK HTTP "+c.getResponseCode());
                    long expected=c.getContentLengthLong();
                    if(expected>=0&&expected!=release.bytes)throw new IOException("Wrong APK length");
                    try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(part)){
                        byte[] buffer=new byte[65536];int n;
                        while((n=in.read(buffer))!=-1){
                            if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");
                            written+=n;if(written>release.bytes)throw new IOException("APK exceeds expected size");
                            hash.update(buffer,0,n);out.write(buffer,0,n);
                            int percent=(int)(written*100/release.bytes);
                            if(percent!=previous){previous=percent;final int value=percent;
                                activity.runOnUiThread(()->{if(!closed&&progress!=null)progress.setProgress(value);});}
                        }
                        out.getFD().sync();
                    }
                }finally{c.disconnect();}
                if(cancelled)throw new InterruptedIOException("Cancelled");
                if(written!=release.bytes||!hex(hash.digest()).equals(release.digest))throw new SecurityException("APK digest mismatch");
                File target=new File(cache,"update-"+release.code+".apk");
                if(target.exists()&&!target.delete())throw new IOException("Cannot replace cached update");
                if(!part.renameTo(target))throw new IOException("Cannot finalize update");
                part=target;
                activity.runOnUiThread(()->{if(!closed&&status!=null)status.setText("Verifying package signature…");});
                verify(target,release.code);
                if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");
                final File verified=target;
                activity.runOnUiThread(()->{
                    if(closed||cancelled){verified.delete();if(!closed)finishDialog();return;}
                    ready=verified;finishDialog();present(()->confirmInstall(verified,release));
                });
                part=null;
            }catch(Exception e){
                if(part!=null)part.delete();
                boolean stopped=cancelled||e instanceof InterruptedIOException;
                if(!stopped)Log.w(TAG,"Update download failed",e);
                activity.runOnUiThread(()->{if(closed)return;finishDialog();if(!stopped)present(()->toast("Update failed verification or download. No APK installed."));});
            }
        });
    }
    private void finishDialog(){
        busy=false;if(dialog!=null){dialog.dismiss();dialog=null;}progress=null;status=null;
    }
    private static long version(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    private int flags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    private PackageInfo installed() throws PackageManager.NameNotFoundException{
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(),flags());
    }
    private static Set<String> signatures(PackageInfo info)throws Exception{
        Signature[] list;
        if(Build.VERSION.SDK_INT>=28){
            if(info.signingInfo==null)throw new SecurityException("Missing signing info");
            list=info.signingInfo.getApkContentsSigners();
        }else list=info.signatures;
        if(list==null||list.length==0)throw new SecurityException("Missing signer");
        Set<String> result=new HashSet<>();
        for(Signature s:list)result.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));
        return result;
    }
    private void verify(File apk,long expectedCode)throws Exception{
        PackageInfo current=installed();
        PackageInfo update=activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),flags());
        if(update==null||!activity.getPackageName().equals(update.packageName)||
                version(update)!=expectedCode||version(update)<=version(current)||
                !signatures(current).equals(signatures(update)))throw new SecurityException("APK package, version or signer mismatch");
    }
    private static String hex(byte[] data){
        StringBuilder out=new StringBuilder(data.length*2);
        for(byte b:data){int n=b&255;out.append(Character.forDigit(n>>>4,16)).append(Character.forDigit(n&15,16));}return out.toString();
    }
    private void confirmInstall(File apk,Release release){
        if(closed||dialog!=null)return;
        dialog=new AlertDialog.Builder(activity).setTitle("Update ready")
                .setMessage("Signature and version verified. Android will ask you to confirm installation.")
                .setNegativeButton("Later",null)
                .setPositiveButton("Install",(d,w)->{dialog=null;install(apk,release);}).create();
        final AlertDialog shown=dialog;shown.setOnDismissListener(d->{if(dialog==shown)dialog=null;});shown.show();
    }
    private void install(File apk,Release release){
        if(!apk.isFile()||ready!=apk){toast("Verified APK is no longer available.");return;}
        try{
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",apk);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(android.content.ClipData.newRawUri("Verified AETHER update",uri));
            activity.startActivity(intent);
        }catch(Exception e){Log.w(TAG,"Installer unavailable",e);toast("Android installer unavailable. Update not installed.");}
    }
    private void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_SHORT).show();}
    @Override public void close(){
        closed=true;cancelled=true;deferred=null;
        if(dialog!=null){dialog.dismiss();dialog=null;}
        worker.shutdownNow();
    }
}
