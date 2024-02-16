package com.denaliai.fw.http;

import com.denaliai.fw.Application;
import com.denaliai.fw.log4j2.TestCaptureAppender;
import com.denaliai.fw.utility.http.MinimalHTTPRequest;
import com.denaliai.fw.utility.http.MinimalHTTPResponse;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HttpServer_IO_Test extends TestBase {

	@Test
	public void testNullHandler() {
		HttpServer httpServer = HttpServer.builder()
			.listenPort(10000)
			.build();
		Assertions.assertTrue(httpServer.start().awaitUninterruptibly(1000));

		String responseData = MinimalHTTPRequest.get("localhost", 10000, "/nothing_to_get.html").body;
		Assertions.assertEquals("", responseData);

		Assertions.assertTrue(httpServer.stop().awaitUninterruptibly(1000));

		boolean onRequest = false;
		for(String logEntry : TestCaptureAppender.getCaptured()) {
			if (logEntry.contains("NullHandler") && logEntry.contains("onRequest()")) {
				onRequest = true;
				break;
			}
		}
		Assertions.assertTrue(onRequest, "Did not find onRequest");
	}

	@Test
	public void testUserHandler() {
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

		MinimalHTTPResponse response = MinimalHTTPRequest.get("localhost", 10000, "/nothing_to_get.html");
		Assertions.assertEquals(HttpResponseStatus.OK.code(), response.code);
		Assertions.assertEquals("GOOD", response.body);

		Assertions.assertTrue(httpServer.stop().awaitUninterruptibly(1000));
	}

	@Test
	public void testStatusCode() {
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
							response.respond(HttpResponseStatus.NOT_FOUND, ByteBufUtil.writeAscii(Application.ioBufferAllocator(), ""));
						})
						.build();
		Assertions.assertTrue(httpServer.start().awaitUninterruptibly(1000));

		int code = MinimalHTTPRequest.get("localhost", 10000, "/nothing_to_get.html").code;
		Assertions.assertEquals(HttpResponseStatus.NOT_FOUND.code(), code);

		Assertions.assertTrue(httpServer.stop().awaitUninterruptibly(1000));
	}
}
