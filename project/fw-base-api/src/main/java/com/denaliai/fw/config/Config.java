package com.denaliai.fw.config;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class Config {
	public static final String FW_PREFIX;

	private static final AtomicReference<Map<String, Setting>> m_settings = new AtomicReference<>(new HashMap<>());
	private static final GetValueAsInt m_intGetter = new GetValueAsInt();
	private static final GetValueAsLong m_longGetter = new GetValueAsLong();
	private static final GetValueAsBool m_boolGetter = new GetValueAsBool();
	private static final GetValueAsString m_stringGetter = new GetValueAsString();
	private static final boolean PRINT_DEBUG_MESSAGES;
	private static final boolean ADD_PROPS_FILES_TO_SYS_PROPS;
	private static IConfigLogger LOG = new ConsoleLogger();

	static {
		String fwPrefix = System.getenv().get("FW_PREFIX");
		if (fwPrefix == null) {
			fwPrefix = System.getProperty("FW_PREFIX", "com.denaliai.fw.");
		}
		FW_PREFIX = fwPrefix;

		final String printDebugMsgs = FW_PREFIX + "configDebugLog";
		PRINT_DEBUG_MESSAGES = "true".equals(System.getenv().get(printDebugMsgs)) || "true".equals(System.getProperty(printDebugMsgs));

		final String addPropsFilesToSysProps = FW_PREFIX + "config.addPropsFilesToSysProps";
		ADD_PROPS_FILES_TO_SYS_PROPS = "true".equals(System.getenv().get(addPropsFilesToSysProps)) || "true".equals(System.getProperty(addPropsFilesToSysProps));

		try {
			addConfigDataset("environment", System.getenv().entrySet());
			addConfigDataset("system-properties", System.getProperties().entrySet());

			// Load the default properties file name
			loadConfigFile("app.properties", false);

			// Load user-defined properties files
			final String propFiles = getString(FW_PREFIX + "config.propertiesFiles", null);
			if (propFiles != null) {
				String[] filenames = propFiles.split(",");
				for(String filename : filenames) {
					loadConfigFile(filename, true);
				}
			}
		} catch(Throwable t) {
			LOG.fatal("Unexpected exception while loading bootstrap configuration, terminating application");
			t.printStackTrace();
			System.exit(1);
		}
	}

	private Config() {
	}

	private static void loadConfigFile(String filename, boolean logIfNotFound) {
		final String fn = filename.trim();
		final File f = new File(fn);
		try {
			if (f.exists()) {
				final Properties p = new Properties();
				final FileReader in = new FileReader(f);
				try {
					p.load(in);
				} finally {
					in.close();
				}
				addConfigDataset(filename, p.entrySet(), ADD_PROPS_FILES_TO_SYS_PROPS);
			} else {
				InputStream in = Config.class.getClassLoader().getResourceAsStream(fn);
				if (in != null) {
					final Properties p = new Properties();
					try {
						p.load(in);
					} finally {
						in.close();
					}
					addConfigDataset(filename, p.entrySet(), ADD_PROPS_FILES_TO_SYS_PROPS);
				} else {
					if (logIfNotFound) {
						LOG.warn("Could not find properties file '%1$s', continuing without it", f.getAbsolutePath());
					}
					return;
				}
			}
		} catch(Throwable t) {
			LOG.warn("Exception reading properties file '%1$s', continuing without it", f.getAbsolutePath());
			t.printStackTrace();
		}
	}

	public static void setConfigLogger(IConfigLogger logger) {
		LOG = logger;
	}

	// static void setOperationInitialized() {
	//     m_operationInitialized = true;
	// }

	/**
	 *
	 * @param sourceName - used for logging to identify where these values
	 *                     are coming from.
	 * @param data - must be a set of Map.Entry instances, like what is returned
	 *               from HashMap.entrySet() or Properties.entrySet()
	 */
	@SuppressWarnings("rawtypes")
	public static void addConfigDataset(String sourceName, Set data) {
		addConfigDataset(sourceName, data, false);
	}

	@SuppressWarnings("rawtypes")
	public static void addConfigDataset(String sourceName, Set data, boolean addToSystemProperties) {
		synchronized(Config.class) {
			final Map<String, Setting> newSettings = new HashMap<>(m_settings.get());
			for(Object o : data) {
				final Map.Entry entry = (Map.Entry)o;
				final String entryKey = entry.getKey().toString();
				final String entryValue = entry.getValue().toString();
				if (addToSystemProperties && System.getProperty(entryKey) == null) {
					System.setProperty(entryKey, entryValue);
				}
				final Setting s = new Setting(entryKey, entryValue, sourceName);
				final Setting existing = newSettings.put(s.name, s);
				if (existing != null) {
					if (PRINT_DEBUG_MESSAGES) {
						LOG.info("Overriding '%1$s' from %2$s with a value from %3$s", s.name, existing.source, s.source);
					}
				}
			}
			m_settings.set(newSettings);
		}
	}

	public static void overrideValue(String sourceName, String key, String value) {
		synchronized(Config.class) {
			final Map<String, Setting> newSettings = new HashMap<>(m_settings.get());
			final Setting s = new Setting(key, value, sourceName);
			final Setting existing = newSettings.put(key, s);
			if (existing != null) {
				if (PRINT_DEBUG_MESSAGES) {
					LOG.info("Overriding '%1$s' from %2$s with a value from %3$s", s.name, existing.source, s.source);
				}
			}
			m_settings.set(newSettings);
		}
	}

	public static void addDefaultValue(String sourceName, String key, String value) {
		synchronized(Config.class) {
			if (m_settings.get().containsKey(key)) {
				// Already set value, do not add the default
				return;
			}
			overrideValue(sourceName, key, value);
		}
	}

	static Setting createSetting(String sourceName, String key, String value) {
		return new Setting(key, value, sourceName);
	}

	public static Set<String> keys() {
		return m_settings.get().keySet();
	}

	public static Integer getFWInt(final String key, final int min, final int max, final Integer defaultValue, final boolean suppressValueLogging) {
		return getInt(Config.FW_PREFIX + key, min, max, defaultValue, suppressValueLogging);
	}
	public static Integer getFWInt(final String key, final int min, final int max, final Integer defaultValue) {
		return getInt(Config.FW_PREFIX + key, min, max, defaultValue, false);
	}
	public static Integer getFWInt(final String key, final Integer defaultValue) {
		return getInt(Config.FW_PREFIX + key, Integer.MIN_VALUE, Integer.MAX_VALUE, defaultValue, false);
	}
	public static Integer getInt(final String key, final int min, final int max, final Integer defaultValue) {
		return getInt(key, min, max, defaultValue, false);
	}
	public static Integer getInt(final String key, final Integer defaultValue) {
		return getInt(key, Integer.MIN_VALUE, Integer.MAX_VALUE, defaultValue, false);
	}
	public static Integer getInt(final String key, final int min, final int max, final Integer defaultValue, final boolean suppressValueLogging) {
		final Integer value = (Integer)getValue(key, defaultValue, suppressValueLogging, m_intGetter);
		if (value != null) {
			if (value < min) {
				if (suppressValueLogging) {
					LOG.warn("Resolved int value for key '%1$s' is below the minimum -- using default value instead", key);
				} else {
					LOG.warn("Resolved int value for key '%1$s' %2$s is below the minimum of %3$s -- using default value of %4$s instead", key, value, min, defaultValue);
				}
				return defaultValue;
			}
			if (value > max) {
				if (suppressValueLogging) {
					LOG.warn("Resolved int value for key '%1$s' is above the maximum -- using default value instead", key);
				} else {
					LOG.warn("Resolved int value for key '%1$s' %2$s is above the maximum of %3$s -- using default value of %4$s instead", key, value, max, defaultValue);
				}
				return defaultValue;
			}
		}
		return value;
	}

	public static Long getFWLong(final String key, final long min, final long max, final Long defaultValue, final boolean suppressValueLogging) {
		return getLong(Config.FW_PREFIX + key, min, max, defaultValue, suppressValueLogging);
	}
	public static Long getFWLong(final String key, final long min, final long max, final Long defaultValue) {
		return getLong(Config.FW_PREFIX + key, min, max, defaultValue, false);
	}
	public static Long getFWLong(final String key, final Long defaultValue) {
		return getLong(Config.FW_PREFIX + key, Long.MIN_VALUE, Long.MAX_VALUE, defaultValue, false);
	}
	public static Long getLong(final String key, final long min, final long max, final Long defaultValue) {
		return getLong(key, min, max, defaultValue, false);
	}
	public static Long getLong(final String key, final Long defaultValue) {
		return getLong(key, Long.MIN_VALUE, Long.MAX_VALUE, defaultValue, false);
	}
	public static Long getLong(final String key, final long min, final long max, final Long defaultValue, final boolean suppressValueLogging) {
		final Long value = (Long)getValue(key, defaultValue, suppressValueLogging, m_longGetter);
		if (value != null) {
			if (value < min) {
				if (suppressValueLogging) {
					LOG.warn("Resolved long value for key '%1$s' is below the minimum -- using default value instead", key);
				} else {
					LOG.warn("Resolved long value for key '%1$s' %2$s is below the minimum of %3$s -- using default value of %4$s instead", key, value, min, defaultValue);
				}
				return defaultValue;
			}
			if (value > max) {
				if (suppressValueLogging) {
					LOG.warn("Resolved long value for key '%1$s' is above the maximum -- using default value instead", key);
				} else {
					LOG.warn("Resolved long value for key '%1$s' %2$s is above the maximum of %3$s -- using default value of %4$s instead", key, value, max, defaultValue);
				}
				return defaultValue;
			}
		}
		return value;
	}

	public static Boolean getFWBoolean(final String key, final Boolean defaultValue) {
		return getBoolean(Config.FW_PREFIX + key, defaultValue);
	}
	public static Boolean getBoolean(final String key, final Boolean defaultValue) {
		final Boolean value = (Boolean)getValue(key, defaultValue, false, m_boolGetter);
		return value;
	}

	public static String getFWString(final String key, final String defaultValue, final boolean suppressValueLogging) {
		return getString(Config.FW_PREFIX + key, defaultValue, suppressValueLogging);
	}
	public static String getFWString(final String key, final String defaultValue) {
		return getString(Config.FW_PREFIX + key, defaultValue, false);
	}
	public static String getString(final String key, final String defaultValue, final boolean suppressValueLogging) {
		return (String)getValue(key, defaultValue, suppressValueLogging, m_stringGetter);

	}
	public static String getString(final String key, final String defaultValue) {
		return (String)getValue(key, defaultValue, false, m_stringGetter);

	}

	private static Object getValue(final String key, final Object defaultValue, final boolean suppressValueLogging, final IValueGetter getter) {
		Setting s = null;
		// if (m_operationInitialized) {
		//     final Operation.IPrivateContext ctx = (Operation.IPrivateContext)Operation.current();
		//     if (ctx != null) {
		//         s = ctx.getConfigSetting(key);
		//     }
		// }
		if (s == null) {
			final Map<String, Setting> settings = m_settings.get();
			s = settings.get(key);
		}
		if (s == null) {
			if (PRINT_DEBUG_MESSAGES) {
				if (suppressValueLogging) {
					LOG.info("%1$s: ******** (default)", key);
				} else {
					LOG.info("%1$s: %2$s (default)", key, defaultValue);
				}
			}
			return defaultValue;
		}
		final Object value = getter.getValueAs(s, defaultValue);
		if (value == null) {
			if (suppressValueLogging) {
				LOG.warn("Cannot interpret key '%1$s' from %2$s value ******** as an %3$s -- using default value ******** instead", s.name, s.source, getter.name());
			} else {
				LOG.warn("Cannot interpret key '%1$s' from %2$s value '%3$s' as an %4$s -- using default value %5$s instead", s.name, s.source, s.strValue, getter.name(), defaultValue);
			}
		} else {
			if (PRINT_DEBUG_MESSAGES) {
				if (suppressValueLogging) {
					LOG.info("%1$s: ******** (%2$s)", key, s.source);
				} else {
					LOG.info("%1$s: %2$s (%3$s)", key, value, s.source);
				}
			}
		}
		return value;
	}

	static final class Setting {
		private final String name;
		private final String source;

		private final String strValue;
		private Integer intValue;
		private Long longValue;
		private Boolean boolValue;

		@SuppressWarnings("unused")
		private Setting overrides; // This is here just for debugging purposes

		private Setting(String name, String value, String source) {
			this.name = name;
			strValue = value;
			this.source = source;
		}
	}

	private interface IValueGetter {
		String name();
		Object getValueAs(Setting s, Object defaultValue);
	}

	private static final class GetValueAsInt implements IValueGetter {
		@Override
		public String name() {
			return "int";
		}

		@Override
		public Object getValueAs(Setting s, Object defaultValue) {
			if (s.intValue == null) {
				try {
					// It really is ok if multiple threads try to do this at the same time...
					// it will end up with the same value.  No reason to synchronize here.
					s.intValue = Integer.parseInt(s.strValue);
				} catch(Throwable t) {
					return null;
				}
			}
			return s.intValue;
		}
	}

	private static final class GetValueAsLong implements IValueGetter {
		@Override
		public String name() {
			return "long";
		}

		@Override
		public Object getValueAs(Setting s, Object defaultValue) {
			if (s.longValue == null) {
				try {
					// It really is ok if multiple threads try to do this at the same time...
					// it will end up with the same value.  No reason to synchronize here.
					s.longValue = Long.parseLong(s.strValue);
				} catch(Throwable t) {
					return null;
				}
			}
			return s.longValue;
		}
	}

	private static final class GetValueAsBool implements IValueGetter {
		@Override
		public String name() {
			return "boolean";
		}

		@Override
		public Object getValueAs(Setting s, Object defaultValue) {
			if (s.boolValue == null) {
				try {
					// It really is ok if multiple threads try to do this at the same time...
					// it will end up with the same value.  No reason to synchronize here.
					s.boolValue = Boolean.parseBoolean(s.strValue);
				} catch(Throwable t) {
					return null;
				}
			}
			return s.boolValue;
		}
	}

	private static final class GetValueAsString implements IValueGetter {
		@Override
		public String name() {
			return "string";
		}

		@Override
		public Object getValueAs(Setting s, Object defaultValue) {
			return s.strValue;
		}
	}

	public interface IConfigLogger {
		void info(String format, Object...args);
		void warn(String format, Object...args);
		void fatal(String format, Object... args);
	}

	private static class ConsoleLogger implements IConfigLogger {

		@Override
		public void info(String format, Object... args)
		{
			System.out.println("[Config] " + String.format(format, args));
		}

		@Override
		public void warn(String format, Object... args)
		{
			System.out.println("[Config] WARN: " + String.format(format, args));
		}

		@Override
		public void fatal(String format, Object... args)
		{
			System.out.println("[Config] FATAL: " + String.format(format, args));
		}

	}
}
