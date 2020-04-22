package com.denaliai.fw.http;

import com.denaliai.fw.Application;
import com.denaliai.fw.log4j2.TestCaptureAppender;
import com.denaliai.fw.utility.http.MinimalHTTPRequest;
import io.netty.buffer.ByteBufUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

public class HttpServer_IO_Test extends TestBase {

	@Test
	public void testNullHandler() {
		HttpServer httpServer = HttpServer.builder()
			.listenPort(10000)
			.build();
		CountDownLatch latch = new CountDownLatch(1);
		Assertions.assertTrue(httpServer.start().awaitUninterruptibly(1000));

		String responseData = MinimalHTTPRequest.get("localhost", 10000, "/nothing_to_get.html");
		Assertions.assertEquals("", responseData);

		boolean onConnect = false;
		boolean onDisconnect = false;
		boolean onRequest = false;
		for(String logEntry : TestCaptureAppender.getCaptured()) {
			if (!onConnect && logEntry.contains("NullHandler") && logEntry.contains("onConnect()")) {
				onConnect = true;
			} else if (!onDisconnect && logEntry.contains("NullHandler") && logEntry.contains("onDisconnect()")) {
				onDisconnect = true;
			} else if (!onRequest && logEntry.contains("NullHandler") && logEntry.contains("onRequest()")) {
				onRequest = true;
			}
		}
		Assertions.assertTrue(onConnect, "Did not find onConnect");
		Assertions.assertTrue(onDisconnect, "Did not find onDisconnect");
		Assertions.assertTrue(onRequest, "Did not find onRequest");

		Assertions.assertTrue(httpServer.stop().awaitUninterruptibly(1000));
	}

	@Test
	public void testUserHandler() {
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

		String responseData = MinimalHTTPRequest.get("localhost", 10000, "/nothing_to_get.html");
		Assertions.assertEquals("GOOD", responseData);

		Assertions.assertTrue(httpServer.stop().awaitUninterruptibly(1000));
	}
}
