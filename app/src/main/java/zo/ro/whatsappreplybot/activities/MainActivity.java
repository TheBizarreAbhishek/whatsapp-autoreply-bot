package zo.ro.whatsappreplybot.activities;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

import zo.ro.whatsappreplybot.R;
import zo.ro.whatsappreplybot.databinding.ActivityMainBinding;
import zo.ro.whatsappreplybot.helpers.NotificationHelper;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private boolean isSettingsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        setSupportActionBar(binding.toolbar);

        if (NotificationHelper.isNotificationServicePermissionGranted(this)) {
            setSettingsButton();
        }

        binding.permissionAndSettingsBtn.setOnClickListener(v -> {
            if (isSettingsButton) {
                startActivity(new Intent(this, BotSettingsActivity.class));
            } else {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });
    }

    //--------------------------------------------------------------------------------------------------
    @Override
    protected void onResume() {
        super.onResume();

        if (NotificationHelper.isNotificationServicePermissionGranted(this)) {
            setSettingsButton();
        }
    }

//    ----------------------------------------------------------------------------------------------

    private void setSettingsButton() {
        isSettingsButton = true;
        binding.permissionAndSettingsBtn.setText(R.string.bot_settings);
        binding.permissionAndSettingsBtn.setIconResource(R.drawable.settings_24);
        binding.shortInfoTV.setText(getString(R.string.manage_bot_settings));
    }
}