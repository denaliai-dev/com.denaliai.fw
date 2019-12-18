package com.denaliai.fw.socket;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SocketServer_Bind_Test extends TestBase {

	@Test
	public void testBindFail_BadPort() throws InterruptedException {
		SocketServer socketServer = SocketServer.builder(65535+1)
			.build();
		CountDownLatch latch = new CountDownLatch(1);
		socketServer.start().addListener((f) -> {
			Assertions.assertFalse(f.isSuccess());
			latch.countDown();
		});
		Assertions.assertTrue(latch.await(1, TimeUnit.SECONDS));
	}

	@Test
	public void testBindFail_PortInUse() throws InterruptedException {
		SocketServer boundSocketServer = SocketServer.builder(50000).loggerNameSuffix("GoodServer").build();
		CountDownLatch bindLatch = new CountDownLatch(1);
		boundSocketServer.start().addListener((f) -> {
			Assertions.assertTrue(f.isSuccess());
			bindLatch.countDown();
		});
		Assertions.assertTrue(bindLatch.await(1, TimeUnit.SECONDS));

		SocketServer failBindSocketServer = SocketServer.builder(50000).loggerNameSuffix("BadServer").build();
		CountDownLatch bindFailedLatch = new CountDownLatch(1);
		failBindSocketServer.start().addListener((f) -> {
			Assertions.assertFalse(f.isSuccess());
			bindFailedLatch.countDown();
		});
		Assertions.assertTrue(bindFailedLatch.await(1, TimeUnit.SECONDS));

		Assertions.assertTrue(boundSocketServer.stop().awaitUninterruptibly(1000));
	}}
