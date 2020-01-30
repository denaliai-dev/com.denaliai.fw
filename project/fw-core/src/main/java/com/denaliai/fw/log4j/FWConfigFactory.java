package com.denaliai.fw.log4j;

import com.denaliai.fw.config.Config;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.composite.CompositeConfiguration;

import java.net.URI;
import java.util.Arrays;

public class FWConfigFactory extends ConfigurationFactory {
	@Override
	protected String[] getSupportedTypes() {
		return new String[0];
	}

	@Override
	public Configuration getConfiguration(final LoggerContext loggerContext, final String name, final URI configLocation, final ClassLoader loader) {
		return getConfiguration(loggerContext, null);
	}

	@Override
	public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
		ConfigurationBuilder<BuiltConfiguration> builder = newConfigurationBuilder();

		builder.setConfigurationName("com.denaliai.fw");
		builder.setStatusLevel(Level.toLevel(Config.getFWString("logging-debug", "DEBUG")));
		builder.setShutdownHook("disable");

		LayoutComponentBuilder standard = builder.newLayout("PatternLayout");
		standard.addAttribute("pattern", Config.getFWString("log-appender-pattern", "[%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %c{1} - %msg%n"));

		AppenderComponentBuilder console = builder.newAppender("Console-Appender", "Console");
		console.addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
		console.addAttribute("name", "Console-Appender");
		console.add(standard);

		builder.add(console);

		Level rootLevel = Level.toLevel(Config.getFWString("logger-level-fw", "INFO"));
		builder.add(builder.newRootLogger(rootLevel).add(builder.newAppenderRef("Console-Appender")));

		BuiltConfiguration coreConfig = builder.build();
		if (System.getProperty(CONFIGURATION_FILE_PROPERTY) == null) {
			return coreConfig;
		}
		removeConfigurationFactory(this);
		try {
			AbstractConfiguration xmlConfig = (AbstractConfiguration)getInstance().getConfiguration(loggerContext, getClass().getSimpleName(), null);
			return new CompositeConfiguration(Arrays.asList(coreConfig, xmlConfig));
		} finally {
			setConfigurationFactory(this);
		}
	}

}
