package com.denaliai.fw.socket;

import com.denaliai.fw.Application;
import com.denaliai.fw.utility.Pipe;
import com.denaliai.fw.utility.test.AbstractTestBase;
import com.denaliai.fw.utility.test.TestUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.concurrent.Promise;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SocketClient_ConnectToServer_Test extends AbstractTestBase {
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
		SocketServer socketServer = SocketServer.builder(10000)
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
		Assertions.assertTrue(socketServer.start().awaitUninterruptibly(1000));

		Wrapper wrapper = new Wrapper();
		SocketClient client = SocketClient.builder("localhost", 10000).onConnect((conn, outPipe) -> {
			wrapper.connectedPromise.setSuccess(outPipe);
		}).onData((conn, inPipe, outPipe) -> {
			ByteBuf buf = inPipe.poll();
			String response = new String(ByteBufUtil.getBytes(buf));
			buf.release();
			LogManager.getLogger(SocketServer_IO_Test.class).info("onData(\"{}\")", response);
			if (response.equals("RESPONSE")) {
				wrapper.responsePromise.setSuccess(null);
			} else {
				wrapper.responsePromise.setFailure(new RuntimeException("Response is " + response));
			}
		}).build();
		for(int i=0; i<16; i++) {
			wrapper.reset();
			Assertions.assertTrue(client.start().awaitUninterruptibly(1000));
			Assertions.assertTrue(wrapper.connectedPromise.awaitUninterruptibly(1000));
			wrapper.connectedPromise.getNow().submit(ByteBufUtil.writeAscii(Application.allocator(), "REQUEST"));

			Assertions.assertTrue(wrapper.responsePromise.awaitUninterruptibly(1000));
			Assertions.assertTrue(wrapper.responsePromise.isSuccess());

			Assertions.assertTrue(client.stop().awaitUninterruptibly(1000));
		}

		Assertions.assertTrue(socketServer.stop().awaitUninterruptibly(1000));

		TestUtils.snapshotAndPrintCounters();
	}


	private static class Wrapper {
		Promise<Pipe.IProducer> connectedPromise;
		Promise<Void> responsePromise;

		void reset() {
			connectedPromise = Application.newPromise();
			responsePromise = Application.newPromise();
		}
	}
}
