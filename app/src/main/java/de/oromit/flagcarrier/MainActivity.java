package de.oromit.flagcarrier;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button clearButton = findViewById(R.id.clearButton);
        clearButton.setOnClickListener(this::onClear);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int adb = Settings.Secure.getInt(this.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
        if(adb == 0)
            return super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.settings_item:
                showSettings();
                return true;
            case R.id.write_tag_item:
                showWriteTag();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void showWriteTag() {
        Intent intent = new Intent(this, WriteTagActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void onClear(View v) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(R.string.clear_confirmation);
        b.setTitle(R.string.clear_conf_title);
        b.setPositiveButton(android.R.string.yes, this::onDoClear);
        b.setNegativeButton(android.R.string.no, this::onDoClear);
        b.show();
    }

    private void onDoClear(DialogInterface dialog, int i) {
        if(i != DialogInterface.BUTTON_POSITIVE)
            return;

        //TODO: actually send Clear-request
    }
}
