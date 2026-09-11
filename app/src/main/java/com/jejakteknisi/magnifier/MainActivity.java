package com.jejakteknisi.magnifier;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION = 7;

    private PreviewView preview;
    private ImageCapture capture;
    private Camera camera;
    private SeekBar zoomBar;
    private TextView zoomText;
    private TextView status;
    private Button torchBtn;
    private Button photoBtn;
    private LinearLayout topBar;
    private LinearLayout controlsBar;
    private boolean torch = false;

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

        // Edge-to-edge: keep the camera immersive, but reserve system-bar space
        // for the header and bottom controls so buttons (including Lampu/Flash)
        // are never covered by the Android navigation bar.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        buildUi();
        applySystemBarInsets();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION
            );
        } else {
            startCamera();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        topBar = top;
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(3), dp(8), dp(3));
        top.setBackgroundColor(Color.rgb(18, 20, 22));

        ImageView logoView = new ImageView(this);
        logoView.setImageResource(R.drawable.jejak_teknisi_logo);
        logoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logoView.setContentDescription("Logo Jejak Teknisi");

        top.addView(
                logoView,
                new LinearLayout.LayoutParams(
                        dp(52),
                        dp(52)
                )
        );

        TextView title = new TextView(this);
        title.setText("JEJAK TEKNISI\nMICROSCOPE V2.1");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(10), 0, 0, 0);

        top.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        dp(58),
                        1
                )
        );

        root.addView(top);

        FrameLayout cameraBox = new FrameLayout(this);

        preview = new PreviewView(this);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        cameraBox.addView(
                preview,
                new FrameLayout.LayoutParams(-1, -1)
        );

        status = new TextView(this);
        status.setText("Menyiapkan kamera...");
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setBackgroundColor(Color.argb(150, 0, 0, 0));
        status.setPadding(dp(8), dp(5), dp(8), dp(5));

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        statusParams.setMargins(dp(8), dp(8), 0, 0);
        cameraBox.addView(status, statusParams);

        root.addView(
                cameraBox,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        zoomText = new TextView(this);
        zoomText.setText("Zoom 1.0×");
        zoomText.setTextColor(Color.WHITE);
        zoomText.setTextSize(15);
        zoomText.setGravity(Gravity.CENTER);
        root.addView(zoomText, new LinearLayout.LayoutParams(-1, dp(30)));

        zoomBar = new SeekBar(this);
        zoomBar.setMax(100);
        zoomBar.setProgress(0);
        root.addView(zoomBar, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout controls = new LinearLayout(this);
        controlsBar = controls;
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(4), dp(4), dp(4), dp(6));
        controls.setBackgroundColor(Color.rgb(10, 12, 14));

        Button minus = makeButton("−");
        torchBtn = makeButton("🔦\nLampu");
        photoBtn = makeButton("📸\nFOTO");
        Button focus = makeButton("🎯\nFokus");
        Button plus = makeButton("+");

        controls.addView(minus, new LinearLayout.LayoutParams(0, dp(62), .75f));
        controls.addView(torchBtn, new LinearLayout.LayoutParams(0, dp(62), 1.15f));
        controls.addView(photoBtn, new LinearLayout.LayoutParams(0, dp(70), 1.55f));
        controls.addView(focus, new LinearLayout.LayoutParams(0, dp(62), 1.15f));
        controls.addView(plus, new LinearLayout.LayoutParams(0, dp(62), .75f));

        LinearLayout.LayoutParams controlParams =
                new LinearLayout.LayoutParams(-1, -2);
        root.addView(controls, controlParams);
        setContentView(root);

        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) setZoomFromProgress(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        minus.setOnClickListener(v -> changeZoom(-0.5f));
        plus.setOnClickListener(v -> changeZoom(0.5f));
        torchBtn.setOnClickListener(v -> toggleTorch());
        focus.setOnClickListener(v -> {
            if (preview != null) {
                focusAt(preview.getWidth() / 2f, preview.getHeight() / 2f);
            }
        });
        photoBtn.setOnClickListener(v -> takePhoto());

        preview.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                focusAt(event.getX(), event.getY());
            }
            return true;
        });
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (topBar != null) {
                topBar.setPadding(dp(8), bars.top + dp(3), dp(8), dp(3));
            }

            if (controlsBar != null) {
                // Extra bottom padding pushes the five controls above the nav bar.
                controlsBar.setPadding(dp(4), dp(4), dp(4), bars.bottom + dp(6));
            }

            return insets;
        });
        ViewCompat.requestApplyInsets(findViewById(android.R.id.content));

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
    }

    private void startCamera() {
        status.setText("Menyiapkan kamera...");

        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview previewUseCase = new Preview.Builder().build();

                capture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(95)
                        .build();

                provider.unbindAll();

                camera = provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase,
                        capture
                );

                previewUseCase.setSurfaceProvider(preview.getSurfaceProvider());
                updateZoomText();
                status.setText("Kamera siap • tap untuk fokus");

            } catch (Exception e) {
                status.setText("Kamera gagal");
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void updateZoomText() {
        if (camera == null) return;
        if (camera.getCameraInfo().getZoomState().getValue() == null) return;

        float current = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
        float max = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();

        zoomText.setText(String.format("Zoom %.1f×  (maks %.1f×)", current, max));
    }

    private void setZoomFromProgress(int progress) {
        if (camera == null) return;
        if (camera.getCameraInfo().getZoomState().getValue() == null) return;

        float max = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        if (max <= 1f) return;

        float ratio = 1f + (max - 1f) * progress / 100f;
        camera.getCameraControl().setZoomRatio(ratio);
        updateZoomText();
    }

    private void changeZoom(float delta) {
        if (camera == null) return;
        if (camera.getCameraInfo().getZoomState().getValue() == null) return;

        float current = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
        float max = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        float newZoom = Math.max(1f, Math.min(max, current + delta));

        camera.getCameraControl().setZoomRatio(newZoom);

        if (max > 1f) {
            zoomBar.setProgress((int) ((newZoom - 1f) / (max - 1f) * 100f));
        }

        updateZoomText();
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
        if (camera == null) {
            status.setText("Kamera belum siap");
            return;
        }

        MeteringPoint point = preview.getMeteringPointFactory().createPoint(x, y);

        FocusMeteringAction action =
                new FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF
                ).build();

        camera.getCameraControl().startFocusAndMetering(action);
        status.setText("Fokus...");
    }

    private void takePhoto() {
        if (capture == null) {
            status.setText("Kamera belum siap");
            return;
        }

        photoBtn.setEnabled(false);
        photoBtn.setText("📸\nMEMOTRET...");

        ContentValues values = new ContentValues();
        values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "Magnifier_" + System.currentTimeMillis() + ".jpg"
        );
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/MagnifierMicroscope"
        );

        ImageCapture.OutputFileOptions output =
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                ).build();

        capture.takePicture(
                output,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults result) {
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
                }
        );
    }

    private void showPhotoOptions(Uri uri) {
        if (uri == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Foto berhasil disimpan")
                .setMessage("Foto tersimpan di Pictures/MagnifierMicroscope")
                .setPositiveButton("🔎 Google Lens",
                        (dialog, which) -> sendToGoogleLens(uri))
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
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else if (status != null) {
                status.setText("Izin kamera diperlukan");
            }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (camera != null) {
                camera.getCameraControl().enableTorch(false);
            }
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
