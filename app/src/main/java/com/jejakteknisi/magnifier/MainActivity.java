package com.jejakteknisi.magnifier;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION = 7;

    private PreviewView preview;
    private ImageCapture capture;
    private Camera camera;
    private SeekBar zoomBar;
    private SeekBar exposureBar;
    private TextView zoomText;
    private TextView exposureText;
    private TextView status;
    private TextView detailText;
    private Button torchBtn;
    private Button photoBtn;
    private Button freezeBtn;
    private ImageView freezeImage;

    private boolean torch = false;
    private boolean frozen = false;
    private boolean detailMode = true;
    private int exposureIndex = 0;

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setBackgroundColor(Color.rgb(35, 40, 44));
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);

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

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(3), dp(8), dp(3));
        top.setBackgroundColor(Color.rgb(18, 20, 22));

        ImageView logoView = new ImageView(this);
        logoView.setImageResource(R.drawable.jejak_teknisi_logo);
        logoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logoView.setContentDescription("Logo Jejak Teknisi");
        top.addView(logoView, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView title = new TextView(this);
        title.setText("JEJAK TEKNISI\nMICROSCOPE V2");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(10), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(58), 1));

        Button detailBtn = makeButton("DETAIL\nON");
        top.addView(detailBtn, new LinearLayout.LayoutParams(dp(82), dp(52)));
        root.addView(top);

        FrameLayout cameraBox = new FrameLayout(this);
        preview = new PreviewView(this);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        cameraBox.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        freezeImage = new ImageView(this);
        freezeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        freezeImage.setBackgroundColor(Color.BLACK);
        freezeImage.setVisibility(View.GONE);
        cameraBox.addView(freezeImage, new FrameLayout.LayoutParams(-1, -1));

        status = new TextView(this);
        status.setText("Menyiapkan kamera...");
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setBackgroundColor(Color.argb(160, 0, 0, 0));
        status.setPadding(dp(8), dp(5), dp(8), dp(5));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        statusParams.setMargins(dp(8), dp(8), 0, 0);
        cameraBox.addView(status, statusParams);

        detailText = new TextView(this);
        detailText.setText("DETAIL • EDGE HIGH QUALITY");
        detailText.setTextColor(Color.WHITE);
        detailText.setTextSize(11);
        detailText.setBackgroundColor(Color.argb(150, 0, 0, 0));
        detailText.setPadding(dp(7), dp(4), dp(7), dp(4));
        FrameLayout.LayoutParams detailParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        detailParams.setMargins(0, dp(8), dp(8), 0);
        cameraBox.addView(detailText, detailParams);

        root.addView(cameraBox, new LinearLayout.LayoutParams(-1, 0, 1));

        zoomText = label("Zoom 1.0×", 14);
        root.addView(zoomText, new LinearLayout.LayoutParams(-1, dp(26)));
        zoomBar = new SeekBar(this);
        zoomBar.setMax(100);
        zoomBar.setProgress(0);
        root.addView(zoomBar, new LinearLayout.LayoutParams(-1, dp(38)));

        exposureText = label("Exposure 0", 13);
        root.addView(exposureText, new LinearLayout.LayoutParams(-1, dp(24)));
        exposureBar = new SeekBar(this);
        exposureBar.setMax(12);
        exposureBar.setProgress(6);
        root.addView(exposureBar, new LinearLayout.LayoutParams(-1, dp(34)));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(4), dp(3), dp(4), dp(6));
        controls.setBackgroundColor(Color.rgb(10, 12, 14));

        Button minus = makeButton("−");
        torchBtn = makeButton("🔦\nLampu");
        freezeBtn = makeButton("❄\nBeku");
        photoBtn = makeButton("📸\nFOTO");
        Button focus = makeButton("🎯\nFokus");
        Button plus = makeButton("+");

        controls.addView(minus, new LinearLayout.LayoutParams(0, dp(60), .65f));
        controls.addView(torchBtn, new LinearLayout.LayoutParams(0, dp(60), 1.0f));
        controls.addView(freezeBtn, new LinearLayout.LayoutParams(0, dp(60), 1.0f));
        controls.addView(photoBtn, new LinearLayout.LayoutParams(0, dp(68), 1.45f));
        controls.addView(focus, new LinearLayout.LayoutParams(0, dp(60), 1.0f));
        controls.addView(plus, new LinearLayout.LayoutParams(0, dp(60), .65f));

        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(-1, -2);
        controlParams.setMargins(0, 0, 0, dp(28));
        root.addView(controls, controlParams);
        setContentView(root);

        detailBtn.setOnClickListener(v -> {
            detailMode = !detailMode;
            detailBtn.setText(detailMode ? "DETAIL\nON" : "DETAIL\nOFF");
            detailText.setText(detailMode ? "DETAIL • EDGE HIGH QUALITY" : "NORMAL MODE");
            status.setText(detailMode ? "Mode detail aktif" : "Mode normal aktif");
        });

        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) setZoomFromProgress(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        exposureBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                exposureIndex = p - 6;
                exposureText.setText("Exposure " + (exposureIndex >= 0 ? "+" : "") + exposureIndex);
                if (fromUser) applyExposure();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        minus.setOnClickListener(v -> changeZoom(-0.5f));
        plus.setOnClickListener(v -> changeZoom(0.5f));
        torchBtn.setOnClickListener(v -> toggleTorch());
        freezeBtn.setOnClickListener(v -> toggleFreeze());
        focus.setOnClickListener(v -> {
            if (!frozen && preview != null) focusAt(preview.getWidth() / 2f, preview.getHeight() / 2f);
        });
        photoBtn.setOnClickListener(v -> takePhoto());

        preview.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && !frozen) {
                focusAt(event.getX(), event.getY());
            }
            return true;
        });
    }

    private TextView label(String text, int size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void startCamera() {
        status.setText("Menyiapkan kamera...");
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                ResolutionSelector highest = new ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        .build();

                Preview.Builder previewBuilder = new Preview.Builder()
                        .setResolutionSelector(highest)
                        .setTargetRotation(preview.getDisplay().getRotation());

                Camera2Interop.Extender<Preview> previewInterop = new Camera2Interop.Extender<>(previewBuilder);
                applyCameraQuality(previewInterop);
                Preview previewUseCase = previewBuilder.build();

                ImageCapture.Builder captureBuilder = new ImageCapture.Builder()
                        .setResolutionSelector(highest)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(100)
                        .setTargetRotation(preview.getDisplay().getRotation());

                Camera2Interop.Extender<ImageCapture> captureInterop = new Camera2Interop.Extender<>(captureBuilder);
                applyCameraQuality(captureInterop);
                capture = captureBuilder.build();

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, capture);
                previewUseCase.setSurfaceProvider(preview.getSurfaceProvider());
                updateZoomText();
                applyExposure();
                status.setText("Kamera siap • tap untuk fokus");
            } catch (Exception e) {
                status.setText("Kamera gagal");
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private <T> void applyCameraQuality(Camera2Interop.Extender<T> interop) {
        interop.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        interop.setCaptureRequestOption(CaptureRequest.EDGE_MODE,
                detailMode ? CaptureRequest.EDGE_MODE_HIGH_QUALITY : CaptureRequest.EDGE_MODE_FAST);
        interop.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        interop.setCaptureRequestOption(CaptureRequest.SHADING_MODE,
                CaptureRequest.SHADING_MODE_HIGH_QUALITY);
    }

    private void updateZoomText() {
        if (camera == null || camera.getCameraInfo().getZoomState().getValue() == null) return;
        float current = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
        float max = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        zoomText.setText(String.format(Locale.US, "Zoom %.1f×  (maks %.1f×)", current, max));
    }

    private void setZoomFromProgress(int progress) {
        if (camera == null || camera.getCameraInfo().getZoomState().getValue() == null) return;
        float max = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        if (max <= 1f) return;
        float ratio = 1f + (max - 1f) * progress / 100f;
        camera.getCameraControl().setZoomRatio(ratio);
        updateZoomText();
    }

    private void changeZoom(float delta) {
        if (camera == null || camera.getCameraInfo().getZoomState().getValue() == null) return;
        float current = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
        float max = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        float newZoom = Math.max(1f, Math.min(max, current + delta));
        camera.getCameraControl().setZoomRatio(newZoom);
        if (max > 1f) zoomBar.setProgress((int) ((newZoom - 1f) / (max - 1f) * 100f));
        updateZoomText();
    }

    private void applyExposure() {
        if (camera == null) return;
        try {
            camera.getCameraControl().setExposureCompensationIndex(exposureIndex);
        } catch (Exception ignored) {
            status.setText("Exposure tidak didukung kamera ini");
        }
    }

    private void toggleTorch() {
        if (camera == null) {
            status.setText("Kamera belum siap");
            return;
        }
        if (!camera.getCameraInfo().hasFlashUnit()) {
            status.setText("Flash tidak tersedia");
            return;
        }
        torch = !torch;
        camera.getCameraControl().enableTorch(torch);
        torchBtn.setText(torch ? "🔦\nLampu ON" : "🔦\nLampu");
    }

    private void focusAt(float x, float y) {
        if (camera == null) return;
        MeteringPoint point = preview.getMeteringPointFactory().createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(
                point, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE).build();
        camera.getCameraControl().startFocusAndMetering(action);
        status.setText("Fokus...");
    }

    private void toggleFreeze() {
        if (preview == null) return;
        if (!frozen) {
            Bitmap frame = preview.getBitmap();
            if (frame == null) {
                status.setText("Frame belum siap");
                return;
            }
            freezeImage.setImageBitmap(frame);
            freezeImage.setVisibility(View.VISIBLE);
            frozen = true;
            freezeBtn.setText("▶\nLIVE");
            status.setText("FREEZE • tekan LIVE untuk kembali");
        } else {
            freezeImage.setImageDrawable(null);
            freezeImage.setVisibility(View.GONE);
            frozen = false;
            freezeBtn.setText("❄\nBeku");
            status.setText("LIVE • tap untuk fokus");
        }
    }

    private void takePhoto() {
        if (capture == null) {
            status.setText("Kamera belum siap");
            return;
        }
        photoBtn.setEnabled(false);
        photoBtn.setText("📸\nMEMOTRET...");

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "JejakTeknisi_Microscope_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JejakTeknisi/Microscope");

        ImageCapture.OutputFileOptions output = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build();

        capture.takePicture(output, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) {
                        photoBtn.setEnabled(true);
                        photoBtn.setText("📸\nFOTO");
                        status.setText("Foto tersimpan • kualitas maksimum");
                        showPhotoOptions(result.getSavedUri());
                    }

                    @Override public void onError(@NonNull ImageCaptureException error) {
                        photoBtn.setEnabled(true);
                        photoBtn.setText("📸\nFOTO");
                        status.setText("Foto gagal: " + error.getImageCaptureError());
                    }
                });
    }

    private void showPhotoOptions(Uri uri) {
        if (uri == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Foto berhasil disimpan")
                .setMessage("Pictures/JejakTeknisi/Microscope")
                .setPositiveButton("🔎 Google Lens", (dialog, which) -> sendToGoogleLens(uri))
                .setNeutralButton("🖼 Lihat", (dialog, which) -> openImage(uri))
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void openImage(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "image/jpeg");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            status.setText("Tidak ada aplikasi galeri");
        }
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
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
