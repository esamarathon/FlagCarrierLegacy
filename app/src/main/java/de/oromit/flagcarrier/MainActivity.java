package de.oromit.flagcarrier;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity implements Callback {
    private HttpManager httpManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        httpManager = new HttpManager(this, this);

        Button clearButton = findViewById(R.id.clearButton);
        clearButton.setOnClickListener(v->onClear());

        Button manualButton = findViewById(R.id.manualButton);
        manualButton.setOnClickListener(v->onManualLogin());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String pk = prefs.getString("pub_key", null);
        if (pk != null && !pk.isEmpty())
            manualButton.setVisibility(View.INVISIBLE);
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

    private void onClear() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(R.string.clear_confirmation);
        b.setTitle(R.string.clear_conf_title);
        b.setPositiveButton(android.R.string.yes, (d,i)->onDoClear(i));
        b.setNegativeButton(android.R.string.no, (d,i)->onDoClear(i));
        b.show();
    }

    private void onManualLogin() {
        Intent intent = new Intent(this, ManualLoginActivity.class);
        startActivity(intent);
    }

    private void onDoClear(int i) {
        if(i != DialogInterface.BUTTON_POSITIVE)
            return;

        try {
            httpManager.doRequest("clear", null);
        } catch(HttpManager.HttpManagerException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        } catch(HttpManager.MissingSettingException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onFailure(Call call, IOException e) {
        runOnUiThread(()->
                Toast.makeText(this, "Request failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    @Override
    public void onResponse(Call call, Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null)
            return;

        final String res;
        int code = response.code();

        if (code != 200) {
            res = "Error " + code + ": " + body.string().trim();
        } else {
            res = body.string().trim();
        }

        runOnUiThread(() -> Toast.makeText(MainActivity.this, res, Toast.LENGTH_LONG).show());
    }
}
