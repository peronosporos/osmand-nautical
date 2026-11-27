package net.osmand.plus.settings.backend;

import net.osmand.plus.api.SettingsAPI;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory SettingsAPI for JVM unit tests.
 *
 * Preferences on OsmandSettings are public final Java fields, so they cannot
 * be intercepted by mockk: reading one on a mocked OsmandSettings returns the
 * raw (null) field, and every { settings.SOME_PREFERENCE } records no call.
 * Tests that need working preferences build a real OsmandSettings over this
 * implementation instead, which stores every value in a plain HashMap for the
 * lifetime of the test.
 */
public class TestSettingsAPI implements SettingsAPI {

	private final Map<String, Map<String, Object>> stores = new HashMap<>();

	private Map<String, Object> store(Object pref) {
		return stores.computeIfAbsent(String.valueOf(pref), name -> new HashMap<>());
	}

	@Override
	public Object getPreferenceObject(String key) {
		// The returned object is only an opaque token handed back to the
		// get/edit calls, so the preference name itself serves as one.
		return key;
	}

	@Override
	public SettingsEditor edit(Object pref) {
		Map<String, Object> store = store(pref);
		return new SettingsEditor() {
			@Override
			public SettingsEditor putString(String key, String value) {
				store.put(key, value);
				return this;
			}

			@Override
			public SettingsEditor putBoolean(String key, boolean value) {
				store.put(key, value);
				return this;
			}

			@Override
			public SettingsEditor putFloat(String key, float value) {
				store.put(key, value);
				return this;
			}

			@Override
			public SettingsEditor putInt(String key, int value) {
				store.put(key, value);
				return this;
			}

			@Override
			public SettingsEditor putLong(String key, long value) {
				store.put(key, value);
				return this;
			}

			@Override
			public SettingsEditor remove(String key) {
				store.remove(key);
				return this;
			}

			@Override
			public SettingsEditor clear() {
				store.clear();
				return this;
			}

			@Override
			public boolean commit() {
				return true;
			}
		};
	}

	@Override
	public String getString(Object pref, String key, String defValue) {
		Object value = store(pref).get(key);
		return value instanceof String ? (String) value : defValue;
	}

	@Override
	public float getFloat(Object pref, String key, float defValue) {
		Object value = store(pref).get(key);
		return value instanceof Float ? (Float) value : defValue;
	}

	@Override
	public boolean getBoolean(Object pref, String key, boolean defValue) {
		Object value = store(pref).get(key);
		return value instanceof Boolean ? (Boolean) value : defValue;
	}

	@Override
	public int getInt(Object pref, String key, int defValue) {
		Object value = store(pref).get(key);
		return value instanceof Integer ? (Integer) value : defValue;
	}

	@Override
	public long getLong(Object pref, String key, long defValue) {
		Object value = store(pref).get(key);
		return value instanceof Long ? (Long) value : defValue;
	}

	@Override
	public boolean contains(Object pref, String key) {
		return store(pref).containsKey(key);
	}
}
