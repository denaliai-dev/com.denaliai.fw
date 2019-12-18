package com.denaliai.fw.utility.concurrent;

import com.denaliai.fw.Application;
import com.denaliai.fw.TestBase;
import com.denaliai.fw.log4j.TestCaptureAppender;
import com.denaliai.fw.log4j.TestLevelCheckAppender;
import io.netty.channel.DefaultEventLoopGroup;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RCPromise_Depth2_Test extends TestBase {

	@BeforeAll
	public static void init() {
		// Force a spawn for notify listener
		System.setProperty("com.bk.db.concurrent.RCPromise.max-notify-depth", "1");
	}


	@Test
	public void testDepthAsyncLaunch() throws InterruptedException {
		CountDownLatch outerLatch = new CountDownLatch(1);
		CountDownLatch innerLatch = new CountDownLatch(1);
		Thread launchingThread = Thread.currentThread();

		RCPromise<Integer> pInner = RCPromise.create(Application.getTaskPool());
		pInner.addListener((f, paramBag) -> {
			// Inner should have been spawned into separate thread
			Assertions.assertNotEquals(launchingThread, Thread.currentThread());
			Assertions.assertTrue(f.isDone());
			Assertions.assertTrue(f.isSuccess());
			Assertions.assertEquals(2, f.get());
			innerLatch.countDown();
		});
		RCPromise<Integer> pOuter = RCPromise.create(Application.getTaskPool());
		pOuter.addListener((f, paramBag) -> {
			// Outer should run on main thread
			Assertions.assertEquals(launchingThread, Thread.currentThread());
			pInner.setSuccess(2);
			outerLatch.countDown();
		});

		pOuter.setSuccess(1);
		Assertions.assertTrue(outerLatch.await(100, TimeUnit.MILLISECONDS));
		Assertions.assertTrue(innerLatch.await(100, TimeUnit.MILLISECONDS));
		Assertions.assertFalse(TestLevelCheckAppender.gotLevelAtOrHigher(Level.WARN));

		// We can't control when the other threads release their instances, so can't test these are last
		pInner.release();
		pOuter.release();
	}
}
