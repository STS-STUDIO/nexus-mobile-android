package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NexusUpdateManager {
    private static final String TAG = "NexusUpdateManager";
    private static final String UPDATE_URL_PRIMARY = "https://stsstudio.org/nexus/download/latest.json";
    private static final String UPDATE_URL_FALLBACK = "https://www.stsstudio.org/nexus/download/latest.json";
    private static final String DEFAULT_APK_URL = "https://stsstudio.org/nexus/download/Nexus-Android.apk";

    public static class UpdateInfo {
        public String version = "";
        public int versionCode = 0;
        public String downloadUrl = DEFAULT_APK_URL;
        public String notes = "";
        public boolean isUpdateAvailable = false;
    }

    /**
     * Check for updates.
     * @param activity current activity
     * @param userInitiated if true, shows a toast if no update is found or on network error
     */
    public static void checkForUpdate(Activity activity, boolean userInitiated) {
        if (activity == null || activity.isFinishing()) return;

        new Thread(() -> {
            try {
                UpdateInfo info = fetchUpdateInfo();
                if (activity.isFinishing()) return;

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    if (info != null && info.isUpdateAvailable) {
                        showUpdateDialog(activity, info);
                    } else if (userInitiated) {
                        String current = BuildConfig.VERSION_NAME;
                        Toast.makeText(activity, "Nexus Launcher is up to date (v" + current + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Update check failed", e);
                if (userInitiated && !activity.isFinishing()) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Unable to check for updates: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            }
        }).start();
    }

    private static UpdateInfo fetchUpdateInfo() {
        String jsonStr = downloadString(UPDATE_URL_PRIMARY);
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            jsonStr = downloadString(UPDATE_URL_FALLBACK);
        }
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;

        try {
            JSONObject root = new JSONObject(jsonStr);
            UpdateInfo info = new UpdateInfo();

            if (root.has("android")) {
                JSONObject androidObj = root.getJSONObject("android");
                info.version = androidObj.optString("version", root.optString("version", ""));
                info.versionCode = androidObj.optInt("versionCode", 0);
                info.downloadUrl = androidObj.optString("url", DEFAULT_APK_URL);
                info.notes = androidObj.optString("notes", root.optString("notes", ""));
            } else {
                info.version = root.optString("version", "");
                info.versionCode = 0;
                info.downloadUrl = DEFAULT_APK_URL;
                info.notes = root.optString("notes", "");
            }

            int currentCode = BuildConfig.VERSION_CODE;
            if (info.versionCode > 0) {
                info.isUpdateAvailable = (info.versionCode > currentCode);
            } else if (!info.version.isEmpty()) {
                info.isUpdateAvailable = isVersionGreater(info.version, BuildConfig.VERSION_NAME);
            }

            return info;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse update manifest", e);
            return null;
        }
    }

    private static boolean isVersionGreater(String remote, String local) {
        if (remote == null || remote.isEmpty()) return false;
        if (local == null || local.isEmpty()) return true;

        try {
            String r = remote.replaceAll("[^0-9.]", "");
            String l = local.replaceAll("[^0-9.]", "");
            String[] rParts = r.split("\\.");
            String[] lParts = l.split("\\.");
            int maxLen = Math.max(rParts.length, lParts.length);

            for (int i = 0; i < maxLen; i++) {
                int rVal = i < rParts.length && !rParts[i].isEmpty() ? Integer.parseInt(rParts[i]) : 0;
                int lVal = i < lParts.length && !lParts[i].isEmpty() ? Integer.parseInt(lParts[i]) : 0;
                if (rVal > lVal) return true;
                if (rVal < lVal) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String downloadString(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr + "?t=" + System.currentTimeMillis());
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "NexusLauncher-Android/" + BuildConfig.VERSION_NAME);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed reading " + urlStr + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static void showUpdateDialog(Activity activity, UpdateInfo info) {
        String title = "Update Available: Nexus v" + info.version;
        String message = "A new version of Nexus Launcher is available.\n\n"
                + (info.notes != null && !info.notes.isEmpty() ? info.notes + "\n\n" : "")
                + "Would you like to download and install this update now?";

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Update Now", (dialog, which) -> downloadAndInstallApk(activity, info))
                .setNegativeButton("Later", null)
                .setCancelable(true)
                .show();
    }

    private static void downloadAndInstallApk(Activity activity, UpdateInfo info) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Downloading Nexus Update");
        progressDialog.setMessage("Please wait while the update is downloading...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            File targetApk = new File(activity.getExternalCacheDir() != null ? activity.getExternalCacheDir() : activity.getCacheDir(), "Nexus-Update.apk");
            HttpURLConnection connection = null;
            InputStream input = null;
            OutputStream output = null;

            try {
                URL url = new URL(info.downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.connect();

                int fileLength = connection.getContentLength();
                input = new BufferedInputStream(connection.getInputStream());
                output = new FileOutputStream(targetApk);

                byte[] data = new byte[8192];
                long total = 0;
                int count;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        activity.runOnUiThread(() -> progressDialog.setProgress(progress));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                activity.runOnUiThread(() -> {
                    try {
                        if (progressDialog.isShowing()) progressDialog.dismiss();
                    } catch (Exception ignored) {}

                    installApk(activity, targetApk);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error downloading APK", e);
                activity.runOnUiThread(() -> {
                    try {
                        if (progressDialog.isShowing()) progressDialog.dismiss();
                    } catch (Exception ignored) {}
                    Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    public static void installApk(Activity activity, File apkFile) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(activity, "Update package not found", Toast.LENGTH_SHORT).show();
                return;
            }

            // Android 8.0+ Unknown sources check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    new AlertDialog.Builder(activity)
                            .setTitle("Install Permission Required")
                            .setMessage("Android requires permission to install updates from within Nexus Launcher.\n\nPlease enable 'Allow from this source' on the next screen, then tap Install.")
                            .setPositiveButton("Open Settings", (d, w) -> {
                                try {
                                    Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                                    settingsIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                                    activity.startActivity(settingsIntent);
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed opening settings", e);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
            }

            Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".nexusupdateprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Failed to launch installer", e);
            Toast.makeText(activity, "Install error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
