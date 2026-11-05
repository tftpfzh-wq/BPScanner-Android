package com.bpscanner.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
        executor.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "BP-Scanner-Android");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() != 200) return;
                byte[] bytes = readAll(c.getInputStream());
                JSONObject release = new JSONObject(new String(bytes, "UTF-8"));
                if (release.optBoolean("draft", true) || release.optBoolean("prerelease", false)) return;
                String tag = release.optString("tag_name", "");
                int latest = versionCodeFromTag(tag);
                if (latest <= installedVersionCode()) return;
                JSONArray assets = release.optJSONArray("assets");
                if (assets == null) return;
                String apkUrl = null;
                for (int i=0; i<assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if ("BPScanner.apk".equals(a.optString("name"))) {
                        apkUrl = a.optString("browser_download_url", null);
                        break;
                    }
                }
                if (apkUrl == null || !apkUrl.startsWith("https://")) return;
                String notes = release.optString("body", "نسخه جدید آماده است.");
                final String url = apkUrl;
                activity.runOnUiThread(() -> showUpdateDialog(tag, notes, url));
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
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
            int major=Integer.parseInt(p[0]), minor=Integer.parseInt(p[1]), patch=Integer.parseInt(p[2].replaceAll("[^0-9].*$",""));
            return major*10000 + minor*100 + patch;
        } catch (Exception e) { return -1; }
    }

    private void showUpdateDialog(String tag, String notes, String url) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
            .setTitle("نسخه جدید BP Scanner")
            .setMessage("نسخه " + tag + " آماده است.\n\n" + notes + "\n\nبرای دریافت و نصب، ادامه بده.")
            .setNegativeButton("بعداً", (d,w)->d.dismiss())
            .setPositiveButton("دریافت آپدیت", (d,w)->download(url))
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
                if (c.getResponseCode()!=200) throw new Exception("download failed");
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("cache unavailable");
                File apk = new File(dir, "BPScanner.apk");
                try (InputStream in = new BufferedInputStream(c.getInputStream());
                     FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n=in.read(buffer))>0) out.write(buffer,0,n);
                }
                activity.runOnUiThread(() -> install(apk));
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity, "دریافت آپدیت ناموفق بود.", Toast.LENGTH_LONG).show());
            } finally { if(c!=null)c.disconnect(); }
        });
    }

    private void install(File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                    .setTitle("اجازه نصب آپدیت")
                    .setMessage("برای نصب نسخه جدید، اجازه نصب برنامه از این منبع را فعال کن.")
                    .setNegativeButton("لغو", null)
                    .setPositiveButton("تنظیمات", (d,w)->activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:"+activity.getPackageName()))))
                    .show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName()+".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "بازکردن نصب‌کننده ناموفق بود.", Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b=new byte[4096]; int n;
        while((n=in.read(b))!=-1) out.write(b,0,n);
        in.close(); return out.toByteArray();
    }
}