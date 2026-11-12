package com.javachat.mobile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.javachat.mobile.R;
import com.javachat.mobile.auth.AuthenticationManager;
import com.javachat.mobile.auth.LoginActivity;
import com.javachat.mobile.network.ConnectionManager;
import com.javachat.mobile.network.MessageListener;
import com.javachat.mobile.protocol.Message;

/**
 * Host activity for the chat experience. Contains a DrawerLayout:
 * - Main content: {@link GlobalChatFragment}
 * - End drawer: online users list (populated via the fragment)
 *
 * Note: ActionBarDrawerToggle is intentionally NOT used — it is designed for
 * start-gravity drawers only and throws on syncState() when no start drawer
 * exists. The end drawer (online users) is opened via the toolbar menu instead.
 */
public class ChatActivity extends AppCompatActivity implements MessageListener {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);

        ConnectionManager.getInstance().setListener(this);
        ConnectionManager.getInstance().setNotificationContext(this);

        if (savedInstanceState == null) {
            // commitNow() runs the transaction synchronously so onViewCreated fires before we
            // return — this guarantees onlineUsersAdapter is initialised before addSelf() below.
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new GlobalChatFragment())
                    .commitNow();
            GlobalChatFragment f = getGlobalChatFragment();
            if (f != null) {
                f.addSelf(AuthenticationManager.getInstance().getUsername());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, getString(R.string.online_users_title))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            // Open the end drawer to show online users
            int gravity = android.view.Gravity.END;
            if (drawerLayout.isDrawerOpen(gravity)) {
                drawerLayout.closeDrawer(gravity);
            } else {
                drawerLayout.openDrawer(gravity);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // BUG 10: only disconnect when actually finishing, not on screen rotation.
        // disconnect() must be off the main thread — same ANR risk as LoginActivity.
        if (isFinishing()) {
            AuthenticationManager.getInstance().logout();
            new Thread(ConnectionManager.getInstance()::disconnect).start();
        }
    }

    // -------------------------------------------------------------------------
    // MessageListener — delegate to the fragment
    // -------------------------------------------------------------------------

    @Override
    public void onMessageReceived(Message message) {
        GlobalChatFragment fragment = getGlobalChatFragment();
        if (fragment != null) {
            fragment.handleMessage(message);
        }
    }

    @Override
    public void onConnectionLost(String reason) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.disconnected_title))
                .setMessage(getString(R.string.disconnected_msg))
                .setPositiveButton(R.string.btn_reconnect, (d, w) -> returnToLogin())
                .setNegativeButton(R.string.btn_exit, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void returnToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private GlobalChatFragment getGlobalChatFragment() {
        return (GlobalChatFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragmentContainer);
    }
}
