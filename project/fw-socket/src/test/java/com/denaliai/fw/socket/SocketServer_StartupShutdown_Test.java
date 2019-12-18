package com.denaliai.fw.socket;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SocketServer_StartupShutdown_Test extends TestBase {

	@Test
	public void test() {
		SocketServer httpServer = SocketServer.builder(10000).build();
		Assertions.assertTrue(httpServer.start().awaitUninterruptibly(1000));
		Assertions.assertTrue(httpServer.stop().awaitUninterruptibly(1000));
	}
}
