package com.denaliai.fw.http;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HttpServer_Bind_Test extends TestBase {

	@Test
	public void testBindFail_BadPort() throws InterruptedException {
		HttpServer httpServer = HttpServer.builder()
			.listenPort(65535+1)
			.build();
		CountDownLatch latch = new CountDownLatch(1);
		httpServer.start().addListener((f) -> {
			Assertions.assertFalse(f.isSuccess());
			latch.countDown();
		});
		Assertions.assertTrue(latch.await(1, TimeUnit.SECONDS));
	}

	@Test
	public void testBindFail_PortInUse() throws InterruptedException {
		HttpServer boundHttpServer = HttpServer.builder().loggerNameSuffix("GoodServer").listenPort(50000).build();
		CountDownLatch bindLatch = new CountDownLatch(1);
		boundHttpServer.start().addListener((f) -> {
			Assertions.assertTrue(f.isSuccess());
			bindLatch.countDown();
		});
		Assertions.assertTrue(bindLatch.await(1, TimeUnit.SECONDS));

		HttpServer failBindHttpServer = HttpServer.builder().loggerNameSuffix("BadServer").listenPort(50000).build();
		CountDownLatch bindFailedLatch = new CountDownLatch(1);
		failBindHttpServer.start().addListener((f) -> {
			Assertions.assertFalse(f.isSuccess());
			bindFailedLatch.countDown();
		});
		Assertions.assertTrue(bindFailedLatch.await(1, TimeUnit.SECONDS));

		Assertions.assertTrue(boundHttpServer.stop().awaitUninterruptibly(1000));
	}}
