package com.denaliai.fw;

import com.denaliai.fw.config.Config;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;

public class ApplicationBootstrap {
	// This is here to force the loading of the Config system before running any methods.  During the config load
	// it can read properties from config files and add them to the System properties.
	@SuppressWarnings("unused")
	private static final Boolean FORCE_CONFIG = Config.getFWBoolean("not-found", null);

	public static void bootstrap(String[] args) {
		System.out.println("ApplicationBootstrap Java " + System.getProperty("java.version"));

		System.setProperty("log4j2.disableJmx", "true");
		System.setProperty("log4j.configurationFile", "core-log4j2.xml");
		PluginManager.addPackage("com.denaliai.fw.log4j");
		processCommandLine(args);
		Application.initLogging();
	}

	public static void bootstrap(String applicationLog4jXMLfilename, String[] args) {
		System.out.println("ApplicationBootstrap Java " + System.getProperty("java.version"));

		System.setProperty("log4j2.disableJmx", "true");
		System.setProperty("log4j.configurationFile", "core-log4j2.xml," + applicationLog4jXMLfilename);
		PluginManager.addPackage("com.denaliai.fw.log4j");
		processCommandLine(args);
		Application.initLogging();
	}

	public static void bootstrapUnitTest(String applicationLog4jXMLfilename) {
		System.out.println("ApplicationBootstrap Java " + System.getProperty("java.version"));
		System.setProperty("com.denaliai.logger-level-root", System.getProperty("com.denaliai.logger-level-root", "DEBUG"));

		System.setProperty("log4j2.disableJmx", "true");
		System.setProperty("log4j.configurationFile", "core-log4j2.xml," + applicationLog4jXMLfilename);
		PluginManager.addPackage("com.denaliai.fw.log4j");
		processCommandLine(null);
		Application.initLogging();
	}

	private static void processCommandLine(String[] args) {
		System.setProperty("javax.net.ssl.trustStore", "app-ca-certs");
		if (args != null && args.length != 0 && args[0].equals("--install-cert")) {
			if (args.length != 3) {
				System.out.println("Usage: --install-cert <host> <store password>");
				System.exit(1);
			}
			try {
				//InstallCert.install(args[1], args[2]);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.exit(1);
		}
	}
}
