package de.oromit.flagcarrier;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.util.Base64;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setHasOptionsMenu(false);

            setPreferencesFromResource(R.xml.preferences, rootKey);

            findPreference("gen_skpk").setOnPreferenceClickListener(preference -> {
                Context ctx = getContext();
                if (ctx == null)
                    return false;

                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                builder.setMessage("Overwrite existing keypair?");
                builder.setPositiveButton("Yes", (d, p) -> {
                    genNewKeypair();
                    d.dismiss();
                });
                builder.setNegativeButton("No", (d, p) -> d.dismiss());

                AlertDialog dialog = builder.create();
                dialog.show();

                return true;
            });
        }

        private void genNewKeypair() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            SharedPreferences.Editor edit = prefs.edit();

            CryptoManager.KeyPair keyPair = CryptoManager.genKeyPair();

            edit.putString("priv_key", Base64.encodeToString(keyPair.PrivateKey, Base64.NO_WRAP));
            edit.putString("pub_key", Base64.encodeToString(keyPair.PublicKey, Base64.NO_WRAP));

            edit.apply();

            if (getActivity() != null)
                getActivity().onBackPressed();
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            view.setBackgroundColor(Color.WHITE);
        }
    }
}
