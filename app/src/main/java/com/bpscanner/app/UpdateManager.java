package com.bpscanner.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {
    private static final String RELEASE_API = "https://api.github.com/repos/tftpfzh-wq/BPScanner-Android/releases/latest";
    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public UpdateManager(Activity activity) { this.activity = activity; }

    public void check() {
        Toast.makeText(activity, "در حال بررسی نسخه جدید...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "BP-Scanner-Android");
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                int response = c.getResponseCode();
                if (response != 200) {
                    showMessage("بررسی آپدیت انجام نشد. اتصال اینترنت یا انتشار نسخه را بررسی کنید.");
                    return;
                }
                JSONObject release = new JSONObject(new String(readAll(c.getInputStream()), "UTF-8"));
                if (release.optBoolean("draft", true) || release.optBoolean("prerelease", false)) {
                    showMessage("در حال حاضر نسخه عمومی جدیدی منتشر نشده است.");
                    return;
                }
                String tag = release.optString("tag_name", "");
                int latest = versionCodeFromTag(tag);
                int installed = installedVersionCode();
                if (latest < 0) {
                    showMessage("شماره نسخه منتشرشده قابل شناسایی نیست.");
                    return;
                }
                if (latest <= installed) {
                    showMessage("برنامه به‌روز است. نسخه نصب‌شده: " + installed);
                    return;
                }
                JSONArray assets = release.optJSONArray("assets");
                if (assets == null) {
                    showMessage("نسخه جدید پیدا شد، اما فایل نصب هنوز منتشر نشده است.");
                    return;
                }
                String apkUrl = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if ("BPScanner.apk".equals(a.optString("name"))) {
                        apkUrl = a.optString("browser_download_url", null);
                        break;
                    }
                }
                if (apkUrl == null || !apkUrl.startsWith("https://")) {
                    showMessage("نسخه جدید پیدا شد، اما فایل APK در انتشار موجود نیست.");
                    return;
                }
                String notes = release.optString("body", "نسخه جدید آماده است.");
                final String url = apkUrl;
                activity.runOnUiThread(() -> showUpdateDialog(tag, notes, url));
            } catch (Exception e) {
                showMessage("ارتباط با سرور آپدیت برقرار نشد. دوباره تلاش کنید.");
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private void showMessage(String message) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing())
                new AlertDialog.Builder(activity).setTitle("به‌روزرسانی BP Scanner")
                    .setMessage(message).setPositiveButton("باشه", null).show();
        });
    }

    private int installedVersionCode() {
        try {
            PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int)p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) { return 1; }
    }

    private int versionCodeFromTag(String tag) {
        try {
            String[] p = tag.replaceFirst("^[vV]", "").split("\\.");
            if (p.length != 3) return -1;
            int major = Integer.parseInt(p[0]);
            int minor = Integer.parseInt(p[1]);
            int patch = Integer.parseInt(p[2].replaceAll("[^0-9].*$", ""));
            return major * 10000 + minor * 100 + patch;
        } catch (Exception e) { return -1; }
    }

    private void showUpdateDialog(String tag, String notes, String url) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
            .setTitle("نسخه جدید BP Scanner")
            .setMessage("نسخه " + tag + " آماده است.\n\n" + notes + "\n\nبرای دریافت و نصب، ادامه بده.")
            .setNegativeButton("بعداً", (d, w) -> d.dismiss())
            .setPositiveButton("دریافت آپدیت", (d, w) -> download(url))
            .show();
    }

    private void download(String url) {
        executor.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.connect();
                if (c.getResponseCode() != 200) throw new Exception("download failed");
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("cache unavailable");
                File apk = new File(dir, "BPScanner.apk");
                try (InputStream in = new BufferedInputStream(c.getInputStream());
                     FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
                activity.runOnUiThread(() -> install(apk));
            } catch (Exception e) {
                showMessage("دریافت آپدیت ناموفق بود. اینترنت را بررسی و دوباره تلاش کنید.");
            } finally { if (c != null) c.disconnect(); }
        });
    }

    public void resumePendingInstall() {
        try {
            File apk = new File(new File(activity.getCacheDir(), "updates"), "BPScanner.apk");
            if (!activity.getPreferences(Activity.MODE_PRIVATE).getBoolean("pending_update_install", false)) return;
            if (!apk.isFile() || apk.length() == 0) {
                activity.getPreferences(Activity.MODE_PRIVATE).edit().remove("pending_update_install").apply();
                return;
            }
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) return;
            launchInstaller(apk);
        } catch (Exception ignored) { }
    }

    private void install(File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                activity.getPreferences(Activity.MODE_PRIVATE).edit().putBoolean("pending_update_install", true).apply();
                new AlertDialog.Builder(activity)
                    .setTitle("اجازه نصب آپدیت")
                    .setMessage("برای نصب نسخه جدید، اجازه نصب برنامه از این منبع را فعال کنید. پس از فعال‌سازی، به BP Scanner برگردید تا نصب ادامه پیدا کند.")
                    .setNegativeButton("لغو", (d, w) -> activity.getPreferences(Activity.MODE_PRIVATE).edit().remove("pending_update_install").apply())
                    .setPositiveButton("تنظیمات", (d, w) -> activity.startActivity(
                        new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()))))
                    .show();
                return;
            }
            launchInstaller(apk);
        } catch (Exception e) {
            showMessage("بازکردن نصب‌کننده ناموفق بود.");
        }
    }

    private void launchInstaller(File apk) throws Exception {
            activity.getPreferences(Activity.MODE_PRIVATE).edit().remove("pending_update_install").apply();
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
        in.close();
        return out.toByteArray();
    }
}
