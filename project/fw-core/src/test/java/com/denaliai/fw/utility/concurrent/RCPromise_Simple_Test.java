package com.denaliai.fw.utility.concurrent;

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

public class RCPromise_Simple_Test extends TestBase {

	@Test
	public void testSimpleSuccessAndFail() {
		DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);

		// Since we are using recycled classes, let's make sure we test a bunch
		for(int i=0; i<100; i++) {
			RCPromise<Integer> p = RCPromise.create(group);
			Assertions.assertFalse(p.isDone());
			Assertions.assertFalse(p.isSuccess());
			p.setSuccess(1);
			Assertions.assertTrue(p.isDone());
			Assertions.assertTrue(p.isSuccess());
			Assertions.assertEquals(1, p.get());
			Assertions.assertTrue(p.release());

			RCPromise<Integer> p2 = RCPromise.create(group);
			Assertions.assertFalse(p2.isDone());
			Assertions.assertFalse(p2.isSuccess());
			p2.setFailure(new RuntimeException());
			Assertions.assertTrue(p2.isDone());
			Assertions.assertFalse(p2.isSuccess());
			Assertions.assertTrue(p2.cause() instanceof RuntimeException);
			Assertions.assertTrue(p2.release());
		}
	}

	@Test
	public void testSimpleListeners() throws InterruptedException {
		DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
		CountDownLatch latch = new CountDownLatch(2);
		Thread launchingThread = Thread.currentThread();

		RCPromise<Integer> p = RCPromise.create(group);
		p.addListener((f, paramBag) -> {
			Assertions.assertEquals(launchingThread, Thread.currentThread());
			Assertions.assertTrue(p.isDone());
			Assertions.assertTrue(p.isSuccess());
			Assertions.assertEquals(1, f.get());
			latch.countDown();
		});
		p.addListener((f, paramBag) -> {
			Assertions.assertEquals(launchingThread, Thread.currentThread());
			latch.countDown();
		});

		p.setSuccess(1);
		Assertions.assertTrue(latch.await(0, TimeUnit.MILLISECONDS));
		Assertions.assertFalse(TestLevelCheckAppender.gotLevelAtOrHigher(Level.WARN));

		CountDownLatch latch2 = new CountDownLatch(1);
		p.addListener((f, paramBag) -> {
			Assertions.assertEquals(launchingThread, Thread.currentThread());
			Assertions.assertTrue(p.isDone());
			Assertions.assertTrue(p.isSuccess());
			Assertions.assertEquals(1, f.get());
			latch2.countDown();
		});
		Assertions.assertTrue(latch2.await(0, TimeUnit.MILLISECONDS));
		Assertions.assertFalse(TestLevelCheckAppender.gotLevelAtOrHigher(Level.WARN));

		p.addListener((f, paramBag) -> {
			RuntimeException ex = new RuntimeException("This is part of the test and is a good result");
			ex.setStackTrace(new StackTraceElement[0]);
			throw ex;
		});
		Assertions.assertTrue(TestLevelCheckAppender.gotLevelAtOrHigher(Level.WARN));

		Assertions.assertTrue(p.release());
	}
}
