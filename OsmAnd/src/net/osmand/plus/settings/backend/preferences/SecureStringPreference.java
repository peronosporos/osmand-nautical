package net.osmand.plus.settings.backend.preferences;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import net.osmand.plus.settings.backend.OsmandSettings;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class SecureStringPreference extends StringPreference {

    private SharedPreferences encryptedPrefs;

    public SecureStringPreference(@NonNull OsmandSettings settings, @NonNull String id, String defaultValue) {
        super(settings, id, defaultValue);
        initEncryptedPrefs();
    }

    private void initEncryptedPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(getContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    getContext(),
                    "net.osmand.secure_settings",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getValue(@NonNull Object prefs, String defaultValue) {
        if (encryptedPrefs != null && encryptedPrefs.contains(getId())) {
            return encryptedPrefs.getString(getId(), defaultValue);
        }
        // Fallback to legacy if present, or return default
        String legacyValue = super.getValue(prefs, defaultValue);
        if (legacyValue != null && !legacyValue.equals(defaultValue)) {
            // Migrate to encrypted
            set(legacyValue);
            // Clear legacy
            getSettingsAPI().edit(prefs).remove(getId()).commit();
        }
        return legacyValue;
    }

    @Override
    protected boolean setValue(Object prefs, String val) {
        if (encryptedPrefs != null) {
            return encryptedPrefs.edit().putString(getId(), val).commit();
        }
        return super.setValue(prefs, val);
    }
}
