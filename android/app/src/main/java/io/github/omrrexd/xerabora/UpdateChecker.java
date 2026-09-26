package io.github.omrrexd.xerabora;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks for a newer APK among the releases of the repository the app was
 * built from, and installs it over this one. Tags end in -r<run>, the same
 * number the build used as its versionCode.
 */
final class UpdateChecker {
    private static final String TAG = "xerabora";
    private static final long INTERVAL_MS = 6L * 3600 * 1000;
    private static final String KEY_CHECKED = "update_checked";
    private static final Pattern BUILD = Pattern.compile("-r(\\d+)$");

    private static boolean running;

    private UpdateChecker() {
    }

    private static final class Release {
        String name;
        String apkUrl;
        long build;
    }

    /** At most every six hours, and never on a debug build. */
    static void maybeCheck(Activity activity) {
        if (BuildConfig.DEBUG || running) {
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong(KEY_CHECKED, 0) < INTERVAL_MS) {
            return;
        }
        running = true;
        new Thread(() -> {
            Release r = latest();
            if (r != null) {
                prefs.edit().putLong(KEY_CHECKED, now).apply();
            }
            activity.runOnUiThread(() -> {
                running = false;
                if (r != null && r.build > BuildConfig.VERSION_CODE
                        && !activity.isFinishing() && !activity.isDestroyed()) {
                    offer(activity, r);
                }
            });
        }, "xerabora-update").start();
    }

    private static Release latest() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://api.github.com/repos/" + BuildConfig.UPDATE_REPO
                    + "/releases/latest").openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "xerabora-android/" + BuildConfig.VERSION_NAME);
            if (c.getResponseCode() != 200) {
                return null;
            }
            JSONObject json = new JSONObject(new String(HttpBridge.readAll(c.getInputStream()),
                    StandardCharsets.UTF_8));
            Matcher m = BUILD.matcher(json.optString("tag_name"));
            if (!m.find()) {
                return null;
            }
            Release r = new Release();
            r.build = Long.parseLong(m.group(1));
            r.name = json.optString("name", json.optString("tag_name"));
            JSONArray assets = json.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").endsWith(".apk")) {
                    r.apkUrl = a.optString("browser_download_url");
                    break;
                }
            }
            return r.apkUrl != null ? r : null;
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "update check: " + e);
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static void offer(Activity activity, Release r) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_title)
                .setMessage(activity.getString(R.string.update_text, r.name))
                .setPositiveButton(R.string.update_install, (d, w) -> install(activity, r))
                .setNegativeButton(R.string.later, null)
                .show();
    }

    private static void install(Activity activity, Release r) {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            // Allowed once, in the system settings; the offer comes back on return.
            activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_CHECKED).apply();
            Toast.makeText(activity, R.string.update_allow, Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            return;
        }

        AlertDialog progress = new AlertDialog.Builder(activity)
                .setMessage(R.string.update_downloading)
                .setCancelable(false)
                .show();
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            String error = downloadAndCommit(app, r);
            activity.runOnUiThread(() -> {
                progress.dismiss();
                if (error != null) {
                    Toast.makeText(app, app.getString(R.string.update_failed, error), Toast.LENGTH_LONG).show();
                }
            });
        }, "xerabora-download").start();
    }

    /** Streams the APK into an install session; null when committed. */
    private static String downloadAndCommit(Context app, Release r) {
        PackageInstaller installer = app.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(app.getPackageName());
        int id = -1;
        HttpURLConnection c = null;

        try {
            id = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(id)) {
                c = (HttpURLConnection) new URL(r.apkUrl).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "xerabora-android/" + BuildConfig.VERSION_NAME);
                if (c.getResponseCode() != 200) {
                    installer.abandonSession(id);
                    return "HTTP " + c.getResponseCode();
                }
                long length = c.getContentLengthLong();
                try (InputStream in = c.getInputStream();
                     OutputStream out = session.openWrite("base.apk", 0, length > 0 ? length : -1)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    session.fsync(out);
                }
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent done = PendingIntent.getBroadcast(app, id,
                        new Intent(app, InstallReceiver.class), flags);
                session.commit(done.getIntentSender());
            }
            return null;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "update install: " + e);
            if (id != -1) {
                try {
                    installer.abandonSession(id);
                } catch (RuntimeException ignored) {
                    // already gone
                }
            }
            return e.getMessage() != null ? e.getMessage() : e.toString();
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
