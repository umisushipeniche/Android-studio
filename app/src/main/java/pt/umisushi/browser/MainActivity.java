package pt.umisushi.browser;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout root, toolbar;
    private WebView web;
    private EditText address;
    private boolean guest = false, portuguese = false, scanBusy = false;
    private String pinnedOrigin = null;
    private String pin = "";
    private CameraDevice camera;
    private CameraCaptureSession cameraSession;
    private ImageReader reader;
    private TextureView preview;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final MultiFormatReader qrDecoder = new MultiFormatReader();
    private static final int CAMERA_REQUEST = 100;
    private final int coffee = Color.rgb(54, 41, 35);
    private final int gold = Color.rgb(184, 145, 83);
    private String t(String zh, String pt) { return portuguese ? pt : zh; }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        portuguese = getPreferences(0).getBoolean("pt", false);
        pin = getPreferences(0).getString("pin", "");
        getWindow().setStatusBarColor(coffee);
        getWindow().setNavigationBarColor(coffee);
        home();
    }
    private void base() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xfff6f2ed);
        setContentView(root);
    }
    private TextView button(String label, Runnable click) {
        TextView b = new TextView(this); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(16);
        b.setGravity(Gravity.CENTER); b.setBackground(new ColorDrawable(coffee));
        b.setPadding(18, 19, 18, 19); b.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(12, 8, 12, 8); b.setLayoutParams(p);
        return b;
    }
    private void home() {
        stopCamera(); guest = false; pinnedOrigin = null;
        getWindow().getDecorView().setSystemUiVisibility(0);
        if (web != null) { root.removeView(web); web.destroy(); web = null; }
        base();
        TextView heading = new TextView(this); heading.setText("UMI BROWSER"); heading.setTextColor(coffee);
        heading.setTextSize(28); heading.setGravity(Gravity.CENTER); heading.setPadding(15,45,15,35); root.addView(heading);
        root.addView(button(t("扫描二维码", "Ler código QR"), this::scan));
        root.addView(button(t("普通浏览器", "Navegador normal"), () -> openBrowser("https://www.google.com", false)));
        root.addView(button(t("语言：中文 / Português", "Idioma: Português / 中文"), () -> {
            portuguese = !portuguese; getPreferences(0).edit().putBoolean("pt", portuguese).apply(); home();
        }));
        root.addView(button(t("设置员工 PIN", "Definir PIN da equipa"), this::configurePin));
    }
    private void configurePin() {
        if (!pin.isEmpty()) { askPin(() -> enterNewPin()); } else enterNewPin();
    }
    private void enterNewPin() {
        EditText field = new EditText(this); field.setHint(t("至少 4 位数字", "Pelo menos 4 dígitos"));
        field.setInputType(2); field.setPadding(30,20,30,20);
        new AlertDialog.Builder(this).setTitle(t("设置员工 PIN", "Definir PIN"))
            .setView(field).setNegativeButton(t("取消", "Cancelar"), null)
            .setPositiveButton(t("保存", "Guardar"), (d,w) -> {
                String p = field.getText().toString();
                if (p.matches("[0-9]{4,12}")) { pin=p; getPreferences(0).edit().putString("pin",pin).apply(); }
                else toast(t("请输入 4-12 位数字", "Introduza 4 a 12 dígitos"));
            }).show();
    }
    private void askPin(Runnable success) {
        EditText field = new EditText(this); field.setInputType(18); field.setPadding(30,20,30,20);
        new AlertDialog.Builder(this).setTitle(t("员工 PIN", "PIN da equipa")).setView(field)
            .setNegativeButton(t("取消", "Cancelar"),null)
            .setPositiveButton(t("确认", "Confirmar"), (d,w) -> {
                if (field.getText().toString().equals(pin)) success.run();
                else toast(t("PIN 错误", "PIN incorreto"));
            }).show();
    }
    private void scan() {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST); return;
        }
        stopCamera(); if(web!=null){web.stopLoading();web.destroy();web=null;} scanBusy=false; base();
        TextView help = new TextView(this); help.setText(t("对准桌台或其他二维码", "Aponte para o código QR"));
        help.setTextColor(coffee); help.setTextSize(19); help.setGravity(Gravity.CENTER); help.setPadding(10,20,10,20); root.addView(help);
        preview = new TextureView(this); root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(button(t("取消", "Cancelar"), this::home));
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){ startCamera(); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s){stopCamera();return true;}
            public void onSurfaceTextureUpdated(SurfaceTexture s){}
        });
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==CAMERA_REQUEST && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) scan();
        else toast(t("需要摄像头权限", "É necessária a permissão da câmara"));
    }
    private void startCamera() {
        try {
            cameraThread = new HandlerThread("umi-camera"); cameraThread.start(); cameraHandler = new Handler(cameraThread.getLooper());
            CameraManager manager = (CameraManager)getSystemService(CAMERA_SERVICE);
            String id = null;
            for(String candidate:manager.getCameraIdList()) {
                Integer facing = manager.getCameraCharacteristics(candidate).get(CameraCharacteristics.LENS_FACING);
                if(facing!=null && facing==CameraCharacteristics.LENS_FACING_BACK){id=candidate;break;}
            }
            if(id==null){toast(t("未找到后置摄像头", "Câmara traseira indisponível"));return;}
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
            reader = ImageReader.newInstance(1280,720,android.graphics.ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(r -> {
                Image img = r.acquireLatestImage(); if(img==null)return;
                try { if(scanBusy)return;
                    Image.Plane y = img.getPlanes()[0]; java.nio.ByteBuffer buffer=y.getBuffer();
                    byte[] bytes=new byte[buffer.remaining()]; buffer.get(bytes);
                    int width=img.getWidth(),height=img.getHeight(),stride=y.getRowStride(),pixel=y.getPixelStride();
                    byte[] compact=new byte[width*height];
                    for(int row=0;row<height;row++)for(int col=0;col<width;col++)compact[row*width+col]=bytes[row*stride+col*pixel];
                    LuminanceSource source = new PlanarYUVLuminanceSource(compact,width,height,0,0,width,height,false);
                    Result result=qrDecoder.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
                    scanBusy=true; String value=result.getText(); runOnUiThread(() -> scanned(value));
                } catch(NotFoundException ignored){} catch(Exception ignored){} finally {img.close();qrDecoder.reset();}
            },cameraHandler);
            manager.openCamera(id,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice d){camera=d; try {
                    SurfaceTexture st=preview.getSurfaceTexture();st.setDefaultBufferSize(1280,720);
                    Surface surface=new Surface(st);
                    CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    request.addTarget(surface);request.addTarget(reader.getSurface());
                    request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    camera.createCaptureSession(Arrays.asList(surface,reader.getSurface()),new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession s){cameraSession=s;try{s.setRepeatingRequest(request.build(),null,cameraHandler);}catch(Exception ignored){}}
                        public void onConfigureFailed(CameraCaptureSession s){toast(t("相机启动失败", "Erro ao iniciar a câmara"));}
                    },cameraHandler);
                }catch(Exception e){toast(e.getMessage());}}
                public void onDisconnected(CameraDevice d){d.close();}
                public void onError(CameraDevice d,int e){d.close();}
            },cameraHandler);
        }catch(Exception e){toast(t("摄像头不可用", "Câmara indisponível")+": "+e.getMessage());}
    }
    private void stopCamera() {
        try {if(cameraSession!=null){cameraSession.close();cameraSession=null;}}catch(Exception ignored){}
        try {if(camera!=null){camera.close();camera=null;}}catch(Exception ignored){}
        try {if(reader!=null){reader.close();reader=null;}}catch(Exception ignored){}
        if(cameraThread!=null){cameraThread.quitSafely();cameraThread=null;cameraHandler=null;}
    }
    private void scanned(String raw) {
        stopCamera();
        String url = normalize(raw);
        if(url==null) {
            new AlertDialog.Builder(this).setTitle(t("二维码内容", "Conteúdo do QR"))
                .setMessage(raw).setPositiveButton(t("完成", "Concluir"),(d,w)->home())
                .setNegativeButton(t("重新扫描", "Ler novamente"),(d,w)->scan()).show();return;
        }
        new AlertDialog.Builder(this).setTitle(t("选择打开方式", "Escolher modo"))
            .setMessage(url).setPositiveButton(t("普通浏览", "Navegação normal"),(d,w)->openBrowser(url,false))
            .setNeutralButton(t("客人模式", "Modo cliente"),(d,w)->{
                if(pin.isEmpty()) { toast(t("请先设置员工 PIN", "Defina primeiro um PIN"));home(); }
                else openBrowser(url,true);
            }).setNegativeButton(t("取消", "Cancelar"),(d,w)->home()).show();
    }
    private String normalize(String value) {
        try {
            Uri uri=Uri.parse(value.trim());
            if(("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme())) && uri.getHost()!=null) return uri.toString();
        }catch(Exception ignored){}return null;
    }
    private String origin(String url) {
        try{Uri u=Uri.parse(url);return u.getScheme()+"://"+u.getHost()+(u.getPort()==-1?"":":"+u.getPort());}catch(Exception e){return "";}
    }
    private void openBrowser(String url,boolean lock) {
        stopCamera();guest=lock;pinnedOrigin=lock?origin(url):null;base();
        toolbar=new LinearLayout(this);toolbar.setOrientation(LinearLayout.HORIZONTAL);toolbar.setBackgroundColor(coffee);root.addView(toolbar);
        if(!lock){
            small("‹",()->{if(web.canGoBack())web.goBack();});
            small("›",()->{if(web.canGoForward())web.goForward();});
            address=new EditText(this);address.setSingleLine(true);address.setTextColor(Color.WHITE);address.setHintTextColor(0xffbbbbbb);
            address.setHint("https://");toolbar.addView(address,new LinearLayout.LayoutParams(0,55,1));
            address.setOnEditorActionListener((v,a,event)->{String typed=address.getText().toString().trim();
                String target=normalize(typed);if(target==null)target="https://www.google.com/search?q="+Uri.encode(typed);
                web.loadUrl(target);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);return true;});
            small("↻",()->web.reload());small("▣",this::scan);small("⌂",this::home);
        }else{
            TextView title=new TextView(this);title.setText("UMI · "+t("客人模式", "Modo cliente"));title.setTextColor(Color.WHITE);
            title.setPadding(18,15,18,15);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            small("🔒",()->askPin(this::endGuest));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();String scheme=u.getScheme();
                if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))return true;
                if(guest && request.isForMainFrame() && !origin(u.toString()).equalsIgnoreCase(pinnedOrigin)){
                    runOnUiThread(()->toast(t("客人模式禁止离开当前网站", "Não pode sair deste site no modo cliente")));return true;
                }
                return false;
            }
            @Override public void onPageFinished(WebView view,String current){if(!guest&&address!=null)address.setText(current);}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsAlert(WebView v,String u,String message,JsResult r){new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("OK",(d,w)->r.confirm()).setOnCancelListener(d->r.cancel()).show();return true;}
        });
        web.loadUrl(url);
    }
    private void small(String title,Runnable action){TextView b=new TextView(this);b.setText(title);b.setTextColor(Color.WHITE);b.setTextSize(18);b.setGravity(Gravity.CENTER);b.setPadding(10,10,10,10);toolbar.addView(b);b.setOnClickListener(v->action.run());}
    private void endGuest() {
        if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.clearHistory();web.clearCache(true);web.clearFormData();}
        CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        home();
    }
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    @Override public void onBackPressed(){if(guest){toast(t("请员工点击锁图标并输入 PIN", "Peça à equipa para desbloquear"));}else if(web!=null&&web.canGoBack())web.goBack();else home();}
    @Override protected void onDestroy(){stopCamera();if(web!=null)web.destroy();super.onDestroy();}
}
