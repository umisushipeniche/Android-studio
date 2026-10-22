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
    private int wrongAttempts = 0;
    private long blockedUntil = 0;
    // Fixed management recovery credential requested by restaurant owner.
    private static final String ADMIN_RECOVERY_SHA256 = "9966ac246aa570868f183fe36e0e938257762811fb13d797b11a7efeafdd0920";
    private FrameLayout browserFrame;
    private CameraDevice camera;
    private CameraCaptureSession cameraSession;
    private ImageReader reader;
    private TextureView preview;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final MultiFormatReader qrDecoder = new MultiFormatReader();
    private static final int CAMERA_REQUEST = 100;
    private final int coffee = Color.rgb(43, 34, 30);
    private final int gold = Color.rgb(205, 178, 129);
    private String t(String zh, String pt) { return portuguese ? pt : zh; }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        portuguese = getPreferences(0).getBoolean("pt", false);
        pin = getPreferences(0).getString("pin", "");
        getWindow().setStatusBarColor(coffee);
        getWindow().setNavigationBarColor(coffee);
        home();
    }
    private int dp(int x) {return (int)(getResources().getDisplayMetrics().density*x+0.5f);}
    private android.graphics.drawable.GradientDrawable bg(int color,int radius) {
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private void base() {
        root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(coffee);
        setContentView(root);
    }
    private TextView button(String label, Runnable click) {
        TextView b=new TextView(this);b.setText(label);b.setTextColor(coffee);b.setTextSize(18);
        b.setGravity(Gravity.CENTER);b.setBackground(bg(gold,16));b.setPadding(dp(14),dp(19),dp(14),dp(19));
        b.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(24),dp(8),dp(24),dp(8));b.setLayoutParams(lp);
        return b;
    }
    private TextView subtle(String label,Runnable click){
        TextView b=button(label,click);b.setTextColor(0xffe6d9c5);b.setTextSize(15);b.setBackground(bg(0xff3b302b,14));return b;
    }
    private void home() {
        stopCamera();guest=false;pinnedOrigin=null;address=null;
        getWindow().getDecorView().setSystemUiVisibility(0);
        if(web!=null){web.stopLoading();web.destroy();web=null;}
        base();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(68)));
        TextView lang=new TextView(this);lang.setText(t("中文  ▾","Português  ▾"));lang.setTextColor(gold);
        lang.setTextSize(15);lang.setPadding(dp(20),dp(12),dp(22),dp(12));top.addView(lang);
        lang.setOnClickListener(v->{portuguese=!portuguese;getPreferences(0).edit().putBoolean("pt",portuguese).apply();home();});
        Space upper=new Space(this);root.addView(upper,new LinearLayout.LayoutParams(1,0,1));
        TextView heading=new TextView(this);heading.setText("UMI  SUSHI");heading.setTextColor(gold);
        heading.setTypeface(android.graphics.Typeface.SERIF,android.graphics.Typeface.BOLD);heading.setTextSize(32);
        heading.setGravity(Gravity.CENTER);root.addView(heading);
        TextView sub=new TextView(this);sub.setText("B R O W S E R");sub.setTextColor(0xffb6a48f);
        sub.setTextSize(12);sub.setGravity(Gravity.CENTER);sub.setPadding(0,dp(8),0,dp(34));root.addView(sub);
        root.addView(button(t("▣   扫描二维码","▣   Ler código QR"),this::scan));
        root.addView(subtle(t("🌐   普通浏览","🌐   Navegação normal"),()->openBrowser("https://www.google.com",false)));
        Space lower=new Space(this);root.addView(lower,new LinearLayout.LayoutParams(1,0,1));
        root.addView(subtle(t("⚙   员工 PIN 设置 / 重设","⚙   PIN da equipa / Recuperar"),this::pinMenu));
        TextView foot=new TextView(this);foot.setText("UMI Browser  ·  v1.3");foot.setTextColor(0xff8e8073);
        foot.setGravity(Gravity.CENTER);foot.setPadding(0,dp(12),0,dp(18));root.addView(foot);
    }
    private String hash(String input){try{
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte v:bytes)out.append(String.format(java.util.Locale.US,"%02x",v&255));return out.toString();
    }catch(Exception e){return "";}}
    private boolean validPin(String value){return pin.equals(value)||pin.equals("sha256:"+hash(value));}
    private void pinMenu(){
        String[] items={t("设置 / 修改 PIN","Definir / Alterar PIN"),t("忘记 PIN：使用恢复码重设","Esqueceu o PIN: recuperar")};
        new AlertDialog.Builder(this).setTitle(t("员工安全设置","Segurança da equipa"))
          .setItems(items,(d,which)->{if(which==0)configurePin();else resetPin();}).show();
    }
    private void configurePin(){if(!pin.isEmpty())askPin(this::enterNewPin);else enterNewPin();}
    private void enterNewPin(){
        EditText field=new EditText(this);field.setHint(t("4–8 位数字","4–8 dígitos"));field.setInputType(18);
        new AlertDialog.Builder(this).setTitle(t("设置员工 PIN","Definir PIN")).setView(field)
         .setNegativeButton(t("取消","Cancelar"),null).setPositiveButton(t("保存","Guardar"),(d,w)->{
            String value=field.getText().toString();if(!value.matches("[0-9]{4,8}")){toast(t("需要 4–8 位数字","Use 4–8 dígitos"));return;}
            pin="sha256:"+hash(value);getPreferences(0).edit().putString("pin",pin).apply();
            toast(t("PIN 已保存","PIN guardado"));
         }).show();
    }
    // A fixed recovery credential is provided by the restaurant. Never display it in setup UI.
    // This is a shared recovery secret, not hardware-backed authentication; keep APK distribution controlled.
    private void resetPin(){
        if(System.currentTimeMillis()<blockedUntil){toast(t("尝试过多，请稍后重试","Demasiadas tentativas. Aguarde."));return;}
        EditText field=new EditText(this);field.setHint(t("管理员恢复密码","Código de recuperação do administrador"));
        field.setInputType(18);field.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle(t("重设员工 PIN","Repor PIN da equipa")).setView(field)
          .setNegativeButton(t("取消","Cancelar"),null).setPositiveButton(t("验证","Verificar"),(d,w)->{
            String enteredHash=hash(field.getText().toString().trim());
            if(enteredHash.equals(ADMIN_RECOVERY_SHA256)){
                wrongAttempts=0;enterNewPin();
            }else{failedPin();}
          }).show();
    }
    private void failedPin(){wrongAttempts++;if(wrongAttempts>=5){blockedUntil=System.currentTimeMillis()+60000;wrongAttempts=0;toast(t("错误次数过多，60 秒后再试","Aguarde 60 segundos"));}else toast(t("验证失败","Código incorreto"));}
    private void askPin(Runnable success){
        if(System.currentTimeMillis()<blockedUntil){toast(t("请稍后再试","Aguarde"));return;}
        EditText field=new EditText(this);field.setInputType(18);
        new AlertDialog.Builder(this).setTitle(t("员工 PIN","PIN da equipa")).setView(field)
          .setNegativeButton(t("取消","Cancelar"),null).setPositiveButton(t("确认","Confirmar"),(d,w)->{
            if(validPin(field.getText().toString())){wrongAttempts=0;success.run();}
            else failedPin();
          }).show();
    }
    private void scan() {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST); return;
        }
        stopCamera(); if(web!=null){web.stopLoading();web.destroy();web=null;} scanBusy=false; base();
        TextView help = new TextView(this); help.setText(t("对准桌台或其他二维码", "Aponte para o código QR"));
        help.setTextColor(gold); help.setTextSize(19); help.setGravity(Gravity.CENTER); help.setPadding(10,20,10,20); root.addView(help);
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
        openBrowser(url,false);
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
        stopCamera();guest=false;pinnedOrigin=null;base();
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setBackgroundColor(coffee);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(76)));
        small("‹",()->{if(web.canGoBack())web.goBack();});
        small("›",()->{if(web.canGoForward())web.goForward();});
        address=new EditText(this);address.setSingleLine(true);address.setTextColor(0xfff3e8d7);
        address.setHintTextColor(0xffb4a594);address.setHint("https://");address.setTextSize(13);
        toolbar.addView(address,new LinearLayout.LayoutParams(0,dp(64),1));
        address.setOnEditorActionListener((v,a,event)->{
            String typed=address.getText().toString().trim();if(typed.isEmpty())return true;
            String target=normalize(typed);if(target==null)target="https://www.google.com/search?q="+Uri.encode(typed);
            web.loadUrl(target);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);return true;
        });
        small("↻",()->web.reload());small("▣",this::scan);lockButton();
        browserFrame=new FrameLayout(this);root.addView(browserFrame,new LinearLayout.LayoutParams(-1,0,1));
        web=new WebView(this);browserFrame.addView(web,new FrameLayout.LayoutParams(-1,-1));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();String scheme=u.getScheme();
                if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))return true;
                if(guest&&request.isForMainFrame()&&!origin(u.toString()).equalsIgnoreCase(pinnedOrigin)){
                    runOnUiThread(()->toast(t("锁定模式禁止离开当前网站","Navegação restrita neste site")));return true;
                }
                return false;
            }
            @Override public void onPageFinished(WebView view,String current){if(!guest&&address!=null)address.setText(current);}
        });
        web.setWebChromeClient(new WebChromeClient());
        web.setDownloadListener((u,agent,disposition,mime,length)->toast(t("暂不支持文件下载","Transferência de ficheiros indisponível")));
        web.loadUrl(url);
        if(lock)lockBrowser();
    }
    private void lockBrowser(){
        if(pin.isEmpty()){new AlertDialog.Builder(this).setTitle(t("请先设置员工 PIN","Defina primeiro o PIN"))
            .setPositiveButton(t("设置","Definir"),(d,w)->enterNewPin()).setNegativeButton(t("取消","Cancelar"),null).show();return;}
        String current=web.getUrl();if(current==null||normalize(current)==null){toast(t("请先打开网页","Abra primeiro uma página"));return;}
        guest=true;pinnedOrigin=origin(current);toolbar.setVisibility(View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        // Invisible staff-only hotspot: long-press the top-right 64dp corner for 3 seconds.
        TextView unlock=new TextView(this);unlock.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(64),dp(64),Gravity.TOP|Gravity.RIGHT);
        browserFrame.addView(unlock,lp);
        unlock.setOnTouchListener(new View.OnTouchListener(){Handler h=new Handler(Looper.getMainLooper());Runnable task;
            public boolean onTouch(View v,android.view.MotionEvent e){
                if(e.getAction()==MotionEvent.ACTION_DOWN){task=()->askPin(()->unlockBrowser(unlock));h.postDelayed(task,3000);return true;}
                if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){if(task!=null)h.removeCallbacks(task);return true;}
                return true;
            }
        });
    }
    private void unlockBrowser(View hotspot){guest=false;pinnedOrigin=null;browserFrame.removeView(hotspot);
        toolbar.setVisibility(View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(0);
        if(address!=null&&web!=null)address.setText(web.getUrl());
        new AlertDialog.Builder(this).setTitle(t("已解锁","Desbloqueado"))
          .setMessage(t("扫描下一桌，或继续浏览当前网页。","Leia o QR da próxima mesa ou continue a navegar."))
          .setPositiveButton(t("▣  扫描下一桌","▣  Ler próxima mesa"),(d,w)->scan())
          .setNegativeButton(t("继续浏览","Continuar a navegar"),null).show();
    }
    private void small(String title,Runnable action){TextView b=new TextView(this);b.setText(title);b.setTextColor(gold);
        b.setTextSize(27);b.setGravity(Gravity.CENTER);b.setMinWidth(dp(52));b.setMinHeight(dp(64));
        b.setPadding(dp(8),dp(8),dp(8),dp(8));toolbar.addView(b);b.setOnClickListener(v->action.run());}
    private void lockButton(){TextView b=new TextView(this);
        b.setText(t("🔒上锁","🔒Bloquear"));b.setTextColor(coffee);b.setTextSize(19);
        b.setTypeface(null,android.graphics.Typeface.BOLD);b.setGravity(Gravity.CENTER);
        b.setBackground(bg(gold,12));b.setMinHeight(dp(66));b.setMinWidth(dp(114));
        b.setPadding(dp(10),dp(8),dp(10),dp(8));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(66));lp.setMargins(dp(4),0,dp(7),0);
        toolbar.addView(b,lp);b.setOnClickListener(v->lockBrowser());}
    private void endGuest() {
        if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.clearHistory();web.clearCache(true);web.clearFormData();}
        CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        home();
    }
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    @Override public void onBackPressed(){if(guest){toast(t("请员工长按右上角 3 秒解锁", "Peça à equipa para desbloquear"));}else if(web!=null&&web.canGoBack())web.goBack();else home();}
    @Override protected void onDestroy(){stopCamera();if(web!=null)web.destroy();super.onDestroy();}
}
