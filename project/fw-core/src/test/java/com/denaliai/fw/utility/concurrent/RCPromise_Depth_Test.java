package com.denaliai.fw.utility.concurrent;

import com.denaliai.fw.Application;
import com.denaliai.fw.TestBase;
import com.denaliai.fw.log4j.TestLevelCheckAppender;
import io.netty.channel.DefaultEventLoopGroup;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RCPromise_Depth_Test extends TestBase {

	@BeforeAll
	public static void init() {
		// Force a spawn for notify listener
		System.setProperty("com.bk.db.concurrent.RCPromise.max-notify-depth", "0");
	}

	@Test
	public void testDepthAsyncLaunch() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(2);
		Thread launchingThread = Thread.currentThread();

		RCPromise<Integer> p = RCPromise.create(Application.getTaskPool());
		p.addListener((f, paramBag) -> {
			Assertions.assertNotEquals(launchingThread, Thread.currentThread());
			Assertions.assertTrue(p.isDone());
			Assertions.assertTrue(p.isSuccess());
			Assertions.assertEquals(1, f.get());
			latch.countDown();
		});
		p.addListener((f, paramBag) -> {
			Assertions.assertNotEquals(launchingThread, Thread.currentThread());
			latch.countDown();
		});

		p.setSuccess(1);
		Assertions.assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
		Assertions.assertFalse(TestLevelCheckAppender.gotLevelAtOrHigher(Level.WARN));

		// This may or may not be the last release
		p.release();
	}
}
