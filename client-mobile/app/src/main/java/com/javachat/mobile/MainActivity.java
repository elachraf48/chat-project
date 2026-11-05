package com.javachat.mobile;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.javachat.mobile.auth.LoginActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Redirect immediately to LoginActivity
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
