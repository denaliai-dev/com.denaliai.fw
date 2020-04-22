package com.denaliai.fw.utility.test;

import com.denaliai.fw.Application;
import com.denaliai.fw.ApplicationBootstrap;
import com.denaliai.fw.log4j2.TestCaptureAppender;
import com.denaliai.fw.log4j2.TestLevelCheckAppender;

public abstract class AbstractTestBase {

	/**
	 * Helpful properties for debug tracing:
	 *    System.setProperty("com.denaliai.logger-level-root", "TRACE");
	 *    System.setProperty("com.denaliai.logger-level-fw", "TRACE");
	 *
	 */
	protected static void bootstrap() {
		System.setProperty("io.netty.leakDetection.level", "PARANOID");
		System.setProperty("io.netty.leakDetection.targetRecords", Integer.toString(Integer.MAX_VALUE));
		System.setProperty("log4j.appConfigurationFile", "test-log4j2.xml");
		System.setProperty("com.denaliai.fw.logger-level-root", System.getProperty("com.denaliai.fw.logger-level-root", "DEBUG"));
		ApplicationBootstrap.bootstrap(new String[]{});
		TestCaptureAppender.clear();
		TestLevelCheckAppender.reset();
		Application.run();
	}

	protected static void deinit() {
		TestCaptureAppender.clear();
		TestLevelCheckAppender.reset();
		Application.terminateAndWait();
	}
}
