package com.mikeyphw.hooklab.termux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mikeyphw.hooklab.R;

/** Native share/selection receiver, deliberately outside all LSPosed host hooks. */
public final class TermuxBridgeActivity extends AppCompatActivity {
    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String PREFS = "termux_bridge";
    private static final String PREF_WORKDIR = "workdir";
    private static final String ACTION = "com.termux.RUN_COMMAND";
    private static final int REQUEST_COMMAND = 902;
    private static final int THEME_AMOLED = 100;

    private TextInputEditText commands;
    private TextInputEditText workingDirectory;
    private TextView status;
    private SharedPreferences preferences;

    @Override public void onCreate(Bundle savedInstanceState) {
        SharedPreferences appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        int night = appearance.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (night == THEME_AMOLED) {
            setTheme(R.style.Theme_HookLab_Amoled);
            night = AppCompatDelegate.MODE_NIGHT_YES;
        }
        getDelegate().setLocalNightMode(night);
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.termux_bridge_title);
        toolbar.setNavigationIcon(R.drawable.ic_nav_back);
        toolbar.setNavigationContentDescription(R.string.ui_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        ViewCompat.requestApplyInsets(root);

        label(content, R.string.termux_bridge_intro);
        commands = editField(content, R.string.termux_bridge_commands, true);
        commands.setMinLines(7);
        commands.setGravity(Gravity.TOP | Gravity.START);
        commands.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commands.setSaveEnabled(false);
        button(content, R.string.termux_bridge_paste, v -> pasteClipboard());
        workingDirectory = editField(content, R.string.termux_bridge_workdir, false);
        workingDirectory.setSingleLine(true);
        workingDirectory.setText(preferences.getString(PREF_WORKDIR, TermuxCommandPolicy.HOME));
        button(content, R.string.termux_bridge_save_workdir, v -> saveWorkingDirectory());
        label(content, R.string.termux_bridge_setup);
        status = label(content, R.string.termux_bridge_idle);
        button(content, R.string.termux_bridge_run, v -> confirmExecution());

        if (savedInstanceState != null) {
            commands.setText(savedInstanceState.getString("command", ""));
            workingDirectory.setText(savedInstanceState.getString("workingDirectory",
                    TermuxCommandPolicy.HOME));
        } else {
            receiveSharedText(getIntent());
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receiveSharedText(intent);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("command", value(commands));
        state.putString("workingDirectory", value(workingDirectory));
        super.onSaveInstanceState(state);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(LinearLayout parent, int id) {
        TextView text = new TextView(this);
        text.setText(id);
        text.setTextSize(15);
        text.setPadding(0, dp(10), 0, dp(10));
        parent.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    private TextInputEditText editField(LinearLayout parent, int hint, boolean multiline) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(getString(hint));
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        edit.setSingleLine(!multiline);
        layout.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        parent.addView(layout, lp);
        return edit;
    }

    private void button(LinearLayout parent, int id, View.OnClickListener callback) {
        MaterialButton button = new MaterialButton(this);
        button.setText(id);
        button.setOnClickListener(callback);
        parent.addView(button, new ViewGroup.LayoutParams(-1, -2));
    }

    private static String value(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private void receiveSharedText(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        CharSequence shared = null;
        try {
            if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(intent.getType()))
                shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            else if (Intent.ACTION_PROCESS_TEXT.equals(action))
                shared = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        } catch (RuntimeException error) {
            status.setText(R.string.termux_bridge_bad_share);
            return;
        }
        if (shared == null) return;
        try {
            commands.setText(TermuxCommandPolicy.checkedCommand(shared.toString()));
            status.setText(R.string.termux_bridge_ready);
        } catch (IllegalArgumentException error) {
            commands.setText("");
            status.setText(error.getMessage());
        }
    }

    private void pasteClipboard() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData data;
        try {
            data = manager == null ? null : manager.getPrimaryClip();
        } catch (SecurityException error) {
            status.setText(R.string.termux_bridge_no_clipboard);
            return;
        }
        if (data == null || data.getItemCount() == 0) {
            status.setText(R.string.termux_bridge_no_clipboard);
            return;
        }
        CharSequence clip = data.getItemAt(0).coerceToText(this);
        try {
            commands.setText(TermuxCommandPolicy.checkedCommand(
                    clip == null ? null : clip.toString()));
            status.setText(R.string.termux_bridge_ready);
        } catch (IllegalArgumentException error) {
            status.setText(error.getMessage());
        }
    }

    private boolean saveWorkingDirectory() {
        try {
            String path = TermuxCommandPolicy.checkedWorkingDirectory(value(workingDirectory));
            preferences.edit().putString(PREF_WORKDIR, path).apply();
            workingDirectory.setText(path);
            status.setText(R.string.termux_bridge_workdir_saved);
            return true;
        } catch (IllegalArgumentException error) {
            status.setText(error.getMessage());
            return false;
        }
    }

    private void confirmExecution() {
        final String source;
        final String workdir;
        try {
            source = TermuxCommandPolicy.checkedCommand(value(commands));
            workdir = TermuxCommandPolicy.checkedWorkingDirectory(value(workingDirectory));
        } catch (IllegalArgumentException error) {
            status.setText(error.getMessage());
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.termux_bridge_confirm_title)
                .setMessage(R.string.termux_bridge_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.termux_bridge_execute, (dialog, which) -> dispatch(source, workdir))
                .show();
    }

    private void dispatch(String command, String workdir) {
        try {
            if (getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0) == null) {
                status.setText(R.string.termux_bridge_missing_termux);
                return;
            }
        } catch (PackageManager.NameNotFoundException error) {
            status.setText(R.string.termux_bridge_missing_termux);
            return;
        }
        if (checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            status.setText(R.string.termux_bridge_permission_needed);
            requestPermissions(new String[]{PERMISSION}, REQUEST_COMMAND);
            return; // No automatic execution if permission is subsequently granted.
        }
        Intent request = new Intent(ACTION);
        request.setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService");
        request.putExtra("com.termux.RUN_COMMAND_PATH", TermuxCommandPolicy.ZSH);
        request.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-l", "-i", "-c", command});
        request.putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir);
        request.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        request.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            ComponentName destination = startService(request);
            if (destination == null) status.setText(R.string.termux_bridge_start_failed);
            else {
                preferences.edit().putString(PREF_WORKDIR, workdir).apply();
                status.setText(R.string.termux_bridge_dispatched);
            }
        } catch (SecurityException error) {
            status.setText(R.string.termux_bridge_permission_needed);
        } catch (RuntimeException error) {
            status.setText(R.string.termux_bridge_start_failed);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_COMMAND) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            status.setText(R.string.termux_bridge_granted_retry);
        else status.setText(R.string.termux_bridge_permission_needed);
    }
}
