package com.denaliai.fw.utility.concurrent;

import com.denaliai.fw.TestBase;
import com.denaliai.fw.log4j.TestCaptureAppender;
import com.denaliai.fw.log4j.TestLevelCheckAppender;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RCSucceededFuture_Test extends TestBase {

	@Test
	public void test() {
		RCFuture<Integer> p = RCSucceededFuture.create(1);
		Assertions.assertTrue(p.isDone());
		Assertions.assertTrue(p.isSuccess());
		Assertions.assertEquals(1, p.get());
		Assertions.assertTrue(p.release());

		RCFuture<Integer> p2 = RCSucceededFuture.create(2);
		Assertions.assertTrue(p2.isDone());
		Assertions.assertTrue(p2.isSuccess());
		Assertions.assertEquals(2, p2.get());

		p2.addListener((f, paramBag) -> {
			Assertions.assertTrue(f.isDone());
			Assertions.assertTrue(f.isSuccess());
			Assertions.assertEquals(2, f.get());
		});
		Assertions.assertTrue(p2.release());

		Assertions.assertFalse(TestLevelCheckAppender.gotLevelAtOrHigher(Level.WARN));
	}
}
