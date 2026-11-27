package net.osmand.plus.settings.backend;

import net.osmand.plus.OsmandApplication;

/**
 * Factory for a real OsmandSettings instance usable in JVM unit tests.
 *
 * The OsmandSettings constructors are protected and the preference fields are
 * public final Java fields, so tests cannot subclass, mock or reflectively
 * inject them. This factory lives in the same package as the protected
 * constructor and pairs it with the in-memory TestSettingsAPI, giving tests a
 * fully functional settings object: reads return the registered defaults and
 * set() calls persist for the lifetime of the test.
 */
public class TestOsmandSettings {

	public static OsmandSettings create(OsmandApplication app) {
		return new OsmandSettings(app, new TestSettingsAPI());
	}
}
