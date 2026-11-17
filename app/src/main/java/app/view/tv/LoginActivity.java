package app.view.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

public class LoginActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private EditText server;
    private EditText user;
    private EditText pass;
    private TextView error;
    private TextView loginBtn;
    private boolean busy;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        server = findViewById(R.id.server);
        user = findViewById(R.id.user);
        pass = findViewById(R.id.pass);
        error = findViewById(R.id.error);
        loginBtn = findViewById(R.id.loginBtn);
        Ui.gradientText(findViewById(R.id.brand));

        server.setText(Prefs.lastServer(this));
        user.setText(Prefs.lastUser(this));

        loginBtn.setOnClickListener(v -> login());
        pass.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_NULL) {
                hideKeyboard(v);
                loginBtn.requestFocus();
                return true;
            }
            return false;
        });

        if (server.getText().length() > 0 && user.getText().length() > 0) pass.requestFocus();
        else server.requestFocus();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void showError(String msg) {
        error.setText(msg);
        error.setVisibility(View.VISIBLE);
    }

    private void login() {
        if (busy) return;
        final String s = Xtream.normalizeServer(server.getText().toString());
        final String u = user.getText().toString().trim();
        final String p = pass.getText().toString().trim();
        if (s.isEmpty() || u.isEmpty() || p.isEmpty()) {
            showError("Bitte Server-URL, Benutzername und Passwort ausfüllen.");
            return;
        }
        hideKeyboard(loginBtn);
        busy = true;
        error.setVisibility(View.GONE);
        loginBtn.setText("Verbinde …");
        server.setText(s);
        new Thread(() -> {
            final Xtream.LoginResult r = Xtream.login(s, u, p);
            ui.post(() -> {
                if (destroyed) return;
                busy = false;
                loginBtn.setText("Anmelden");
                if (!r.ok) {
                    showError(r.error);
                    return;
                }
                Xtream.clearCache(LoginActivity.this);
                Prefs.saveLogin(LoginActivity.this, s, u, p, r.expiry);
                Intent i = new Intent(LoginActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
        }, "view-login").start();
    }
}
