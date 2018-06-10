package de.oromit.flagcarrier;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class WriteTagActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_write_tag);
        setTitle(R.string.write_tag_title);

        Button writeButton = findViewById(R.id.writeTagButton);
        writeButton.setOnClickListener(this::onDoWriteTag);
    }

    public void onDoWriteTag(View v) {
        Toast.makeText(this, "Woah", Toast.LENGTH_SHORT).show();
    }
}
