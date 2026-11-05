package com.bpscanner.app;

import android.app.Activity;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.graphics.Color;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private Camera camera;
    private SurfaceView preview;
    private SurfaceHolder holder;
    private TextView bpmText, statusText;
    private Button scanButton;
    private boolean scanning=false;
    private double previous=0;
    private long lastPeak=0;
    private int frames=0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        new android.os.Handler(getMainLooper()).postDelayed(() -> new UpdateManager(this).check(), 3500);
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},7);
    }
    private void buildUi() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28,30,28,24); root.setBackgroundColor(0xFF101116);
        TextView title=new TextView(this); title.setText("BP SCANNER"); title.setTextColor(Color.WHITE); title.setTextSize(27); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,64));
        TextView sub=new TextView(this); sub.setText("پایش آزمایشی ضربان قلب"); sub.setTextColor(0xFFBBBBBB); sub.setTextSize(15); sub.setGravity(Gravity.CENTER);
        root.addView(sub,new LinearLayout.LayoutParams(-1,42));
        preview=new SurfaceView(this); holder=preview.getHolder(); holder.addCallback(this);
        LinearLayout.LayoutParams pv=new LinearLayout.LayoutParams(230,230); pv.gravity=Gravity.CENTER; pv.topMargin=16; pv.bottomMargin=16; root.addView(preview,pv);
        bpmText=new TextView(this); bpmText.setText("-- BPM"); bpmText.setTextColor(0xFFF0183D); bpmText.setTextSize(38); bpmText.setGravity(Gravity.CENTER);
        root.addView(bpmText,new LinearLayout.LayoutParams(-1,70));
        statusText=new TextView(this); statusText.setText("انگشت را آرام روی دوربین و فلش قرار بده."); statusText.setTextColor(Color.LTGRAY); statusText.setGravity(Gravity.CENTER);
        root.addView(statusText,new LinearLayout.LayoutParams(-1,60));
        scanButton=new Button(this); scanButton.setText("شروع اسکن"); scanButton.setTextColor(Color.WHITE);
        scanButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD7193F));
        scanButton.setOnClickListener(v->toggleScan()); LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(-1,58); b.topMargin=16; root.addView(scanButton,b);
        TextView note=new TextView(this); note.setText("هشدار: این نسخه آزمایشی است و فشار خون را اندازه‌گیری نمی‌کند. برای سنجش فشار خون از دستگاه کاف‌دار معتبر استفاده کن.");
        note.setTextColor(0xFFFFB5BF); note.setTextSize(12); note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams n=new LinearLayout.LayoutParams(-1,-2); n.topMargin=22; root.addView(note,n);
        setContentView(root);
    }
    private void toggleScan() {
        if(camera==null){statusText.setText("دوربین در دسترس نیست یا مجوز داده نشده.");return;}
        scanning=!scanning; previous=0;lastPeak=0;frames=0; bpmText.setText("-- BPM");
        scanButton.setText(scanning?"توقف اسکن":"شروع اسکن");
        statusText.setText(scanning?"انگشت را ثابت نگه دار؛ نتیجه صرفاً آزمایشی است.":"اسکن متوقف شد.");
        try { Camera.Parameters p=camera.getParameters(); if(p.getSupportedFlashModes()!=null) {
            p.setFlashMode(scanning && p.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_TORCH)?Camera.Parameters.FLASH_MODE_TORCH:Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(p);
        }} catch(Exception ignored){}
    }
    @Override public void onPreviewFrame(byte[] data,Camera source) {
        if(!scanning||data==null)return;
        Camera.Size s=source.getParameters().getPreviewSize(); int count=Math.min(data.length,s.width*s.height);
        long sum=0; for(int i=0;i<count;i++)sum+=(data[i]&255);
        double v=sum/(double)Math.max(1,count); long now=System.currentTimeMillis(); frames++;
        if(frames>10 && previous>v) {
            if(lastPeak==0) {
                lastPeak=now;
            } else {
                long interval=now-lastPeak;
                lastPeak=now;
                int bpm=(int)(60000/interval);
                if(interval>=350 && interval<=2000 && bpm>=40 && bpm<=200)
                    runOnUiThread(()->{bpmText.setText(bpm+" BPM");statusText.setText("تخمین آزمایشی؛ برای تصمیم پزشکی معتبر نیست.");});
            }
        }
        previous=v;
    }
    private void startCamera() {
        try { camera=Camera.open(); camera.setPreviewDisplay(holder); camera.setPreviewCallback(this); camera.startPreview(); }
        catch(Exception e){statusText.setText("راه‌اندازی دوربین ناموفق بود.");releaseCamera();}
    }
    @Override public void surfaceCreated(SurfaceHolder h){if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED&&camera==null)startCamera();}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int he){}
    @Override public void surfaceDestroyed(SurfaceHolder h){releaseCamera();}
    private void releaseCamera(){if(camera!=null){try{camera.setPreviewCallback(null);camera.stopPreview();camera.release();}catch(Exception ignored){}camera=null;}}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();else statusText.setText("مجوز دوربین لازم است.");}
    @Override protected void onPause(){super.onPause();releaseCamera();}
    @Override protected void onResume(){super.onResume();if(holder!=null&&holder.getSurface().isValid()&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED&&camera==null)startCamera();}
}
