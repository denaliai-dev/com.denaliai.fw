package com.denaliai.fw.utility.test;

import com.denaliai.fw.Application;
import com.denaliai.fw.ApplicationBootstrap;
import com.denaliai.fw.log4j.TestCaptureAppender;
import com.denaliai.fw.log4j.TestLevelCheckAppender;

public abstract class AbstractTestBase {

	/**
	 * Helpful properties for debug tracing:
	 *    System.setProperty("com.denaliai.logger-level-root", "TRACE");
	 *    System.setProperty("com.denaliai.logger-level-fw", "TRACE");
	 *    System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "TRACE");
	 *
	 */
	protected static void bootstrap() {
		System.setProperty("io.netty.leakDetection.level", "PARANOID");
		System.setProperty("io.netty.leakDetection.targetRecords", Integer.toString(Integer.MAX_VALUE));
		ApplicationBootstrap.bootstrapUnitTest("test-log4j2.xml");
		TestCaptureAppender.clear();
		TestLevelCheckAppender.reset();
		Application.run();
	}

	protected static void deinit() {
		TestCaptureAppender.clear();
		TestLevelCheckAppender.reset();
		Application.terminate();
	}
}
