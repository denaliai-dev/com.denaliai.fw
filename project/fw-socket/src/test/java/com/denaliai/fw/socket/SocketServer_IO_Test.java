package com.denaliai.fw.socket;

import com.denaliai.fw.Application;
import com.denaliai.fw.log4j2.TestCaptureAppender;
import com.denaliai.fw.utility.socket.MinimalRawSocketExchange;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class SocketServer_IO_Test extends TestBase {

	@Test
	public void testNullHandler() throws IOException {
		SocketServer socketServer = SocketServer.builder(10000).build();
		CountDownLatch latch = new CountDownLatch(1);
		Assertions.assertTrue(socketServer.start().awaitUninterruptibly(1000));

		String responseData = MinimalRawSocketExchange.submit("localhost", 10000, "REQUEST");
		Assertions.assertEquals("REQUEST", responseData);

		boolean onConnect = false;
		boolean onDisconnect = false;
		boolean onData = false;
		for(String logEntry : TestCaptureAppender.getCaptured()) {
			if (!onConnect && logEntry.contains("NullHandler") && logEntry.contains("onConnect()")) {
				onConnect = true;
			} else if (!onDisconnect && logEntry.contains("NullHandler") && logEntry.contains("onDisconnect()")) {
				onDisconnect = true;
			} else if (!onData && logEntry.contains("NullHandler") && logEntry.contains("onData()")) {
				onData = true;
			}
		}
		Assertions.assertTrue(onConnect, "Did not find onConnect");
		Assertions.assertTrue(onDisconnect, "Did not find onDisconnect");
		Assertions.assertTrue(onData, "Did not find onData");

		Assertions.assertTrue(socketServer.stop().awaitUninterruptibly(1000));
	}

	@Test
	public void testUserHandler() throws IOException {
		CountDownLatch requestGoodLatch = new CountDownLatch(1);
		SocketServer httpServer = SocketServer.builder(10000)
			.onData((conn, inPipe, outPipe) -> {
				ByteBuf buf = inPipe.poll();
				String request = new String(ByteBufUtil.getBytes(buf));
				buf.release();
				LogManager.getLogger(SocketServer_IO_Test.class).info("[{}] onData(\"{}\")", conn.remoteHostAddress(), request);
				if (request.equals("REQUEST")) {
					outPipe.submit(ByteBufUtil.writeAscii(Application.allocator(), "RESPONSE"));
				} else {
					outPipe.submit(ByteBufUtil.writeAscii(Application.allocator(), "request was wrong: '" + request + "'"));
				}
				conn.close();
			})
			.build();
		Assertions.assertTrue(httpServer.start().awaitUninterruptibly(1000));

		String responseData = MinimalRawSocketExchange.submit("localhost", 10000, "REQUEST");
		Assertions.assertEquals("RESPONSE", responseData);

		Assertions.assertTrue(httpServer.stop().awaitUninterruptibly(1000));
	}

}
