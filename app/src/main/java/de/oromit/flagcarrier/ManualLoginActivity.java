package de.oromit.flagcarrier;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.hbb20.CountryCodePicker;

import java.util.HashMap;

public class ManualLoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_write_tag);
        setTitle(R.string.manual_login_title);

        Button submitButton = findViewById(R.id.writeTagButton);
        submitButton.setText(R.string.manual_login_submit);
        submitButton.setOnClickListener(v->onLogin());

        View label = findViewById(R.id.extraDataLabel);
        label.setVisibility(View.INVISIBLE);

        View box = findViewById(R.id.extraDataText);
        box.setVisibility(View.INVISIBLE);
    }

    private String getFieldText(int id) {
        EditText e = findViewById(id);
        return e.getText().toString();
    }

    private String getCountryCode() {
        CountryCodePicker ccp = findViewById(R.id.countryCodePicker);
        return ccp.getSelectedCountryNameCode();
    }

    private void onLogin() {
        HashMap<String, String> dataMap = new HashMap<>();

        String dsplname = getFieldText(R.id.displayNameText).trim();

        if(dsplname.isEmpty()) {
            Toast.makeText(this, "A display name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        dataMap.put("display_name", dsplname);
        dataMap.put("country_code", getCountryCode());

        String tmp = getFieldText(R.id.speedrunNameText).trim();
        if(!tmp.isEmpty())
            dataMap.put("speedruncom_name", tmp);

        tmp = getFieldText(R.id.twitchNameText).trim();
        if(!tmp.isEmpty())
            dataMap.put("twitch_name", tmp);

        tmp = getFieldText(R.id.twitterHandleText).trim();
        if(!tmp.isEmpty())
            dataMap.put("twitter_handle", tmp);

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setAction("de.oromit.flagcarrier.ManualLoginActivity.Login");
        intent.putExtra("MANUAL_TAG_LOGIN_DATA", dataMap);
        startActivity(intent);
        finish();
    }
}
