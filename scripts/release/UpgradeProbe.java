package org.nexlibre.releaseprobe;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Same-certificate, disposable-emulator probe. Never shipped inside the app. */
public final class UpgradeProbe extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    private void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (text.contentEquals(node.getText() == null ? "" : node.getText()) ||
            text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = find(node.getChild(i), text);
            if (match != null) return match;
        }
        return null;
    }
    private AccessibilityNodeInfo waitForText(String text) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 20000;
        do {
            AccessibilityNodeInfo found = find(getUiAutomation().getRootInActiveWindow(), text);
            if (found != null) return found;
            Thread.sleep(100);
        } while (android.os.SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Visible UI text missing: " + text);
    }
    private void click(AccessibilityNodeInfo node) {
        while (node != null && !node.isClickable()) node = node.getParent();
        require(node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "UI click failed");
    }
    private String string(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
        require(id != 0, "Missing string: " + name);
        return context.getString(id);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            SharedPreferences prefs = context.getSharedPreferences("bitchord_settings", 0);
            SharedPreferences updates = context.getSharedPreferences("space_updates", 0);
            File playlists = new File(context.getFilesDir(), "star-device-playlists.json");
            String playlist = "[{\"id\":\"upgrade-preserved\",\"name\":\"Upgrade preserved playlist\",\"songs\":[{\"id\":\"upgrade-track\",\"title\":\"Upgrade preserved track\",\"artist\":\"Upgrade test\",\"uri\":\"file:///storage/emulated/0/Music/upgrade-test.wav\"}]}]";
            if ("seed".equals(arguments.getString("phase"))) {
                require(info.getLongVersionCode() == 9 && "1.4.1".equals(info.versionName), "Seed must run on exact 1.4.1");
                require(prefs.edit().putString("theme_mode", "DARK").putBoolean("liquid_glass", true)
                    .putBoolean("google_artwork_colors", true).putBoolean("adaptive_glass", true)
                    .putInt("google_accent", 0xff197643).putString("upgrade_probe_marker", "persisted-on-1.4.1").commit(), "Preference commit failed");
                require(updates.edit().putInt("seen_version", 9).commit(), "News seed failed");
                Files.write(playlists.toPath(), playlist.getBytes(StandardCharsets.UTF_8));
                result.putString("stream", "UPGRADE_SEED_OK version=1.4.1 code=9\n");
            } else {
                require(info.getLongVersionCode() == 10 && "1.5.0".equals(info.versionName), "Wrong installed release");
                require("persisted-on-1.4.1".equals(prefs.getString("upgrade_probe_marker", "")), "Upgrade erased preferences");
                require("DARK".equals(prefs.getString("theme_mode", "")) && prefs.getBoolean("liquid_glass", false), "Theme lost");
                require(prefs.getBoolean("google_artwork_colors", false) && prefs.getBoolean("adaptive_glass", false), "Visual preferences lost");
                require(prefs.getInt("google_accent", 0) == 0xff197643, "Accent lost");
                require(read(playlists).equals(playlist), "Playlist changed during update");
                require(updates.getInt("seen_version", 0) == 9, "News was consumed before UI launch");
                Activity activity = startActivitySync(new Intent().setClassName(context.getPackageName(), "com.music.bitchord.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                waitForIdleSync();
                String title = String.format(string(context, "space_whats_new"), "1.5.0");
                waitForText(title);
                click(waitForText(string(context, "back")));
                waitForText(string(context, "library_nav_playlists"));
                require(updates.getInt("seen_version", 0) == 10, "News acknowledgement not persisted");
                click(waitForText(string(context, "library_nav_playlists")));
                click(waitForText("Upgrade preserved playlist"));
                waitForText("Upgrade preserved track");
                click(waitForText("Upgrade preserved track"));
                Thread.sleep(3000);
                require(((android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).isMusicActive(), "Upgraded release did not play local playlist audio");
                runOnMainSync(() -> activity.finish());
                waitForIdleSync();
                Activity second = startActivitySync(new Intent().setClassName(context.getPackageName(), "com.music.bitchord.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                waitForText(string(context, "library_nav_playlists"));
                require(find(getUiAutomation().getRootInActiveWindow(), title) == null, "News repeated after acknowledgement");
                runOnMainSync(() -> second.finish());
                result.putString("stream", "UPGRADE_VERIFY_OK version=1.5.0 code=10 preferences=preserved playlist=visible playback=active news=once\n");
            }
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "UPGRADE_FAILED " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
