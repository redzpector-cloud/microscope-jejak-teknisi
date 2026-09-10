package com.jejakteknisi.magnifier;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
 PreviewView preview; ImageCapture capture; Camera camera; SeekBar zoomBar;
 TextView zoomText,modeText,status; Button torchBtn; boolean torch=false,microscope=false;
 int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 Button b(String s){Button x=new Button(this);x.setText(s);x.setTextColor(Color.WHITE);x.setTextSize(13);x.setAllCaps(false);x.setBackgroundColor(Color.rgb(35,40,44));return x;}

 @Override public void onCreate(Bundle x){super.onCreate(x);ui(); if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},7);else start();}

 void ui(){
  LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setBackgroundColor(Color.BLACK);
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(3),dp(8),dp(3));top.setBackgroundColor(Color.rgb(18,20,22));
  modeText=new TextView(this);modeText.setText("🔍 KACA PEMBESAR");modeText.setTextColor(Color.WHITE);modeText.setTextSize(18);top.addView(modeText,new LinearLayout.LayoutParams(0,dp(50),1));
  Button mode=b("MODE");top.addView(mode,new LinearLayout.LayoutParams(dp(80),dp(48)));r.addView(top);

  FrameLayout f=new FrameLayout(this);preview=new PreviewView(this);preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);f.addView(preview,new FrameLayout.LayoutParams(-1,-1));
  status=new TextView(this);status.setText("Menyiapkan kamera...");status.setTextColor(Color.WHITE);status.setTextSize(12);status.setPadding(dp(8),dp(5),dp(8),dp(5));
  FrameLayout.LayoutParams q=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);q.setMargins(dp(8),dp(8),0,0);f.addView(status,q);r.addView(f,new LinearLayout.LayoutParams(-1,0,1));

  zoomText=new TextView(this);zoomText.setText("Zoom 1.0×");zoomText.setTextColor(Color.WHITE);zoomText.setGravity(Gravity.CENTER);zoomText.setTextSize(15);r.addView(zoomText,new LinearLayout.LayoutParams(-1,dp(30)));
  zoomBar=new SeekBar(this);zoomBar.setMax(100);r.addView(zoomBar,new LinearLayout.LayoutParams(-1,dp(42)));

  LinearLayout c=new LinearLayout(this);c.setGravity(Gravity.CENTER);
  Button minus=b("−");torchBtn=b("🔦 Lampu");Button photo=b("📸 FOTO");Button focus=b("🎯 Fokus");Button plus=b("+");
  c.addView(minus,new LinearLayout.LayoutParams(dp(55),dp(55)));c.addView(torchBtn,new LinearLayout.LayoutParams(dp(85),dp(55)));c.addView(photo,new LinearLayout.LayoutParams(dp(105),dp(55)));c.addView(focus,new LinearLayout.LayoutParams(dp(85),dp(55)));c.addView(plus,new LinearLayout.LayoutParams(dp(55),dp(55)));r.addView(c);setContentView(r);

  mode.setOnClickListener(v->{microscope=!microscope;modeText.setText(microscope?"🔬 MICROSCOPE":"🔍 KACA PEMBESAR");});
  zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){zoom(p);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
  minus.setOnClickListener(v->step(-.5f));plus.setOnClickListener(v->step(.5f));torchBtn.setOnClickListener(v->torch());focus.setOnClickListener(v->focusAt(preview.getWidth()/2f,preview.getHeight()/2f));photo.setOnClickListener(v->photo());
  preview.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP)focusAt(e.getX(),e.getY());return true;});
 }
 void start(){ProcessCameraProvider.getInstance(this).addListener(()->{try{ProcessCameraProvider p=ProcessCameraProvider.getInstance(this).get();Preview pr=new Preview.Builder().setTargetRotation(preview.getDisplay().getRotation()).build();capture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setTargetRotation(preview.getDisplay().getRotation()).build();p.unbindAll();camera=p.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,pr,capture);pr.setSurfaceProvider(preview.getSurfaceProvider());update();status.setText("Kamera siap • tap untuk fokus");}catch(Exception e){status.setText("Kamera gagal");}},ContextCompat.getMainExecutor(this));}
 void update(){if(camera==null)return;float min=camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio(),max=camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio(),z=camera.getCameraInfo().getZoomState().getValue().getZoomRatio();zoomText.setText(String.format("Zoom %.1f×  (maks %.1f×)",z,max));}
 void zoom(int p){if(camera==null)return;float min=1,max=camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();camera.getCameraControl().setZoomRatio(min+(max-min)*p/100f);update();}
 void step(float d){if(camera==null)return;float z=camera.getCameraInfo().getZoomState().getValue().getZoomRatio(),max=camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();z=Math.max(1,Math.min(max,z+d));camera.getCameraControl().setZoomRatio(z);zoomBar.setProgress((int)((z-1)/(max-1)*100));}
 void torch(){if(camera==null||!camera.getCameraInfo().hasFlashUnit()){status.setText("Flash tidak tersedia");return;}torch=!torch;camera.getCameraControl().enableTorch(torch);torchBtn.setText(torch?"🔦 Lampu ON":"🔦 Lampu");}
 void focusAt(float x,float y){if(camera==null)return;MeteringPoint pt=preview.getMeteringPointFactory().createPoint(x,y);camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(pt,FocusMeteringAction.FLAG_AF).build());status.setText("Fokus...");}
 void photo(){if(capture==null)return;ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,"Magnifier_"+System.currentTimeMillis()+".jpg");v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MagnifierMicroscope");ImageCapture.OutputFileOptions o=new ImageCapture.OutputFileOptions.Builder(getContentResolver(),MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v).build();capture.takePicture(o,ContextCompat.getMainExecutor(this),new ImageCapture.OnImageSavedCallback(){public void onImageSaved(@NonNull ImageCapture.OutputFileResults r){status.setText("Foto tersimpan");}public void onError(@NonNull ImageCaptureException e){status.setText("Foto gagal");}});}
 @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)start();}
}