package io.aether.studio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.*;
import android.widget.FrameLayout;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/assets/web/index.html";
    private static final int PICK = 101, EXPORT = 102;
    private WebView web;
    private ReleaseUpdater updater;
    private ValueCallback<Uri[]> fileCallback;
    private int exportId;
    private String exportText;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean destroyed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16,16,29));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
                return insets;
            });
        } else { root.setFitsSystemWindows(true); }
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(16,16,29));
        root.addView(web,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(false); s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false); s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request){
                Uri u=request.getUrl(); String h=u.getHost();
                if ("https".equals(u.getScheme()) && HOST.equals(h)) {
                    WebResourceResponse r=loader.shouldInterceptRequest(u);
                    if(r!=null)return r;
                } else if ("https".equals(u.getScheme()) && ("fonts.googleapis.com".equals(h)||"fonts.gstatic.com".equals(h))) return null;
                return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",java.util.Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri u=request.getUrl(); return !HOME.equals(u.toString());
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);
                fileCallback=callback;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
                String[] types=params.getAcceptTypes();if(types!=null&&types.length==1&&types[0].contains("/")&&!types[0].contains(","))intent.setType(types[0]);
                try{startActivityForResult(intent,PICK);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;}
                return true;
            }
        });
        web.addJavascriptInterface(new ExportBridge(),"AetherAndroid");
        web.loadUrl(HOME);
        updater = new ReleaseUpdater(this);
    }
    private final class ExportBridge {
        @JavascriptInterface public void checkForUpdates(){runOnUiThread(()->{if(updater!=null&&!destroyed)updater.check(true);});}
        @JavascriptInterface public void exportText(int id,String name,String text){
            runOnUiThread(()->{
                if(destroyed)return;
                if(exportText!=null){reply(id,false,"Another export is open");return;}
                if(name==null||!name.matches("aether-[a-z]+-[0-9]+\\.[a-z]+")||text==null||text.length()>1000000){reply(id,false,"Invalid export");return;}
                exportId=id;exportText=text;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/octet-stream");intent.putExtra(Intent.EXTRA_TITLE,name);
                try{startActivityForResult(intent,EXPORT);}catch(Exception e){exportText=null;reply(id,false,"Cannot open file picker");}
            });
        }
    }
    private void reply(int id,boolean ok,String message){
        if(!destroyed&&web!=null)web.evaluateJavascript("window.aetherExportResult("+id+","+ok+","+JSONObject.quote(message)+")",null);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK){ValueCallback<Uri[]> c=fileCallback;fileCallback=null;if(c!=null)c.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);}
        if(request==EXPORT&&exportText!=null){final int id=exportId;final String text=exportText;exportText=null;
            if(result!=RESULT_OK||data==null||data.getData()==null){reply(id,false,"Export cancelled");return;}
            final Uri uri=data.getData();io.execute(()->{try(OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){if(stream==null)throw new java.io.IOException("No output stream");stream.write(text.getBytes(StandardCharsets.UTF_8));stream.flush();}catch(Exception e){runOnUiThread(()->reply(id,false,"Export failed: "+e.getClass().getSimpleName()));return;}runOnUiThread(()->reply(id,true,"selected document"));});
        }
    }
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.aetherBack ? window.aetherBack() : false",result->{if(!"true".equals(result))super.onBackPressed();});else super.onBackPressed();}
    @Override protected void onPause(){if(updater!=null)updater.pause();if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();if(updater!=null)updater.resume();}
    @Override protected void onDestroy(){destroyed=true;if(updater!=null)updater.close();if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}io.shutdown();if(web!=null){web.removeJavascriptInterface("AetherAndroid");web.stopLoading();((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
}
