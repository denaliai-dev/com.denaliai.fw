package com.denaliai.fw.socket;

import com.denaliai.fw.Application;
import com.denaliai.fw.utility.Pipe;
import com.denaliai.fw.utility.socket.MinimalRawSocketExchange;
import com.denaliai.fw.utility.test.AbstractTestBase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SocketServer_ShutdownWhileInUse_Test extends TestBase {

	@Test
	public void test() throws InterruptedException {
		Promise<Wrapper> responsePromise = Application.newPromise();
		SocketServer socketServer = SocketServer.builder(10000)
			.onData((conn, inPipe, outPipe) -> {
				ByteBuf buf = inPipe.poll();
				String request = new String(ByteBufUtil.getBytes(buf));
				buf.release();
				LogManager.getLogger(SocketServer_ShutdownWhileInUse_Test.class).info("[{}] onData(\"{}\")", conn.remoteHostAddress(), request);
				if (request.equals("REQUEST")) {
					responsePromise.setSuccess(new Wrapper(conn, outPipe));
				} else {
					responsePromise.setFailure(new RuntimeException("Got '" + request + "' sent to me"));
					conn.close();
				}
			})
			.build();
		Assertions.assertTrue(socketServer.start().awaitUninterruptibly(1000));

		CountDownLatch submitStartedLatch = new CountDownLatch(1);
		CountDownLatch submitDoneLatch = new CountDownLatch(1);
		// Start a request in the background
		// Can't do this in the pool since we are blocking
		new Thread(() -> {
			submitStartedLatch.countDown();
			try {
				String response = MinimalRawSocketExchange.submit("localhost", 10000, "REQUEST");
				LogManager.getLogger(SocketServer_ShutdownWhileInUse_Test.class).info("MinimalRawSocketExchange=\"{}\"", response);
				if (response.equals("RESPONSE")) {
					submitDoneLatch.countDown();
				} else {
					LogManager.getLogger(SocketServer_ShutdownWhileInUse_Test.class).error("Got '{}' from the server", response);
				}
			} catch (IOException e) {
				LogManager.getLogger(SocketServer_ShutdownWhileInUse_Test.class).error("unhandled exception", e);
			}
		}).start();
		Assertions.assertTrue(submitStartedLatch.await(5000, TimeUnit.MILLISECONDS));
		Assertions.assertTrue(responsePromise.awaitUninterruptibly(5000));

		// Start the server shutdown
		Future<Void> stopDone = socketServer.stop();

		// Wait a little bit
		Thread.sleep(250);

		// We expect that the server has not stopped yet
		Assertions.assertTrue(!stopDone.isDone());

		// Respond to ourselves
		Wrapper w = responsePromise.getNow();
		w.producer.submit(ByteBufUtil.writeAscii(Application.allocator(), "RESPONSE"));
		w.connection.close();

		// The server should now stop
		Assertions.assertTrue(stopDone.awaitUninterruptibly(5000));

		// Check that our submit was good and didn't error out
		Assertions.assertTrue(submitDoneLatch.await(5000, TimeUnit.MILLISECONDS));
	}

	private static final class Wrapper {
		final SocketServer.ISocketConnection connection;
		final Pipe.IProducer producer;

		Wrapper(SocketServer.ISocketConnection connection, Pipe.IProducer producer) {
			this.connection = connection;
			this.producer = producer;
		}
	}
}
