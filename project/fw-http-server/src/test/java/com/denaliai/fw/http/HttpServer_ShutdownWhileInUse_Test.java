package com.denaliai.fw.http;

import com.denaliai.fw.Application;
import com.denaliai.fw.utility.http.MinimalHTTPRequest;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HttpServer_ShutdownWhileInUse_Test extends TestBase {

	@Test
	public void test() throws InterruptedException {
		Promise<HttpServer.IHttpResponse> responsePromise = Application.newPromise();
		HttpServer httpServer = HttpServer.builder()
			.listenPort(10000)
			.onRequest((HttpServer.IHttpRequest request, HttpServer.IHttpResponse response)-> {
				responsePromise.setSuccess(response);
			})
			.build();
		Assertions.assertTrue(httpServer.start().awaitUninterruptibly(1000));

		CountDownLatch requestStartedLatch = new CountDownLatch(1);
		// Start a request in the background
		Application.getTaskPool().submit(() -> {
			requestStartedLatch.countDown();
			MinimalHTTPRequest.get("localhost", 10000, "/nothing_to_get.html");
		});
		Assertions.assertTrue(requestStartedLatch.await(1000, TimeUnit.MILLISECONDS));
		Assertions.assertTrue(responsePromise.awaitUninterruptibly(1000));

		// Start the server shutdown
		Future<Void> stopDone = httpServer.stop();

		// Wait a little bit
		Thread.sleep(250);

		// We expect that the server has not stopped yet
		Assertions.assertTrue(!stopDone.isDone());

		// Respond to ourselves
		responsePromise.getNow().respondOk(Application.allocateIOBuffer());

		// The server should now stop
		Assertions.assertTrue(stopDone.awaitUninterruptibly(1000));

	}
}
