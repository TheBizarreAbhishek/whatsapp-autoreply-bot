package zo.ro.whatsappreplybot.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;

import zo.ro.whatsappreplybot.R;
import zo.ro.whatsappreplybot.helpers.NotificationHelper;
import zo.ro.whatsappreplybot.others.InputFilterMinMax;
import zo.ro.whatsappreplybot.services.MyNotificationListenerService;

public class BotSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bot_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

//        ------------------------------------------------------------------------------------------

        if (NotificationHelper.isNotificationServicePermissionGranted(this)){

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean isBotEnabled = sharedPreferences.getBoolean("is_bot_enabled", true);

            if (isBotEnabled) {
                if (!NotificationHelper.isNotificationListenerServiceRunning(this)) {
                    startService(new Intent(this, MyNotificationListenerService.class));
                }
            } else {
                if (NotificationHelper.isNotificationListenerServiceRunning(this)) {
                    stopService(new Intent(this, MyNotificationListenerService.class));
                }
            }
        } else {
            startActivity(new Intent(this, MainActivity.class));
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1000);
        }

//        ------------------------------------------------------------------------------------------

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.bot_settings);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            EditTextPreference editTextPreference = findPreference("max_reply");
            if (editTextPreference != null) {
                editTextPreference.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setFilters(new InputFilter[]{new InputFilterMinMax(1, 999999999)});
                });
            }

            // Add validation for custom delay
            EditTextPreference customDelayPreference = findPreference("custom_delay_seconds");
            if (customDelayPreference != null) {
                customDelayPreference.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    editText.setFilters(new InputFilter[]{new InputFilterMinMax(0, 300)}); // 0 to 300 seconds (5 minutes max)
                });
            }
        }
    }
}