package com.bpscanner.app;

import android.app.Activity;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final int RED = 0xFFFF173D;
    private static final int BG = 0xFF07070A;
    private static final int PANEL = 0xFF151116;
    private Camera camera;
    private SurfaceView preview;
    private SurfaceHolder holder;
    private TextView bpmText, statusText, scanTitle, pressureValue, timerText;
    private Button scanButton;
    private boolean scanning = false;
    private boolean activityResumed = false;
    private double previous = 0;
    private long lastPeak = 0;
    private int frames = 0;
    private long scanStartedAt = 0;
    private int signalSamples = 0;
    private double signalSum = 0, signalSumSq = 0, signalMin = 255, signalMax = 0;
    private int latestBpm = 0;
    private final android.os.Handler scanHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF050507);
        getWindow().setNavigationBarColor(0xFF050507);
        buildUi();
        new android.os.Handler(getMainLooper()).postDelayed(() -> new UpdateManager(this).check(), 1800);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 7);
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(10), dp(16), dp(18));
        scroll.addView(root);

        LinearLayout header = horizontal();
        TextView logo = text("♥", 36, RED, true);
        header.addView(logo, lp(48, 58));
        LinearLayout brand = vertical();
        TextView title = text("BP SCANNER", 25, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        TextView subtitle = text("سلامتی شما، اولویت ماست", 13, 0xFFFFB8C3, false);
        subtitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        brand.addView(title, lp(-1, 34));
        brand.addView(subtitle, lp(-1, 24));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(58), 1));
        TextView settings = text("⚙", 29, Color.WHITE, false);
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings, lp(48, 58));
        root.addView(header, lp(-1, 64));

        LinearLayout metrics = horizontal();
        metrics.setPadding(dp(8), dp(12), dp(8), dp(12));
        metrics.setBackground(shape(0xFF120D12, RED, 22));
        LinearLayout heart = vertical();
        heart.addView(text("♥  ضربان قلب", 16, 0xFFFF9EAE, true), lp(-1, 28));
        bpmText = text("-- BPM", 34, RED, true);
        heart.addView(bpmText, lp(-1, 52));
        heart.addView(text("تخمین آزمایشی با دوربین", 11, 0xFFBDB3B8, false), lp(-1, 24));
        metrics.addView(heart, new LinearLayout.LayoutParams(0, dp(112), 1));
        View divider = new View(this);
        divider.setBackgroundColor(0xFF8F142D);
        metrics.addView(divider, lp(1, 82));
        LinearLayout pressure = vertical();
        pressure.addView(text("♥  فشار خون", 16, 0xFFFF9EAE, true), lp(-1, 28));
        pressureValue = text("قابل اندازه‌گیری نیست", 17, 0xFFEDE8EA, true);
        pressure.addView(pressureValue, lp(-1, 52));
        pressure.addView(text("نیازمند اعتبارسنجی پزشکی", 11, 0xFFFFB8C3, false), lp(-1, 24));
        metrics.addView(pressure, new LinearLayout.LayoutParams(0, dp(112), 1));
        root.addView(metrics, lp(-1, 138));

        TextView scanHeading = text("آزمایش ثبت سیگنال مچ", 20, Color.WHITE, true);
        scanHeading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sh = lp(-1, 42);
        sh.topMargin = dp(12);
        root.addView(scanHeading, sh);

        FrameLayout cameraFrame = new FrameLayout(this);
        cameraFrame.setBackground(shape(0xFF100B10, RED, 120));
        cameraFrame.setPadding(dp(8), dp(8), dp(8), dp(8));
        preview = new SurfaceView(this);
        holder = preview.getHolder();
        holder.addCallback(this);
        cameraFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams cf = lp(238, 238);
        cf.gravity = Gravity.CENTER;
        cf.topMargin = dp(5);
        cf.bottomMargin = dp(8);
        root.addView(cameraFrame, cf);

        scanTitle = text("آماده اسکن", 20, 0xFFFF667D, true);
        root.addView(scanTitle, lp(-1, 36));
        timerText = text("زمان باقی‌مانده: ۴۵ ثانیه", 22, RED, true);
        root.addView(timerText, lp(-1, 38));
        statusText = text("انگشت خود را آرام روی دوربین و فلش قرار دهید.", 13, 0xFFE5D9DE, false);
        root.addView(statusText, lp(-1, 34));

        scanButton = new Button(this);
        scanButton.setText("شروع اسکن");
        scanButton.setTextColor(Color.WHITE);
        scanButton.setTextSize(16);
        scanButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        scanButton.setBackground(shape(RED, 0xFFFF647A, 18));
        scanButton.setOnClickListener(v -> toggleScan());
        LinearLayout.LayoutParams sb = lp(-1, 54);
        sb.topMargin = dp(8);
        root.addView(scanButton, sb);

        LinearLayout tiles = horizontal();
        tiles.setGravity(Gravity.CENTER);
        tiles.setPadding(0, dp(14), 0, dp(4));
        tiles.addView(tile("▥", "سوابق", "مشاهده تاریخچه", () -> showHistory()), new LinearLayout.LayoutParams(0, dp(108), 1));
        View gap1 = new View(this); tiles.addView(gap1, lp(8, 1));
        tiles.addView(tile("◉", "وضعیت", "دوربین و اتصال", () -> Toast.makeText(this, "وضعیت دوربین در بخش اسکن نمایش داده می‌شود.", Toast.LENGTH_SHORT).show()), new LinearLayout.LayoutParams(0, dp(108), 1));
        View gap2 = new View(this); tiles.addView(gap2, lp(8, 1));
        tiles.addView(tile("↻", "به‌روزرسانی", "بررسی نسخه جدید", () -> new UpdateManager(this).check()), new LinearLayout.LayoutParams(0, dp(108), 1));
        root.addView(tiles, lp(-1, 126));

        TextView disclaimer = text("این نسخه فقط سیگنال نوری مچ و ضربان را برای آزمایش ثبت می‌کند. فشار سیستولیک و دیاستولیک با دوربین هنوز قابل اندازه‌گیری معتبر نیست؛ برای تصمیم درمانی از فشارسنج تأییدشده استفاده کنید.", 11, 0xFFFFB7C1, false);
        disclaimer.setPadding(dp(8), dp(10), dp(8), dp(10));
        disclaimer.setBackground(shape(0xFF170D12, 0xFF6C172B, 14));
        LinearLayout.LayoutParams note = lp(-1, -2);
        note.topMargin = dp(10);
        root.addView(disclaimer, note);

        LinearLayout nav = horizontal();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(8), dp(4), dp(4));
        nav.setBackground(shape(0xFF120D12, RED, 24));
        nav.addView(navItem("⌂", "خانه", () -> scroll.smoothScrollTo(0, 0)), new LinearLayout.LayoutParams(0, dp(62), 1));
        nav.addView(navItem("♡", "اسکن", () -> toggleScan()), new LinearLayout.LayoutParams(0, dp(62), 1));
        nav.addView(navItem("▥", "تاریخچه", () -> showHistory()), new LinearLayout.LayoutParams(0, dp(62), 1));
        nav.addView(navItem("⚙", "تنظیمات", () -> showSettings()), new LinearLayout.LayoutParams(0, dp(62), 1));
        LinearLayout.LayoutParams np = lp(-1, 78);
        np.topMargin = dp(12);
        root.addView(nav, np);
        setContentView(scroll);
    }

    private View tile(String icon, String title, String detail, Runnable action) {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(3), dp(5), dp(3), dp(5));
        box.setBackground(shape(PANEL, 0xFF8D1A32, 16));
        box.addView(text(icon, 25, RED, true), lp(-1, 34));
        box.addView(text(title, 13, Color.WHITE, true), lp(-1, 24));
        box.addView(text(detail, 9, 0xFFFFA9B6, false), lp(-1, 22));
        box.setOnClickListener(v -> action.run());
        return box;
    }

    private View navItem(String icon, String title, Runnable action) {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        box.addView(text(icon, 25, RED, true), lp(-1, 32));
        box.addView(text(title, 12, Color.WHITE, true), lp(-1, 22));
        box.setOnClickListener(v -> action.run());
        return box;
    }

    private void showHistory() {
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        String date = p.getString("test_date", null);
        String details = date == null ? "هنوز اسکن آزمایشی ثبت نشده است." :
            "آخرین اسکن: " + date + "\\nمدت: " + p.getLong("test_duration", 0) + " ثانیه" +
            "\\nنمونه‌های ثبت‌شده: " + p.getInt("test_samples", 0) +
            "\\nضربان تخمینی: " + p.getInt("test_bpm", 0) + " BPM" +
            "\\nکیفیت سیگنال: " + p.getString("test_quality", "نامشخص") +
            "\\nفشار درمانگاه: " + p.getString("clinic_bp", "ثبت نشده");
        new android.app.AlertDialog.Builder(this).setTitle("گزارش آزمایش")
            .setMessage(details)
            .setNeutralButton("ثبت فشار درمانگاه", (d,w) -> showClinicEntry())
            .setPositiveButton("باشه", null).show();
    }

    private void showClinicEntry() {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), dp(4));
        EditText sys = new EditText(this); sys.setSingleLine(true); sys.setInputType(2); sys.setHint("سیستولیک (عدد بالا)، مثلاً 120");
        EditText dia = new EditText(this); dia.setSingleLine(true); dia.setInputType(2); dia.setHint("دیاستولیک (عدد پایین)، مثلاً 80");
        form.addView(sys, lp(-1, 54)); form.addView(dia, lp(-1, 54));
        new android.app.AlertDialog.Builder(this).setTitle("ثبت نتیجه فشارسنج درمانگاه")
            .setMessage("اعداد را دقیقاً از روی اندازه‌گیری پرستار وارد کن. این اعداد مرجع مقایسه‌اند و خروجی گوشی نیستند.")
            .setView(form).setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره", (d,w) -> {
                try {
                    int s = Integer.parseInt(sys.getText().toString().trim());
                    int diaValue = Integer.parseInt(dia.getText().toString().trim());
                    if (s < 60 || s > 260 || diaValue < 30 || diaValue > 160 || s <= diaValue) throw new Exception();
                    getPreferences(MODE_PRIVATE).edit().putString("clinic_bp", s + "/" + diaValue + " mmHg").apply();
                    Toast.makeText(this, "نتیجه درمانگاه ذخیره شد.", Toast.LENGTH_LONG).show();
                } catch (Exception e) { Toast.makeText(this, "اعداد واردشده معتبر نیستند.", Toast.LENGTH_LONG).show(); }
            }).show();
    }

    private void showSettings() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("تنظیمات BP Scanner")
            .setItems(new String[]{"بررسی به‌روزرسانی", "درباره برنامه", "راهنمای اسکن"}, (d, which) -> {
                if (which == 0) new UpdateManager(this).check();
                else if (which == 1) new android.app.AlertDialog.Builder(this).setMessage("BP Scanner نسخه آزمایشی پایش ضربان قلب است؛ ابزار اندازه‌گیری فشار خون نیست.").setPositiveButton("باشه", null).show();
                else new android.app.AlertDialog.Builder(this).setMessage("انگشت را بدون فشار زیاد روی لنز دوربین و فلش پشت گوشی قرار دهید و تا پایان اسکن ثابت نگه دارید.").setPositiveButton("باشه", null).show();
            }).show();
    }

    private void toggleScan() {
        if (scanning) { finishScan(); return; }
        if (camera == null) {
            statusText.setText("دوربین آماده نیست؛ مجوز دوربین و دسترسی دستگاه را بررسی کنید.");
            return;
        }
        scanning = true;
        previous = 0; lastPeak = 0; frames = 0; latestBpm = 0;
        signalSamples = 0; signalSum = 0; signalSumSq = 0; signalMin = 255; signalMax = 0;
        scanStartedAt = System.currentTimeMillis();
        bpmText.setText("-- BPM");
        scanButton.setText("پایان اسکن");
        scanTitle.setText("در حال ثبت سیگنال مچ");
        statusText.setText("۴۵ ثانیه ثابت نگه دار؛ نور فلش روشن باشد.");
        timerText.setText("زمان باقی‌مانده: ۴۵ ثانیه");
        updateCountdown();
        try {
            Camera.Parameters p = camera.getParameters();
            if (p.getSupportedFlashModes() != null) {
                p.setFlashMode(scanning && p.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_TORCH)
                    ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(p);
            }
        } catch (Exception ignored) { }
    }

    private void updateCountdown() {
        if (!scanning) return;
        long elapsed = System.currentTimeMillis() - scanStartedAt;
        long remaining = Math.max(0, (45000 - elapsed + 999) / 1000);
        timerText.setText("زمان باقی‌مانده: " + remaining + " ثانیه");
        if (remaining <= 0) {
            finishScan();
        } else {
            scanHandler.postDelayed(this::updateCountdown, 250);
        }
    }

    private void finishScan() {
        if (!scanning) return;
        scanning = false;
        scanHandler.removeCallbacksAndMessages(null);
        if (timerText != null) timerText.setText("زمان اسکن: پایان‌یافته");
        try {
            Camera.Parameters p = camera == null ? null : camera.getParameters();
            if (p != null && p.getSupportedFlashModes() != null && p.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_OFF)) {
                p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF); camera.setParameters(p);
            }
        } catch (Exception ignored) { }
        long seconds = Math.max(1, (System.currentTimeMillis() - scanStartedAt) / 1000);
        double mean = signalSamples == 0 ? 0 : signalSum / signalSamples;
        double variance = signalSamples == 0 ? 0 : Math.max(0, signalSumSq / signalSamples - mean * mean);
        double sd = Math.sqrt(variance);
        String quality = signalSamples < 250 ? "داده ناکافی" : (signalMax - signalMin < 0.5 ? "سیگنال ضعیف/بدون تغییر" : "ثبت شد؛ نیازمند بررسی تخصصی");
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        getPreferences(MODE_PRIVATE).edit().putString("test_date", stamp).putLong("test_duration", seconds)
            .putInt("test_samples", signalSamples).putInt("test_bpm", latestBpm)
            .putString("test_quality", quality).putFloat("test_mean", (float)mean)
            .putFloat("test_sd", (float)sd).putFloat("test_range", (float)(signalMax - signalMin)).apply();
        scanButton.setText("شروع اسکن ۴۵ ثانیه‌ای");
        scanTitle.setText("ثبت آزمایش تمام شد");
        statusText.setText("سیگنال ذخیره شد؛ فشار خون از این داده محاسبه نشده است.");
        new android.app.AlertDialog.Builder(this).setTitle("پایان اسکن آزمایشی")
            .setMessage("مدت: " + seconds + " ثانیه\\nنمونه‌ها: " + signalSamples + "\\nضربان تخمینی: " + (latestBpm > 0 ? latestBpm + " BPM" : "نامشخص") +
                "\\nکیفیت: " + quality + "\\n\\nسیستولیک/دیاستولیک توسط گوشی اندازه‌گیری نشده است. در درمانگاه، فشارسنج دستی را معیار قرار بده.")
            .setNeutralButton("ثبت عدد درمانگاه", (d,w) -> showClinicEntry())
            .setPositiveButton("باشه", null).show();
    }

    @Override public void onPreviewFrame(byte[] data, Camera source) {
        if (!scanning || data == null) return;
        Camera.Size s = source.getParameters().getPreviewSize();
        int width = s.width, height = s.height;
        int x0 = width / 4, x1 = width * 3 / 4, y0 = height / 4, y1 = height * 3 / 4;
        long sum = 0; int count = 0;
        for (int y = y0; y < y1; y += 3) {
            int row = y * width;
            for (int x = x0; x < x1; x += 3) {
                int index = row + x;
                if (index < data.length) { sum += (data[index] & 255); count++; }
            }
        }
        double value = sum / (double)Math.max(1, count);
        signalSamples++; signalSum += value; signalSumSq += value * value;
        signalMin = Math.min(signalMin, value); signalMax = Math.max(signalMax, value);
        long now = System.currentTimeMillis();
        frames++;
        if (frames > 10 && previous > value) {
            if (lastPeak == 0) lastPeak = now;
            else {
                long interval = now - lastPeak;
                lastPeak = now;
                int bpm = (int)(60000 / interval);
                if (interval >= 350 && interval <= 2000 && bpm >= 40 && bpm <= 200) {
                    runOnUiThread(() -> {
                        latestBpm = bpm;
                        bpmText.setText(bpm + " BPM");
                        statusText.setText("سیگنال در حال ثبت است؛ فشار خون محاسبه نمی‌شود.");
                    });
                }
            }
        }
        previous = value;
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        if (activityResumed && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
    }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int he) {
        if (activityResumed && camera == null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
    }
    @Override public void surfaceDestroyed(SurfaceHolder h) { releaseCamera(); }

    private void startCamera() {
        if (!activityResumed || camera != null || holder == null || !holder.getSurface().isValid()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            camera = Camera.open();
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
            if (statusText != null) statusText.setText("دوربین آماده است؛ برای شروع اسکن دکمه را بزنید.");
        } catch (Exception e) {
            if (statusText != null) statusText.setText("راه‌اندازی دوربین ناموفق بود. برنامه را دوباره باز کنید.");
            releaseCamera();
        }
    }

    private void releaseCamera() {
        scanning = false;
        if (scanButton != null) scanButton.setText("شروع اسکن");
        if (timerText != null) timerText.setText("زمان باقی‌مانده: ۴۵ ثانیه");
        if (camera != null) {
            Camera old = camera;
            camera = null;
            try { old.setPreviewCallback(null); old.stopPreview(); } catch (Exception ignored) { }
            try { old.release(); } catch (Exception ignored) { }
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 7 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            if (activityResumed && holder != null && holder.getSurface().isValid()) startCamera();
        } else if (statusText != null) statusText.setText("برای اسکن، مجوز دوربین لازم است.");
    }

    @Override protected void onPause() { activityResumed = false; releaseCamera(); super.onPause(); }
    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        new UpdateManager(this).resumePendingInstall();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && holder != null && holder.getSurface().isValid()) startCamera();
    }
}
