package com.denaliai.fw;

import com.denaliai.fw.config.Config;
import com.denaliai.fw.config.Log4jConfigLogger;
import com.denaliai.fw.log4j.FWConfigFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.Configurator;

public class ApplicationInit {

	static void initLogging() {
		LoggerContext.getContext(false).start();

		Config.setConfigLogger(new Log4jConfigLogger());
		for(String configKey : Config.keys()) {
			if (configKey.startsWith("logger.")) {
				final String loggerName = configKey.substring(7);
				final String levelString = Config.getString(configKey, null);
				final Level level;
				try {
					level = Level.getLevel(levelString);
				} catch(Exception ex) {
					LogManager.getLogger(ApplicationInit.class).error("Could not parse logger level '{}' for '{}'", levelString, configKey, ex);
					continue;
				}
				if (level == null) {
					LogManager.getLogger(ApplicationInit.class).error("Could not parse logger level '{}' for '{}'", levelString, configKey);

				} else if (loggerName.equals("root")) {
					Configurator.setRootLevel(level);

				} else {
					Configurator.setLevel(loggerName, level);
				}
			}
		}
		LogManager.getLogger(ApplicationInit.class).info("Logging initialized");
		if (Application.isTerminating()) {
			LogManager.getLogger(ApplicationInit.class).error("Attempt to re-initialize a terminated application");
			Application.fatalExit();
		}
	}
}
