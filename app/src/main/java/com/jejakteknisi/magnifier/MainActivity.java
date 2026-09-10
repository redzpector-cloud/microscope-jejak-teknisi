```java
package com.jejakteknisi.magnifier;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    PreviewView preview;
    ImageCapture capture;
    Camera camera;
    SeekBar zoomBar;
    TextView zoomText, modeText, status;
    Button torchBtn, photoBtn;

    boolean torch = false;
    boolean microscope = false;

    int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + .5f);
    }

    Button button(String text) {
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
    public void onCreate(Bundle state) {
        super.onCreate(state);

        buildUi();

        // Android 15 / targetSdk 35 edge-to-edge
        View rootView = findViewById(android.R.id.content);

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());

            v.setPadding(
                    bars.left,
                    bars.top,
                    bars.right,
                    bars.bottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(rootView);

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    7
            );

        } else {
            startCamera();
        }
    }

    void buildUi() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        // =========================
        // TOP BAR
        // =========================

        LinearLayout top = new LinearLayout(this);

        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(
                dp(8),
                dp(3),
                dp(8),
                dp(3)
        );

        top.setBackgroundColor(
                Color.rgb(18, 20, 22)
        );

        modeText = new TextView(this);

        modeText.setText("🔬 MICROSCOPE");
        modeText.setTextColor(Color.WHITE);
        modeText.setTextSize(18);
        modeText.setGravity(Gravity.CENTER_VERTICAL);

        top.addView(
                modeText,
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1
                )
        );

        Button mode = button("MODE");

        top.addView(
                mode,
                new LinearLayout.LayoutParams(
                        dp(90),
                        dp(50)
                )
        );

        root.addView(top);

        // =========================
        // CAMERA
        // =========================

        FrameLayout cameraBox = new FrameLayout(this);

        preview = new PreviewView(this);

        preview.setScaleType(
                PreviewView.ScaleType.FILL_CENTER
        );

        cameraBox.addView(
                preview,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        status = new TextView(this);

        status.setText("Menyiapkan kamera...");
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);

        status.setPadding(
                dp(8),
                dp(5),
                dp(8),
                dp(5)
        );

        FrameLayout.LayoutParams statusLp =
                new FrameLayout.LayoutParams(
                        -2,
                        -2,
                        Gravity.TOP | Gravity.START
                );

        statusLp.setMargins(
                dp(8),
                dp(8),
                0,
                0
        );

        cameraBox.addView(
                status,
                statusLp
        );

        root.addView(
                cameraBox,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        // =========================
        // ZOOM TEXT
        // =========================

        zoomText = new TextView(this);

        zoomText.setText("Zoom 1.0×");
        zoomText.setTextColor(Color.WHITE);
        zoomText.setGravity(Gravity.CENTER);
        zoomText.setTextSize(15);

        root.addView(
                zoomText,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(30)
                )
        );

        // =========================
        // ZOOM BAR
        // =========================

        zoomBar = new SeekBar(this);

        zoomBar.setMax(100);

        root.addView(
                zoomBar,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(42)
                )
        );

        // =========================
        // BOTTOM CONTROLS
        // =========================

        LinearLayout controls = new LinearLayout(this);

        controls.setGravity(Gravity.CENTER);

        controls.setPadding(
                dp(4),
                dp(4),
                dp(4),
                dp(6)
        );

        controls.setBackgroundColor(
                Color.rgb(10, 12, 14)
        );

        Button minus = button("−");
        torchBtn = button("🔦\nLampu");
        photoBtn = button("📸\nFOTO");
        Button focus = button("🎯\nFokus");
        Button plus = button("+");

        controls.addView(
                minus,
                new LinearLayout.LayoutParams(
                        0,
                        dp(62),
                        0.75f
                )
        );

        controls.addView(
                torchBtn,
                new LinearLayout.LayoutParams(
                        0,
                        dp(62),
                        1.15f
                )
        );

        controls.addView(
                photoBtn,
                new LinearLayout.LayoutParams(
                        0,
                        dp(70),
                        1.55f
                )
        );

        controls.addView(
                focus,
                new LinearLayout.LayoutParams(
                        0,
                        dp(62),
                        1.15f
                )
        );

        controls.addView(
                plus,
                new LinearLayout.LayoutParams(
                        0,
                        dp(62),
                        0.75f
                )
        );

        root.addView(controls);

        setContentView(root);

        // =========================
        // MODE
        // =========================

        mode.setOnClickListener(v -> {

            microscope = !microscope;

            modeText.setText(
                    microscope
                            ? "🔬 MICROSCOPE"
                            : "🔍 KACA PEMBESAR"
            );
        });

        // =========================
        // ZOOM
        // =========================

        zoomBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar s,
                            int p,
                            boolean fromUser) {

                        zoom(p);
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar s) {
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar s) {
                    }
                }
        );

        minus.setOnClickListener(
                v -> step(-.5f)
        );

        plus.setOnClickListener(
                v -> step(.5f)
        );

        torchBtn.setOnClickListener(
                v -> toggleTorch()
        );

        focus.setOnClickListener(
                v -> focusAt(
                        preview.getWidth() / 2f,
                        preview.getHeight() / 2f
                )
        );

        photoBtn.setOnClickListener(
                v -> takePhoto()
        );

        // =========================
        // TAP TO FOCUS
        // =========================

        preview.setOnTouchListener((v, e) -> {

            if (e.getAction() == MotionEvent.ACTION_UP) {

                focusAt(
                        e.getX(),
                        e.getY()
                );
            }

            return true;
        });
    }

    // =========================================================
    // START CAMERA
    // =========================================================

    void startCamera() {

        ProcessCameraProvider.getInstance(this)
                .addListener(() -> {

                    try {

                        ProcessCameraProvider provider =
                                ProcessCameraProvider
                                        .getInstance(this)
                                        .get();

                        // Kamera menggunakan resolusi tertinggi
                        // yang tersedia pada perangkat.

                        ResolutionSelector highest =
                                new ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                                ResolutionStrategy
                                                        .HIGHEST_AVAILABLE_STRATEGY
                                        )
                                        .build();

                        // =========================
                        // PREVIEW
                        // =========================

                        Preview.Builder previewBuilder =
                                new Preview.Builder()
                                        .setResolutionSelector(highest)
                                        .setTargetRotation(
                                                preview
                                                        .getDisplay()
                                                        .getRotation()
                                        );

                        Camera2Interop.Extender<Preview>
                                previewInterop =
                                new Camera2Interop.Extender<>(
                                        previewBuilder
                                );

                        // Continuous autofocus
                        previewInterop.setCaptureRequestOption(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest
                                        .CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        );

                        // Edge enhancement
                        previewInterop.setCaptureRequestOption(
                                CaptureRequest.EDGE_MODE,
                                CaptureRequest
                                        .EDGE_MODE_HIGH_QUALITY
                        );

                        // Noise reduction
                        previewInterop.setCaptureRequestOption(
                                CaptureRequest.NOISE_REDUCTION_MODE,
                                CaptureRequest
                                        .NOISE_REDUCTION_MODE_HIGH_QUALITY
                        );

                        // Shading correction
                        previewInterop.setCaptureRequestOption(
                                CaptureRequest.SHADING_MODE,
                                CaptureRequest
                                        .SHADING_MODE_HIGH_QUALITY
                        );

                        Preview previewUseCase =
                                previewBuilder.build();

                        // =========================
                        // IMAGE CAPTURE
                        // =========================

                        ImageCapture.Builder captureBuilder =
                                new ImageCapture.Builder()
                                        .setResolutionSelector(highest)
                                        .setCaptureMode(
                                                ImageCapture
                                                        .CAPTURE_MODE_MAXIMIZE_QUALITY
                                        )
                                        .setJpegQuality(100)
                                        .setTargetRotation(
                                                preview
                                                        .getDisplay()
                                                        .getRotation()
                                        );

                        Camera2Interop.Extender<ImageCapture>
                                captureInterop =
                                new Camera2Interop.Extender<>(
                                        captureBuilder
                                );

                        captureInterop.setCaptureRequestOption(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest
                                        .CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        );

                        captureInterop.setCaptureRequestOption(
                                CaptureRequest.EDGE_MODE,
                                CaptureRequest
                                        .EDGE_MODE_HIGH_QUALITY
                        );

                        captureInterop.setCaptureRequestOption(
                                CaptureRequest.NOISE_REDUCTION_MODE,
                                CaptureRequest
                                        .NOISE_REDUCTION_MODE_HIGH_QUALITY
                        );

                        captureInterop.setCaptureRequestOption(
                                CaptureRequest.SHADING_MODE,
                                CaptureRequest
                                        .SHADING_MODE_HIGH_QUALITY
                        );

                        capture = captureBuilder.build();

                        // =========================
                        // BIND CAMERA
                        // =========================

                        provider.unbindAll();

                        camera = provider.bindToLifecycle(
                                this,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                previewUseCase,
                                capture
                        );

                        previewUseCase.setSurfaceProvider(
                                preview.getSurfaceProvider()
                        );

                        updateZoomText();

                        status.setText(
                                "Kamera siap • tap untuk fokus"
                        );

                    } catch (Exception e) {

                        status.setText(
                                "Kamera gagal"
                        );

                        e.printStackTrace();
                    }

                }, ContextCompat.getMainExecutor(this));
    }

    // =========================================================
    // ZOOM TEXT
    // =========================================================

    void updateZoomText() {

        if (camera == null) return;

        float max =
                camera.getCameraInfo()
                        .getZoomState()
                        .getValue()
                        .getMaxZoomRatio();

        float z =
                camera.getCameraInfo()
                        .getZoomState()
                        .getValue()
                        .getZoomRatio();

        zoomText.setText(
                String.format(
                        "Zoom %.1f×  (maks %.1f×)",
                        z,
                        max
                )
        );
    }

    // =========================================================
    // ZOOM SLIDER
    // =========================================================

    void zoom(int progress) {

        if (camera == null) return;

        float max =
                camera.getCameraInfo()
                        .getZoomState()
                        .getValue()
                        .getMaxZoomRatio();

        float zoom =
                1f +
                        (max - 1f)
                                * progress
                                / 100f;

        camera.getCameraControl()
                .setZoomRatio(zoom);

        updateZoomText();
    }

    // =========================================================
    // ZOOM BUTTON
    // =========================================================

    void step(float delta) {

        if (camera == null) return;

        float z =
                camera.getCameraInfo()
                        .getZoomState()
                        .getValue()
                        .getZoomRatio();

        float max =
                camera.getCameraInfo()
                        .getZoomState()
                        .getValue()
                        .getMaxZoomRatio();

        z = Math.max(
                1f,
                Math.min(max, z + delta)
        );

        camera.getCameraControl()
                .setZoomRatio(z);

        zoomBar.setProgress(
                max <= 1f
                        ? 0
                        : (int)
                                ((z - 1f)
                                        / (max - 1f)
                                        * 100f)
        );

        updateZoomText();
    }

    // =========================================================
    // TORCH
    // =========================================================

    void toggleTorch() {

        if (camera == null ||
                !camera.getCameraInfo().hasFlashUnit()) {

            status.setText(
                    "Flash tidak tersedia"
            );

            return;
        }

        torch = !torch;

        camera.getCameraControl()
                .enableTorch(torch);

        torchBtn.setText(
                torch
                        ? "🔦\nLampu ON"
                        : "🔦\nLampu"
        );
    }

    // =========================================================
    // FOCUS
    // =========================================================

    void focusAt(float x, float y) {

        if (camera == null) return;

        MeteringPoint point =
                preview
                        .getMeteringPointFactory()
                        .createPoint(x, y);

        camera.getCameraControl()
                .startFocusAndMetering(
                        new FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF
                        ).build()
                );

        status.setText(
                "Fokus..."
        );
    }

    // =========================================================
    // TAKE PHOTO
    // =========================================================

    void takePhoto() {

        if (capture == null) return;

        photoBtn.setEnabled(false);

        photoBtn.setText(
                "📸\nMEMOTRET..."
        );

        ContentValues values =
                new ContentValues();

        values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "Magnifier_" +
                        System.currentTimeMillis() +
                        ".jpg"
        );

        values.put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
        );

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

                        photoBtn.setText(
                                "📸\nFOTO"
                        );

                        status.setText(
                                "Foto tersimpan"
                        );

                        showPhotoOptions(
                                result.getSavedUri()
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull ImageCaptureException error) {

                        photoBtn.setEnabled(true);

                        photoBtn.setText(
                                "📸\nFOTO"
                        );

                        status.setText(
                                "Foto gagal"
                        );

                        error.printStackTrace();
                    }
                }
        );
    }

    // =========================================================
    // PHOTO OPTIONS
    // =========================================================

    void showPhotoOptions(Uri uri) {

        if (uri == null) return;

        new AlertDialog.Builder(this)
                .setTitle(
                        "Foto berhasil disimpan"
                )
                .setMessage(
                        "Foto tersimpan di " +
                                "Pictures/MagnifierMicroscope"
                )
                .setPositiveButton(
                        "🔎 Google Lens",
                        (dialog, which) ->
                                sendToGoogleLens(uri)
                )
                .setNegativeButton(
                        "Tutup",
                        null
                )
                .show();
    }

    // =========================================================
    // GOOGLE LENS
    // =========================================================

    void sendToGoogleLens(Uri uri) {

        Intent intent =
                new Intent(Intent.ACTION_SEND);

        intent.setType("image/jpeg");

        intent.putExtra(
                Intent.EXTRA_STREAM,
                uri
        );

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        intent.setPackage(
                "com.google.android.googlequicksearchbox"
        );

        try {

            startActivity(intent);

        } catch (Exception e) {

            intent.setPackage(null);

            startActivity(
                    Intent.createChooser(
                            intent,
                            "Buka foto dengan"
                    )
            );
        }
    }

    // =========================================================
    // CAMERA PERMISSION
    // =========================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == 7 &&
                grantResults.length > 0 &&
                grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {

            startCamera();
        }
    }
}
```
