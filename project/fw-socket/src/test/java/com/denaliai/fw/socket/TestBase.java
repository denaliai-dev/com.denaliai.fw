package com.denaliai.fw.socket;

import com.denaliai.fw.utility.test.AbstractTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class TestBase extends AbstractTestBase {
	@BeforeAll
	public static void baseInit() {
		System.setProperty("com.denaliai.fw.logger-level-root", "DEBUG");
		bootstrap();
	}

	@AfterAll
	public static void baseDeinit() {
		deinit();
	}
}
