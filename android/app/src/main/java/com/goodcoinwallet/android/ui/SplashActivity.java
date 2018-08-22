package com.goodcoinwallet.android.ui;

import android.content.Intent;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;

import com.goodcoinwallet.android.Locker;
import com.goodcoinwallet.android.MainApplication;
import com.goodcoinwallet.android.R;
import com.goodcoinwallet.android.UserAuthenticationLocker;

public class SplashActivity extends AppCompatActivity {

    private static final int HIDE_DELAY_MILLIS = 500;
    private static final int AUTHENTICATION_SCREEN_REQUEST_CODE = 1;

    private Runnable cont;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        setContentView(R.layout.activity_splash);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AUTHENTICATION_SCREEN_REQUEST_CODE) {
            if (resultCode == RESULT_OK)
            {
                cont.run();
            }
            else
            {
                finishAndRemoveTask();
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Locker.UserAuthenticationHandler handler;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            handler = UserAuthenticationLocker.defaultHandler((ret) -> {
                Intent intent = (Intent) ret[0];
                cont = (Runnable) ret[1];
                startActivityForResult(intent, AUTHENTICATION_SCREEN_REQUEST_CODE);
            });
        } else {
            handler = null;
        }

        new Handler().postDelayed(() -> MainApplication.app().login((success) -> {
            if (success) {
                startActivity(new Intent(this, MainActivity.class));
            } else {
                startActivity(new Intent(this, WelcomeActivity.class));
            }
            finish();
        }, handler), HIDE_DELAY_MILLIS);
    }

}
