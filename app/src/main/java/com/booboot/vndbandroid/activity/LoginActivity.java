package com.booboot.vndbandroid.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.booboot.vndbandroid.R;
import com.booboot.vndbandroid.api.Cache;
import com.booboot.vndbandroid.api.bean.Links;
import com.booboot.vndbandroid.util.Callback;
import com.booboot.vndbandroid.util.SettingsManager;
import com.booboot.vndbandroid.util.Utils;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener {
    public static LoginActivity instance;
    public static boolean autologin = true;
    private Button loginButton;
    private EditText loginUsername;
    private EditText loginPassword;
    private ProgressBar progressBar;
    private TextView signupTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(SettingsManager.getNoActionBarTheme(this));
        setContentView(R.layout.login);

        instance = this;

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(MainActivity.getThemeColor(this, R.attr.colorPrimaryDark));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setBackgroundColor(getResources().getColor(android.R.color.transparent, getTheme()));
        } else {
            window.getDecorView().setBackgroundColor(getResources().getColor(android.R.color.transparent));
        }
        window.getDecorView().setVisibility(View.GONE);

        signupTextView = (TextView) findViewById(R.id.signupTextView);
        initSignup();
        loginButton = (Button) findViewById(R.id.loginButton);
        loginButton.setOnClickListener(this);
        loginUsername = (EditText) findViewById(R.id.loginUsername);
        loginPassword = (EditText) findViewById(R.id.loginPassword);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        String savedUsername = SettingsManager.getUsername(this);
        String savedPassword = SettingsManager.getPassword(this);
        if (autologin && savedUsername != null && savedPassword != null) {
            /* Filling the inputs with saved values (for appearance's sake) */
            loginUsername.setText(savedUsername);
            loginPassword.setText(savedPassword);
            disableAll();

            new Thread() {
                public void run() {
                    login();
                }
            }.start();
        } else {
            enableAll();
        }

        autologin = false;
    }

    private void initSignup() {
        SpannableString ss = new SpannableString("Don't have a VNDB account yet? Sign up here.");
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View textView) {
                Utils.openInBrowser(LoginActivity.this, Links.VNDB_REGISTER);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        };
        ss.setSpan(clickableSpan, ss.toString().indexOf("Sign up here"), ss.toString().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        signupTextView.setText(ss);
        signupTextView.setMovementMethod(LinkMovementMethod.getInstance());
        signupTextView.setHighlightColor(Color.TRANSPARENT);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.loginButton) {
            SettingsManager.setUsername(this, loginUsername.getText().toString());
            SettingsManager.setPassword(this, loginPassword.getText().toString());
            disableAll();
            login();
        }
    }

    private void login() {
        Cache.loadFromCache(this);

        if (Cache.loadedFromCache) {
            VNTypeFragment.refreshOnInit = true;
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
        } else {
            Cache.loadData(LoginActivity.this, new Callback() {
                @Override
                protected void config() {
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                }
            }, getCallback());
        }
    }

    public void enableAll() {
        progressBar.setVisibility(View.INVISIBLE);
        loginUsername.setEnabled(true);
        loginPassword.setEnabled(true);
        loginButton.setText(R.string.sign_in);
        loginButton.setEnabled(true);
    }

    public void disableAll() {
        /* Disabling the inputs */
        loginUsername.setEnabled(false);
        loginPassword.setEnabled(false);
        loginButton.setText(R.string.signing_in);
        loginButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void config() {
                if (Cache.pipeliningError) return;
                Cache.pipeliningError = true;
                Callback.showToast(LoginActivity.this, message);
                enableAll();
                if (countDownLatch != null) countDownLatch.countDown();
            }
        };
    }
}
