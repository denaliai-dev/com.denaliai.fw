package com.denaliai.fw;

import com.denaliai.fw.utility.test.AbstractTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class TestBase extends AbstractTestBase {
	@BeforeAll
	public static void baseInit() {
		bootstrap();
	}

	@AfterAll
	public static void baseDeinit() {
		deinit();
	}
}
