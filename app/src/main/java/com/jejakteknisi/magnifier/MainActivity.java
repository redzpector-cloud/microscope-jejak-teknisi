package com.jejakteknisi.magnifier;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CaptureRequest;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION = 7;
    private PreviewView preview;
    private ImageView freezeView;
    private ImageCapture capture;
    private Camera camera;
    private SeekBar zoomBar, exposureBar;
    private TextView zoomText, exposureText, status, detailBadge;
    private Button torchBtn, photoBtn, freezeBtn;
    private boolean torch = false;
    private boolean frozen = false;
    private Bitmap frozenBitmap;

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(1), 0, dp(1), 0);
        b.setBackgroundColor(Color.rgb(35, 40, 44));
        return b;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        } else {
            startCamera();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        // Compact branded header.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(2), dp(8), dp(2));
        top.setBackgroundColor(Color.rgb(18, 20, 22));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.jejak_teknisi_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        top.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(this);
        title.setText("JEJAK TEKNISI\nMICROSCOPE V2.1");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(9), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button detail = makeButton("DETAIL\nON");
        detail.setTextSize(12);
        top.addView(detail, new LinearLayout.LayoutParams(dp(82), dp(48)));
        root.addView(top);

        // Camera preview takes the largest possible area.
        FrameLayout cameraBox = new FrameLayout(this);
        preview = new PreviewView(this);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        cameraBox.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        freezeView = new ImageView(this);
        freezeView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        freezeView.setVisibility(View.GONE);
        cameraBox.addView(freezeView, new FrameLayout.LayoutParams(-1, -1));

        detailBadge = overlay("DETAIL • HIGH QUALITY");
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.END);
        badgeLp.setMargins(0, dp(7), dp(7), 0);
        cameraBox.addView(detailBadge, badgeLp);

        status = overlay("Menyiapkan kamera...");
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.START);
        statusLp.setMargins(dp(7), dp(7), 0, 0);
        cameraBox.addView(status, statusLp);

        root.addView(cameraBox, new LinearLayout.LayoutParams(-1, 0, 1));

        // Compact zoom section.
        zoomText = compactText("Zoom 1.0×");
        root.addView(zoomText, new LinearLayout.LayoutParams(-1, dp(25)));
        zoomBar = new SeekBar(this);
        zoomBar.setMax(100);
        zoomBar.setPadding(dp(18), 0, dp(18), 0);
        root.addView(zoomBar, new LinearLayout.LayoutParams(-1, dp(32)));

        // Compact exposure section.
        exposureText = compactText("Exposure 0");
        root.addView(exposureText, new LinearLayout.LayoutParams(-1, dp(24)));
        exposureBar = new SeekBar(this);
        exposureBar.setMax(12);
        exposureBar.setProgress(6);
        exposureBar.setPadding(dp(18), 0, dp(18), 0);
        root.addView(exposureBar, new LinearLayout.LayoutParams(-1, dp(31)));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(4), dp(3), dp(4), dp(3));
        controls.setBackgroundColor(Color.rgb(10, 12, 14));

        Button minus = makeButton("−");
        torchBtn = makeButton("🔦\nLampu");
        freezeBtn = makeButton("❄\nBeku");
        photoBtn = makeButton("📸\nFOTO");
        Button focus = makeButton("🎯\nFokus");
        Button plus = makeButton("+");

        controls.addView(minus, new LinearLayout.LayoutParams(0, dp(58), .62f));
        controls.addView(torchBtn, new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        controls.addView(freezeBtn, new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        controls.addView(photoBtn, new LinearLayout.LayoutParams(0, dp(62), 1.35f));
        controls.addView(focus, new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        controls.addView(plus, new LinearLayout.LayoutParams(0, dp(58), .62f));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(64)));

        setContentView(root);

        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) setZoom(p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        exposureBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) setExposure(p - 6);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        minus.setOnClickListener(v -> stepZoom(-0.5f));
        plus.setOnClickListener(v -> stepZoom(0.5f));
        torchBtn.setOnClickListener(v -> toggleTorch());
        freezeBtn.setOnClickListener(v -> toggleFreeze());
        focus.setOnClickListener(v -> focusAt(preview.getWidth() / 2f, preview.getHeight() / 2f));
        photoBtn.setOnClickListener(v -> takePhoto());

        preview.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP && !frozen) {
                focusAt(e.getX(), e.getY());
            }
            return true;
        });
    }

    private TextView compactText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(Color.BLACK);
        return t;
    }

    private TextView overlay(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(11);
        t.setPadding(dp(7), dp(4), dp(7), dp(4));
        t.setBackgroundColor(Color.argb(125, 0, 0, 0));
        return t;
    }

    private void startCamera() {
        status.setText("Menyiapkan kamera...");
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                ResolutionSelector highest = new ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        .build();

                int rotation = preview.getDisplay() == null ? 0 : preview.getDisplay().getRotation();
                Preview.Builder pb = new Preview.Builder()
                        .setResolutionSelector(highest)
                        .setTargetRotation(rotation);
                Camera2Interop.Extender<Preview> pi = new Camera2Interop.Extender<>(pb);
                setHighQuality(pi);
                Preview previewUseCase = pb.build();

                ImageCapture.Builder cb = new ImageCapture.Builder()
                        .setResolutionSelector(highest)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(100)
                        .setTargetRotation(rotation);
                Camera2Interop.Extender<ImageCapture> ci = new Camera2Interop.Extender<>(cb);
                setHighQuality(ci);
                capture = cb.build();

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase, capture);
                previewUseCase.setSurfaceProvider(preview.getSurfaceProvider());
                updateZoomText();
                setExposure(0);
                status.setText("Kamera siap • tap PCB untuk fokus");
            } catch (Exception e) {
                status.setText("Kamera gagal");
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private <T> void setHighQuality(Camera2Interop.Extender<T> ext) {
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        ext.setCaptureRequestOption(CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_HIGH_QUALITY);
        ext.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        ext.setCaptureRequestOption(CaptureRequest.SHADING_MODE,
                CaptureRequest.SHADING_MODE_HIGH_QUALITY);
    }

    private void updateZoomText() {
        if (camera == null || camera.getCameraInfo().getZoomState().getValue() == null) return;
        float z = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
        float max = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        zoomText.setText(String.format("Zoom %.1f×  •  maks %.1f×", z, max));
    }

    private void setZoom(int progress) {
        if (camera == null || camera.getCameraInfo().getZoomState().getValue() == null) return;
        float max = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        float ratio = 1f + (max - 1f) * progress / 100f;
        camera.getCameraControl().setZoomRatio(ratio);
        updateZoomText();
    }

    private void stepZoom(float delta) {
        if (camera == null || camera.getCameraInfo().getZoomState().getValue() == null) return;
        float z = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
        float max = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        z = Math.max(1f, Math.min(max, z + delta));
        camera.getCameraControl().setZoomRatio(z);
        zoomBar.setProgress(max <= 1f ? 0 : (int) ((z - 1f) / (max - 1f) * 100f));
        updateZoomText();
    }

    private void setExposure(int index) {
        if (camera == null) return;
        int min = camera.getCameraInfo().getExposureState().getExposureCompensationRange().getLower();
        int max = camera.getCameraInfo().getExposureState().getExposureCompensationRange().getUpper();
        int safe = Math.max(min, Math.min(max, index));
        camera.getCameraControl().setExposureCompensationIndex(safe);
        exposureText.setText("Exposure " + (safe > 0 ? "+" + safe : safe));
        int progress = Math.max(0, Math.min(12, safe + 6));
        if (exposureBar.getProgress() != progress) exposureBar.setProgress(progress);
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            status.setText("Lampu flash tidak tersedia");
            return;
        }
        torch = !torch;
        camera.getCameraControl().enableTorch(torch);
        torchBtn.setText(torch ? "🔦\nLampu ON" : "🔦\nLampu");
    }

    private void focusAt(float x, float y) {
        if (camera == null || frozen) return;
        MeteringPoint point = preview.getMeteringPointFactory().createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE).build();
        camera.getCameraControl().startFocusAndMetering(action);
        status.setText("Fokus...");
    }

    private void toggleFreeze() {
        if (preview == null) return;
        if (!frozen) {
            Bitmap bmp = preview.getBitmap();
            if (bmp == null) {
                status.setText("Frame belum siap");
                return;
            }
            frozenBitmap = bmp;
            freezeView.setImageBitmap(bmp);
            freezeView.setVisibility(View.VISIBLE);
            preview.setVisibility(View.INVISIBLE);
            frozen = true;
            freezeBtn.setText("▶\nLIVE");
            status.setText("FRAME BEKU • tekan LIVE untuk kembali");
        } else {
            freezeView.setVisibility(View.GONE);
            preview.setVisibility(View.VISIBLE);
            frozen = false;
            freezeBtn.setText("❄\nBeku");
            status.setText("LIVE • tap PCB untuk fokus");
            if (frozenBitmap != null) {
                frozenBitmap.recycle();
                frozenBitmap = null;
            }
        }
    }

    private void takePhoto() {
        if (frozen && frozenBitmap != null) {
            saveBitmap(frozenBitmap);
            return;
        }
        if (capture == null) {
            status.setText("Kamera belum siap");
            return;
        }
        photoBtn.setEnabled(false);
        photoBtn.setText("📸\nMEMOTRET...");

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "JejakTeknisi_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/JejakTeknisi/Microscope");

        ImageCapture.OutputFileOptions output = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build();
        capture.takePicture(output, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) {
                        photoBtn.setEnabled(true);
                        photoBtn.setText("📸\nFOTO");
                        status.setText("Foto tersimpan");
                        showPhotoOptions(result.getSavedUri());
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException error) {
                        photoBtn.setEnabled(true);
                        photoBtn.setText("📸\nFOTO");
                        status.setText("Foto gagal");
                    }
                });
    }

    private void saveBitmap(Bitmap bitmap) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "JejakTeknisi_Freeze_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/JejakTeknisi/Microscope");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("URI null");
            java.io.OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new Exception("Output null");
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.close();
            status.setText("Frame beku tersimpan");
            showPhotoOptions(uri);
        } catch (Exception e) {
            status.setText("Gagal menyimpan frame");
        }
    }

    private void showPhotoOptions(Uri uri) {
        if (uri == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Foto tersimpan")
                .setMessage("Pictures/JejakTeknisi/Microscope")
                .setPositiveButton("🔎 Google Lens", (d, w) -> sendToGoogleLens(uri))
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void sendToGoogleLens(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage("com.google.android.googlequicksearchbox");
        try {
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            startActivity(Intent.createChooser(intent, "Buka foto dengan"));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else if (status != null) {
                status.setText("Izin kamera diperlukan");
            }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (camera != null) camera.getCameraControl().enableTorch(false);
            if (frozenBitmap != null && !frozenBitmap.isRecycled()) frozenBitmap.recycle();
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
