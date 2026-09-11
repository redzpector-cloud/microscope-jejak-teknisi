package com.jejakteknisi.magnifier;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.view.View;
import android.view.ViewParent;
import android.view.ScaleGestureDetector;
import android.graphics.Matrix;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import androidx.annotation.NonNull;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.camera2.interop.Camera2Interop;
import android.hardware.camera2.CaptureRequest;
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
    private TextView exposureText;
    private SeekBar exposureBar;
    private Button torchBtn;
    private Button photoBtn;
    private Button freezeBtn;
    private ImageView freezeView;
    private boolean frozen = false;
    private Bitmap frozenBitmap;
    private float frozenZoom = 1f;
    private float frozenPanX = 0f;
    private float frozenPanY = 0f;
    private float lastTouchX;
    private float lastTouchY;
    private boolean movingFrozen = false;
    private ScaleGestureDetector frozenScaleDetector;
    private final Matrix frozenMatrix = new Matrix();
    private Button detailBtn;
    private Button autoExposureBtn;
    private SeekBar detailBar;
    private TextView detailText;
    private boolean detailOn = true;
    private LinearLayout topBar;
    private LinearLayout controlsBar;
    private boolean torch = false;

    // V2.5 PCB inspection / annotation
    private FrameLayout cameraBox;
    private AnnotationView annotationView;
    private LinearLayout annotationBar;
    private enum AnnotationMode { NONE, SELECT, MARKER, ARROW, CIRCLE, TEXT, OCR }
    private AnnotationMode annotationMode = AnnotationMode.NONE;

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

    private TextView makeInfoText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
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
        title.setText("JEJAK TEKNISI\nMICROSCOPE V2.7");
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

        detailBtn = makeButton("DETAIL\nON");
        detailBtn.setTextSize(13);
        top.addView(detailBtn, new LinearLayout.LayoutParams(dp(92), dp(58)));

        root.addView(top);

        cameraBox = new FrameLayout(this);

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

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(dp(8), 0, dp(8), 0);
        infoRow.setBackgroundColor(Color.BLACK);

        zoomText = makeInfoText("Zoom 1.0×");
        exposureText = makeInfoText("Exposure 0 • NORMAL");
        infoRow.addView(zoomText, new LinearLayout.LayoutParams(0, dp(30), 1));
        infoRow.addView(exposureText, new LinearLayout.LayoutParams(0, dp(30), 1));
        root.addView(infoRow, new LinearLayout.LayoutParams(-1, dp(30)));

        zoomBar = new SeekBar(this);
        zoomBar.setMax(100);
        zoomBar.setProgress(0);
        root.addView(zoomBar, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout exposureRow = new LinearLayout(this);
        exposureRow.setOrientation(LinearLayout.HORIZONTAL);
        exposureRow.setGravity(Gravity.CENTER_VERTICAL);
        exposureRow.setPadding(dp(8), 0, dp(8), 0);
        exposureBar = new SeekBar(this);
        exposureBar.setMax(8);
        exposureBar.setProgress(4);
        exposureRow.addView(exposureBar, new LinearLayout.LayoutParams(0, dp(40), 1));
        autoExposureBtn = makeButton("AUTO");
        autoExposureBtn.setTextSize(12);
        exposureRow.addView(autoExposureBtn, new LinearLayout.LayoutParams(dp(68), dp(40)));
        root.addView(exposureRow, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout detailRow = new LinearLayout(this);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(Gravity.CENTER_VERTICAL);
        detailRow.setPadding(dp(8), 0, dp(8), 0);
        detailText = makeInfoText("Detail 80%");
        detailRow.addView(detailText, new LinearLayout.LayoutParams(dp(88), dp(38)));
        detailBar = new SeekBar(this);
        detailBar.setMax(100);
        detailBar.setProgress(80);
        detailRow.addView(detailBar, new LinearLayout.LayoutParams(0, dp(38), 1));
        root.addView(detailRow, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout controls = new LinearLayout(this);
        controlsBar = controls;
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(4), dp(4), dp(4), dp(6));
        controls.setBackgroundColor(Color.rgb(10, 12, 14));

        Button minus = makeButton("−");
        torchBtn = makeButton("🔦\nLampu");
        freezeBtn = makeButton("❄️\nBeku");
        photoBtn = makeButton("📸\nFOTO");
        Button focus = makeButton("🎯\nFokus");
        Button plus = makeButton("+");

        controls.addView(minus, new LinearLayout.LayoutParams(0, dp(62), .55f));
        controls.addView(torchBtn, new LinearLayout.LayoutParams(0, dp(62), 1.0f));
        controls.addView(freezeBtn, new LinearLayout.LayoutParams(0, dp(62), 1.0f));
        controls.addView(photoBtn, new LinearLayout.LayoutParams(0, dp(70), 1.25f));
        controls.addView(focus, new LinearLayout.LayoutParams(0, dp(62), 1.0f));
        controls.addView(plus, new LinearLayout.LayoutParams(0, dp(62), .55f));

        LinearLayout.LayoutParams controlParams =
                new LinearLayout.LayoutParams(-1, -2);
        root.addView(controls, controlParams);
        setContentView(root);

        frozenScaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (!frozen) return false;
                frozenZoom = Math.max(1f, Math.min(8f, frozenZoom * detector.getScaleFactor()));
                updateFrozenImage();
                return true;
            }
        });

        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (frozen) setFrozenZoomFromProgress(progress);
                    else setZoomFromProgress(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        exposureBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) setExposure(progress - 4);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        detailBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                detailText.setText("Detail " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        autoExposureBtn.setOnClickListener(v -> setExposure(0));
        detailBtn.setOnClickListener(v -> {
            detailOn = !detailOn;
            detailBtn.setText(detailOn ? "DETAIL\nON" : "DETAIL\nOFF");
            if (camera != null) {
                status.setText(detailOn ? "Detail high quality ON" : "Detail normal");
            }
        });

        minus.setOnClickListener(v -> {
            if (frozen) changeFrozenZoom(-0.5f); else changeZoom(-0.5f);
        });
        plus.setOnClickListener(v -> {
            if (frozen) changeFrozenZoom(0.5f); else changeZoom(0.5f);
        });
        torchBtn.setOnClickListener(v -> toggleTorch());
        freezeBtn.setOnClickListener(v -> toggleFreeze());
        focus.setOnClickListener(v -> {
            if (preview != null) {
                focusAt(preview.getWidth() / 2f, preview.getHeight() / 2f);
            }
        });
        photoBtn.setOnClickListener(v -> takePhoto());

        preview.setOnTouchListener((v, event) -> {
            if (frozen) {
                frozenScaleDetector.onTouchEvent(event);
                if (event.getPointerCount() == 1) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            lastTouchX = event.getX();
                            lastTouchY = event.getY();
                            movingFrozen = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getX() - lastTouchX;
                            float dy = event.getY() - lastTouchY;
                            if (Math.abs(dx) + Math.abs(dy) > dp(2)) movingFrozen = true;
                            frozenPanX += dx;
                            frozenPanY += dy;
                            lastTouchX = event.getX();
                            lastTouchY = event.getY();
                            updateFrozenImage();
                            return true;
                        case MotionEvent.ACTION_UP:
                            return true;
                    }
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                focusAt(event.getX(), event.getY());
            }
            return true;
        });
    }


    private void showAnnotationTools() {
        if (annotationBar != null) return;

        annotationBar = new LinearLayout(this);
        annotationBar.setOrientation(LinearLayout.VERTICAL);
        annotationBar.setGravity(Gravity.CENTER);
        annotationBar.setPadding(dp(4), dp(3), dp(4), dp(3));
        annotationBar.setBackgroundColor(Color.rgb(22, 24, 27));

        LinearLayout toolRow1 = new LinearLayout(this);
        toolRow1.setOrientation(LinearLayout.HORIZONTAL);
        toolRow1.setGravity(Gravity.CENTER);
        LinearLayout toolRow2 = new LinearLayout(this);
        toolRow2.setOrientation(LinearLayout.HORIZONTAL);
        toolRow2.setGravity(Gravity.CENTER);
        LinearLayout optionRow = new LinearLayout(this);
        optionRow.setOrientation(LinearLayout.HORIZONTAL);
        optionRow.setGravity(Gravity.CENTER);

        Button pan = makeButton("✋\nGeser");
        Button select = makeButton("☝\nPilih");
        Button marker = makeButton("●\nTitik");
        Button arrow = makeButton("➜\nPanah");
        Button circle = makeButton("○\nLingkar");
        Button text = makeButton("T\nTeks");
        Button ocr = makeButton("🔎\nOCR");
        Button undo = makeButton("↩\nUndo");
        Button clear = makeButton("✕\nHapus");
        Button save = makeButton("💾\nSimpan");
        Button share = makeButton("↗\nShare");

        Button[] row1 = {pan, select, marker, arrow, circle, text};
        for (Button b : row1) {
            b.setTextSize(10);
            toolRow1.addView(b, new LinearLayout.LayoutParams(0, dp(46), 1f));
        }
        Button[] row2 = {ocr, undo, clear, save, share};
        for (Button b : row2) {
            b.setTextSize(10);
            toolRow2.addView(b, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }

        Button red = makeButton("●");
        Button yellow = makeButton("●");
        Button green = makeButton("●");
        Button blue = makeButton("●");
        Button small = makeButton("S");
        Button medium = makeButton("M");
        Button large = makeButton("L");
        TextView legend = makeInfoText("Warna / Ukuran");
        legend.setTextSize(10);

        Button[] opts = {red, yellow, green, blue, small, medium, large};
        optionRow.addView(legend, new LinearLayout.LayoutParams(0, dp(32), 1.7f));
        for (Button b : opts) optionRow.addView(b, new LinearLayout.LayoutParams(0, dp(32), .55f));

        annotationBar.addView(toolRow1, new LinearLayout.LayoutParams(-1, dp(48)));
        annotationBar.addView(toolRow2, new LinearLayout.LayoutParams(-1, dp(44)));
        annotationBar.addView(optionRow, new LinearLayout.LayoutParams(-1, dp(34)));

        pan.setOnClickListener(v -> setAnnotationMode(AnnotationMode.NONE, "Geser aktif • gunakan 1 jari untuk pan / 2 jari untuk zoom"));
        marker.setOnClickListener(v -> setAnnotationMode(AnnotationMode.MARKER, "Marker aktif • tap titik komponen"));
        arrow.setOnClickListener(v -> setAnnotationMode(AnnotationMode.ARROW, "Panah aktif • tarik dari awal ke akhir"));
        circle.setOnClickListener(v -> setAnnotationMode(AnnotationMode.CIRCLE, "Lingkaran aktif • tarik mengelilingi komponen"));
        text.setOnClickListener(v -> setAnnotationMode(AnnotationMode.TEXT, "Teks aktif • tap lokasi untuk menulis catatan"));
        select.setOnClickListener(v -> setAnnotationMode(AnnotationMode.SELECT, "Pilih aktif • geser tanda untuk memindahkannya"));
        ocr.setOnClickListener(v -> detectOcrOnFrozenImage());
        undo.setOnClickListener(v -> { annotationView.undo(); status.setText("Undo anotasi"); });
        clear.setOnClickListener(v -> { annotationView.clearAll(); status.setText("Semua anotasi dihapus"); });
        save.setOnClickListener(v -> saveAnnotatedFreeze());
        share.setOnClickListener(v -> shareAnnotatedFreeze());

        red.setTextColor(Color.RED); yellow.setTextColor(Color.YELLOW); green.setTextColor(Color.GREEN); blue.setTextColor(Color.CYAN);
        red.setOnClickListener(v -> { annotationView.setColor(Color.RED); status.setText("Warna merah dipilih"); });
        yellow.setOnClickListener(v -> { annotationView.setColor(Color.YELLOW); status.setText("Warna kuning dipilih"); });
        green.setOnClickListener(v -> { annotationView.setColor(Color.GREEN); status.setText("Warna hijau dipilih"); });
        blue.setOnClickListener(v -> { annotationView.setColor(Color.CYAN); status.setText("Warna biru dipilih"); });
        small.setOnClickListener(v -> { annotationView.setSize(3); status.setText("Ukuran kecil"); });
        medium.setOnClickListener(v -> { annotationView.setSize(5); status.setText("Ukuran sedang"); });
        large.setOnClickListener(v -> { annotationView.setSize(8); status.setText("Ukuran besar"); });

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(-1, dp(130), Gravity.BOTTOM);
        barParams.bottomMargin = dp(2);
        annotationView = new AnnotationView(this);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(-1, -1);
        cameraBox.addView(annotationView, cameraBox.indexOfChild(freezeView) + 1, overlayParams);
        annotationView.setMode(annotationMode);
        cameraBox.addView(annotationBar, barParams);
        annotationBar.bringToFront();
    }

    private void detectOcrOnFrozenImage() {
        if (!frozen || frozenBitmap == null || annotationView == null) {
            status.setText("Bekukan gambar dulu");
            return;
        }
        Bitmap cropBitmap = getFrozenZoomedBitmap();
        CropInfo crop = getCurrentCropInfo();
        if (cropBitmap == null || crop == null) {
            status.setText("Area gambar belum siap");
            return;
        }

        status.setText("🔎 Mendeteksi tulisan...");
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        InputImage input = InputImage.fromBitmap(cropBitmap, 0);
        recognizer.process(input)
                .addOnSuccessListener(result -> {
                    java.util.ArrayList<String> found = new java.util.ArrayList<>();
                    int count = 0;
                    for (Text.TextBlock block : result.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            String value = line.getText() == null ? "" : line.getText().trim();
                            if (value.isEmpty() || line.getBoundingBox() == null) continue;
                            android.graphics.Rect r = line.getBoundingBox();
                            float sx = (float) crop.width / Math.max(1, cropBitmap.getWidth());
                            float sy = (float) crop.height / Math.max(1, cropBitmap.getHeight());
                            float x1 = crop.left + r.left * sx;
                            float y1 = crop.top + r.top * sy;
                            float x2 = crop.left + r.right * sx;
                            float y2 = crop.top + r.bottom * sy;
                            annotationView.addOcr(x1, y1, x2, y2, value);
                            found.add(value);
                            count++;
                        }
                    }
                    annotationView.invalidate();
                    if (count == 0) {
                        status.setText("OCR tidak menemukan tulisan");
                        showOcrResultDialog(new java.util.ArrayList<String>());
                    } else {
                        status.setText("OCR menemukan " + count + " tulisan");
                        showOcrResultDialog(found);
                    }
                    recognizer.close();
                })
                .addOnFailureListener(e -> {
                    status.setText("OCR gagal • coba foto lebih terang/dekat");
                    recognizer.close();
                });
    }

    private void showOcrResultDialog(java.util.ArrayList<String> found) {
        if (found.isEmpty()) return;
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < found.size(); i++) {
            message.append(i + 1).append(". ").append(found.get(i));
            if (i < found.size() - 1) message.append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("Hasil Deteksi OCR")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Salin", (d, which) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("OCR PCB", message.toString()));
                        status.setText("Hasil OCR disalin");
                    }
                })
                .show();
    }

    private void setAnnotationMode(AnnotationMode mode, String message) {
        annotationMode = mode;
        if (annotationView != null) annotationView.setMode(mode);
        status.setText(message);
    }

    private void hideAnnotationTools() {
        annotationMode = AnnotationMode.NONE;
        if (annotationBar != null) {
            cameraBox.removeView(annotationBar);
            annotationBar = null;
        }
        if (annotationView != null) {
            cameraBox.removeView(annotationView);
            annotationView = null;
        }
    }

    private void saveAnnotatedFreeze() {
        if (!frozen || frozenBitmap == null || annotationView == null) {
            status.setText("Bekukan gambar dulu");
            return;
        }
        Bitmap result = buildAnnotatedBitmap();
        if (result == null) {
            status.setText("Gagal menyiapkan gambar");
            return;
        }
        if (saveBitmapToGallery(result) != null) status.setText("PCB inspection tersimpan");
    }

    private Bitmap buildAnnotatedBitmap() {
        Bitmap base = getFrozenZoomedBitmap();
        if (base == null) return null;
        Bitmap result = base.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);

        CropInfo crop = getCurrentCropInfo();
        if (crop == null) return result;
        annotationView.drawAnnotationsToCrop(canvas, crop.left, crop.top, crop.width, crop.height,
                result.getWidth(), result.getHeight());
        return result;
    }

    private static class CropInfo {
        int left, top, width, height;
        CropInfo(int l, int t, int w, int h) { left=l; top=t; width=w; height=h; }
    }

    private CropInfo getCurrentCropInfo() {
        if (frozenBitmap == null) return null;
        int w = frozenBitmap.getWidth(), h = frozenBitmap.getHeight();
        float cropScale = 1f / frozenZoom;
        int cw = Math.max(1, Math.min(w, Math.round(w * cropScale)));
        int ch = Math.max(1, Math.min(h, Math.round(h * cropScale)));
        float cx = w / 2f - frozenPanX / Math.max(1f, freezeView == null ? 1f : freezeView.getWidth()) * w;
        float cy = h / 2f - frozenPanY / Math.max(1f, freezeView == null ? 1f : freezeView.getHeight()) * h;
        int left = Math.max(0, Math.min(w - cw, Math.round(cx - cw / 2f)));
        int top = Math.max(0, Math.min(h - ch, Math.round(cy - ch / 2f)));
        return new CropInfo(left, top, cw, ch);
    }

    private void showTextInput(final float x, final float y) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint("Contoh: VCC / jalur putus");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Tambah catatan")
                .setView(input)
                .setPositiveButton("Tambah", (d, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty() && annotationView != null) {
                        annotationView.addTextAtView(x, y, value);
                        status.setText("Catatan ditambahkan");
                    }
                })
                .setNegativeButton("Batal", null).create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            input.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        });
        dialog.show();
    }

    private void shareAnnotatedFreeze() {
        if (!frozen || frozenBitmap == null || annotationView == null) {
            status.setText("Bekukan gambar dulu");
            return;
        }
        Bitmap result = buildAnnotatedBitmap();
        Uri uri = result == null ? null : saveBitmapToGallery(result);
        if (uri == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Bagikan hasil PCB"));
    }

    private class AnnotationView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final java.util.ArrayList<Annotation> items = new java.util.ArrayList<>();
        private AnnotationMode mode = AnnotationMode.NONE;
        private float startX, startY, endX, endY;
        private boolean drawing = false;
        private int currentColor = Color.RED;
        private float strokeDp = 5f;
        private Annotation selected;
        private float lastSelectX, lastSelectY;

        AnnotationView(Context context) {
            super(context);
            setBackground(new ColorDrawable(Color.TRANSPARENT));
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setMode(AnnotationMode m) { mode=m; drawing=false; selected=null; invalidate(); }
        void addOcr(float x1, float y1, float x2, float y2, String text) {
            items.add(Annotation.ocr(x1, y1, x2, y2, text, Color.YELLOW, 3f));
        }
        void setColor(int color) { currentColor=color; invalidate(); }
        void setSize(float size) { strokeDp=size; invalidate(); }

        void undo() { if (!items.isEmpty()) items.remove(items.size()-1); selected=null; invalidate(); }
        void clearAll() { items.clear(); selected=null; invalidate(); }

        private float[] viewToSource(float x, float y) {
            Matrix inv = new Matrix();
            frozenMatrix.invert(inv);
            float[] pts = new float[]{x,y};
            inv.mapPoints(pts);
            return pts;
        }

        private float[] sourceToView(float x, float y) {
            float[] pts = new float[]{x,y};
            frozenMatrix.mapPoints(pts);
            return pts;
        }

        void addTextAtView(float x, float y, String text) {
            float[] p=viewToSource(x,y);
            items.add(Annotation.text(p[0],p[1],text,currentColor,strokeDp));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawAnnotations(canvas);
            if (drawing && mode != AnnotationMode.TEXT && mode != AnnotationMode.MARKER && mode != AnnotationMode.SELECT) {
                float[] a=viewToSource(startX,startY), b=viewToSource(endX,endY);
                drawOne(canvas, Annotation.shape(mode,a[0],a[1],b[0],b[1],currentColor,strokeDp));
            }
        }

        private void drawAnnotations(Canvas canvas) {
            canvas.save();
            canvas.concat(frozenMatrix);
            for (Annotation a:items) drawOneSource(canvas,a);
            canvas.restore();
        }

        private void drawOneSource(Canvas canvas, Annotation a) {
            float scale = Math.max(0.35f, Math.min(4f, frozenMatrix.mapRadius(1f)));
            paint.setColor(a.color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp((int)a.size) / Math.max(0.35f, frozenMatrix.mapRadius(1f)));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setTextSize(dp((int)(18 + a.size)) / Math.max(0.35f, frozenMatrix.mapRadius(1f)));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            if (a.type==AnnotationMode.MARKER) {
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(a.x1,a.y1,dp((int)a.size),paint);
            } else if (a.type==AnnotationMode.ARROW) {
                canvas.drawLine(a.x1,a.y1,a.x2,a.y2,paint);
                double ang=Math.atan2(a.y2-a.y1,a.x2-a.x1); float len=dp(18)/Math.max(0.35f,frozenMatrix.mapRadius(1f));
                canvas.drawLine(a.x2,a.y2,a.x2-len*(float)Math.cos(ang-.45),a.y2-len*(float)Math.sin(ang-.45),paint);
                canvas.drawLine(a.x2,a.y2,a.x2-len*(float)Math.cos(ang+.45),a.y2-len*(float)Math.sin(ang+.45),paint);
            } else if (a.type==AnnotationMode.CIRCLE) {
                canvas.drawOval(new RectF(Math.min(a.x1,a.x2),Math.min(a.y1,a.y2),Math.max(a.x1,a.x2),Math.max(a.y1,a.y2)),paint);
            } else if (a.type==AnnotationMode.TEXT) {
                paint.setStyle(Paint.Style.FILL); paint.setShadowLayer(dp(3),1,1,Color.BLACK); canvas.drawText(a.text,a.x1,a.y1,paint); paint.clearShadowLayer();
            } else if (a.type==AnnotationMode.OCR) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2) / Math.max(0.35f, frozenMatrix.mapRadius(1f)));
                canvas.drawRect(new RectF(a.x1, a.y1, a.x2, a.y2), paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(dp(14) / Math.max(0.35f, frozenMatrix.mapRadius(1f)));
                paint.setShadowLayer(dp(3), 1, 1, Color.BLACK);
                canvas.drawText(a.text, a.x1, Math.max(a.y1 - dp(3) / Math.max(0.35f, frozenMatrix.mapRadius(1f)), a.y1), paint);
                paint.clearShadowLayer();
            }
        }

        private void drawOne(Canvas canvas, Annotation a) { drawOneSource(canvas,a); }

        void drawAnnotationsToCrop(Canvas canvas,int left,int top,int cw,int ch,int outW,int outH) {
            canvas.save();
            float sx=(float)outW/cw, sy=(float)outH/ch;
            canvas.scale(sx,sy); canvas.translate(-left,-top);
            for (Annotation a:items) drawOneSource(canvas,a);
            canvas.restore();
        }

        private Annotation hitTest(float vx,float vy) {
            float[] p=viewToSource(vx,vy);
            float tolerance=dp(24)/Math.max(0.35f,frozenMatrix.mapRadius(1f));
            for (int i=items.size()-1;i>=0;i--) {
                Annotation a=items.get(i);
                float minX=Math.min(a.x1,a.x2)-tolerance, maxX=Math.max(a.x1,a.x2)+tolerance;
                float minY=Math.min(a.y1,a.y2)-tolerance, maxY=Math.max(a.y1,a.y2)+tolerance;
                if (a.type==AnnotationMode.MARKER || a.type==AnnotationMode.TEXT) { minX=a.x1-tolerance;maxX=a.x1+tolerance;minY=a.y1-tolerance;maxY=a.y1+tolerance; }
                if (p[0]>=minX && p[0]<=maxX && p[1]>=minY && p[1]<=maxY) return a;
            }
            return null;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!frozen || mode==AnnotationMode.NONE) return false;
            float x=e.getX(), y=e.getY();
            if (mode==AnnotationMode.SELECT) {
                switch(e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: selected=hitTest(x,y); lastSelectX=x; lastSelectY=y; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (selected!=null) { float[] p1=viewToSource(lastSelectX,lastSelectY), p2=viewToSource(x,y); float dx=p2[0]-p1[0],dy=p2[1]-p1[1]; selected.x1+=dx;selected.y1+=dy;selected.x2+=dx;selected.y2+=dy; lastSelectX=x;lastSelectY=y;invalidate(); } return true;
                    case MotionEvent.ACTION_UP: selected=null; return true;
                }
                return true;
            }
            if (mode==AnnotationMode.TEXT && e.getActionMasked()==MotionEvent.ACTION_UP) { showTextInput(x,y); return true; }
            if (mode==AnnotationMode.MARKER && e.getActionMasked()==MotionEvent.ACTION_UP) { float[] p=viewToSource(x,y); items.add(Annotation.marker(p[0],p[1],currentColor,strokeDp)); invalidate(); return true; }
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: startX=x;startY=y;endX=x;endY=y;drawing=true;return true;
                case MotionEvent.ACTION_MOVE: endX=x;endY=y;invalidate();return true;
                case MotionEvent.ACTION_UP:
                    endX=x;endY=y; if(drawing){float[] a=viewToSource(startX,startY),b=viewToSource(endX,endY);items.add(Annotation.shape(mode,a[0],a[1],b[0],b[1],currentColor,strokeDp));} drawing=false;invalidate();return true;
            }
            return true;
        }
    }

    private static class Annotation {
        AnnotationMode type; float x1,y1,x2,y2; String text; int color=Color.RED; float size=5f;
        static Annotation marker(float x,float y,int c,float s){return shape(AnnotationMode.MARKER,x,y,x,y,c,s);}
        static Annotation text(float x,float y,String t,int c,float s){Annotation a=marker(x,y,c,s);a.type=AnnotationMode.TEXT;a.text=t;return a;}
        static Annotation ocr(float x1,float y1,float x2,float y2,String t,int c,float s){Annotation a=shape(AnnotationMode.OCR,x1,y1,x2,y2,c,s);a.text=t;return a;}
        static Annotation shape(AnnotationMode m,float x1,float y1,float x2,float y2,int c,float s){Annotation a=new Annotation();a.type=m;a.x1=x1;a.y1=y1;a.x2=x2;a.y2=y2;a.color=c;a.size=s;return a;}
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

                        ImageCapture.Builder captureBuilder = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(98);
                Camera2Interop.Extender<ImageCapture> extender = new Camera2Interop.Extender<>(captureBuilder);
                extender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
                extender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
                capture = captureBuilder.build();

                provider.unbindAll();

                camera = provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase,
                        capture
                );

                previewUseCase.setSurfaceProvider(preview.getSurfaceProvider());
                updateZoomText();
                setExposure(0);
                status.setText("Kamera LIVE • tap untuk fokus • Detail ON");

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


    private void setExposure(int value) {
        if (camera == null) return;
        int range = camera.getCameraInfo().getExposureState().getExposureCompensationRange().getUpper();
        int lower = camera.getCameraInfo().getExposureState().getExposureCompensationRange().getLower();
        int clamped = Math.max(lower, Math.min(range, value));
        camera.getCameraControl().setExposureCompensationIndex(clamped);
        exposureBar.setProgress(clamped + 4);
        exposureText.setText(clamped == 0 ? "Exposure 0 • NORMAL" : String.format("Exposure %+d", clamped));
    }

    private void toggleFreeze() {
        if (preview == null) return;

        if (!frozen) {
            Bitmap bitmap = preview.getBitmap();
            if (bitmap == null) {
                status.setText("Frame belum siap");
                return;
            }

            frozenBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            freezeView = new ImageView(this);
            freezeView.setImageBitmap(frozenBitmap);
            freezeView.setScaleType(ImageView.ScaleType.MATRIX);
            freezeView.setBackgroundColor(Color.BLACK);
            freezeView.setOnTouchListener((v, event) -> {
                if (!frozen || annotationMode != AnnotationMode.NONE) return false;
                frozenScaleDetector.onTouchEvent(event);
                if (event.getPointerCount() == 1) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: lastTouchX=event.getX(); lastTouchY=event.getY(); return true;
                        case MotionEvent.ACTION_MOVE: frozenPanX += event.getX()-lastTouchX; frozenPanY += event.getY()-lastTouchY; lastTouchX=event.getX(); lastTouchY=event.getY(); updateFrozenImage(); return true;
                    }
                }
                return true;
            });
            FrameLayout parent = (FrameLayout) preview.getParent();
            parent.addView(freezeView, new FrameLayout.LayoutParams(-1, -1));
            frozen = true;
            frozenZoom = 1f;
            frozenPanX = 0f;
            frozenPanY = 0f;
            zoomBar.setProgress(0);
            updateFrozenImage();
            showAnnotationTools();
            freezeBtn.setText("▶️\nLIVE");
            status.setText("❄️ BEKU • pilih alat untuk menandai PCB");
        } else {
            hideAnnotationTools();
            frozenBitmap = null;
            if (freezeView != null) {
                ViewParent parent = freezeView.getParent();
                if (parent instanceof FrameLayout) {
                    ((FrameLayout) parent).removeView(freezeView);
                }
                freezeView = null;
            }
            frozen = false;
            frozenZoom = 1f;
            frozenPanX = 0f;
            frozenPanY = 0f;
            freezeBtn.setText("❄️\nBeku");
            updateZoomText();
            status.setText("Kamera LIVE");
        }
    }

    private void setFrozenZoomFromProgress(int progress) {
        if (!frozen) return;
        frozenZoom = 1f + (7f * progress / 100f);
        updateFrozenImage();
    }

    private void changeFrozenZoom(float delta) {
        if (!frozen) return;
        frozenZoom = Math.max(1f, Math.min(8f, frozenZoom + delta));
        zoomBar.setProgress((int) (((frozenZoom - 1f) / 7f) * 100f));
        updateFrozenImage();
    }

    private void updateFrozenImage() {
        if (!frozen || freezeView == null || frozenBitmap == null) return;
        int vw = freezeView.getWidth();
        int vh = freezeView.getHeight();
        if (vw <= 0 || vh <= 0) return;

        float base = Math.max((float) vw / frozenBitmap.getWidth(),
                (float) vh / frozenBitmap.getHeight());
        float scale = base * frozenZoom;
        float drawW = frozenBitmap.getWidth() * scale;
        float drawH = frozenBitmap.getHeight() * scale;
        float centerX = (vw - drawW) / 2f + frozenPanX;
        float centerY = (vh - drawH) / 2f + frozenPanY;

        // Limit panning so the image never leaves empty gaps.
        float minX = vw - drawW;
        float minY = vh - drawH;
        float maxX = 0f;
        if (drawW <= vw) centerX = (vw - drawW) / 2f;
        else centerX = Math.max(minX, Math.min(maxX, centerX));
        if (drawH <= vh) centerY = (vh - drawH) / 2f;
        else centerY = Math.max(minY, Math.min(0f, centerY));

        frozenMatrix.reset();
        frozenMatrix.setScale(scale, scale);
        frozenMatrix.postTranslate(centerX, centerY);
        freezeView.setImageMatrix(frozenMatrix);

        zoomText.setText(String.format("Freeze Zoom %.1f×", frozenZoom));
        status.setText(String.format("❄️ BEKU • %.1f× • geser untuk melihat area", frozenZoom));
    }

    private Bitmap getFrozenZoomedBitmap() {
        CropInfo crop = getCurrentCropInfo();
        if (crop == null) return null;
        return Bitmap.createBitmap(frozenBitmap, crop.left, crop.top, crop.width, crop.height);
    }

    private void saveBitmap(Bitmap bitmap) {
        Uri uri = saveBitmapToGallery(bitmap);
        if (uri != null) status.setText("Foto tersimpan • Jejak Teknisi");
    }

    private Uri saveBitmapToGallery(Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            String name = "JejakTeknisi_" + System.currentTimeMillis() + ".jpg";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JejakTeknisi/Microscope");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) { status.setText("Gagal menyimpan foto"); return null; }
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 98, out)) {
                    status.setText("Gagal menyimpan foto"); return null;
                }
            }
            return uri;
        } catch (Exception e) {
            status.setText("Gagal menyimpan foto"); e.printStackTrace(); return null;
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

        status.setText("Fokus...");
        camera.getCameraControl().startFocusAndMetering(action)
                .addListener(() -> status.setText("Fokus siap"),
                        ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (frozen && frozenBitmap != null) {
            Bitmap zoomed = getFrozenZoomedBitmap();
            saveBitmap(zoomed != null ? zoomed : frozenBitmap);
            return;
        }

        if (capture == null) {
            status.setText("Kamera belum siap");
            return;
        }

        photoBtn.setEnabled(false);
        photoBtn.setText("📸\nMEMOTRET...");

        ContentValues values = new ContentValues();
        values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "JejakTeknisi_" + System.currentTimeMillis() + ".jpg"
        );
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/JejakTeknisi/Microscope"
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
                .setMessage("Foto tersimpan di Pictures/JejakTeknisi/Microscope")
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
