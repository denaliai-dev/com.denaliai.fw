package com.denaliai.fw.http;

import com.denaliai.fw.Application;
import com.denaliai.fw.utility.test.AbstractTestBase;
import com.denaliai.fw.utility.test.TestUtils;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.concurrent.Promise;
import org.asynchttpclient.netty.util.ByteBufUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class HttpClient_ConnectToServer_Test extends AbstractTestBase {
	@BeforeAll
	public static void init() {
		System.setProperty("com.denaliai.logger-level-root", "INFO");
		AbstractTestBase.bootstrap();
	}

	@AfterAll
	public static void deinit() {
		AbstractTestBase.deinit();
	}

	@Test
	public void test() throws InterruptedException {
		CountDownLatch requestGoodLatch = new CountDownLatch(1);
		HttpServer httpServer = HttpServer.builder()
			.listenPort(10000)
			.onRequest((HttpServer.IHttpRequest request, HttpServer.IHttpResponse response)->{
				if (!request.requestMethod().equals("GET")) {
					response.respondOk(ByteBufUtil.writeAscii(Application.ioBufferAllocator(), "Request method is " + request.requestMethod() + " instead of GET"));
					return;
				}
				if (!request.requestURI().equals("/nothing_to_get.html")) {
					response.respondOk(ByteBufUtil.writeAscii(Application.ioBufferAllocator(), "Request URI is '" + request.requestURI() + "' instead of '/nothing_to_get.html'"));
					return;
				}
				response.respondOk(ByteBufUtil.writeAscii(Application.ioBufferAllocator(), "GOOD"));
			})
			.build();
		Assertions.assertTrue(httpServer.start().awaitUninterruptibly(1000));

		HttpClient client = new HttpClient();
		Assertions.assertTrue(client.start().awaitUninterruptibly(1000));

		LinkedList<Promise<String>> promises = new LinkedList<>();
		for(int i=0; i<16; i++) {
			final Promise<String> responsePromise = Application.newPromise();
			promises.add(responsePromise);
			HttpClientRequest req = HttpClientRequest.create("http://localhost:10000/nothing_to_get.html");
			req.onCompletionHandler(new HttpClientRequest.IRequestCompletionHandler() {
				@Override
				public void requestComplete(HttpClientResponse response) {
					if (response.getHttpStatusCode() == 200) {
						responsePromise.setSuccess(ByteBufUtils.byteBuf2String(StandardCharsets.UTF_8, response.getResponseData()));
					} else {
						responsePromise.setFailure(new RuntimeException("Status is " + response.getHttpStatusCode()));
					}
				}

				@Override
				public void requestFailed(Throwable cause) {
					responsePromise.setFailure(cause);
				}
			});
			client.submit(req);
		}
		for(Promise<String> responsePromise : promises) {
			Assertions.assertTrue(responsePromise.awaitUninterruptibly(1000));
			Assertions.assertEquals("GOOD", responsePromise.getNow());
		}

		Assertions.assertTrue(client.stop().awaitUninterruptibly(1000));
		Assertions.assertTrue(httpServer.stop().awaitUninterruptibly(1000));

		TestUtils.snapshotAndPrintCounters();
	}
}
