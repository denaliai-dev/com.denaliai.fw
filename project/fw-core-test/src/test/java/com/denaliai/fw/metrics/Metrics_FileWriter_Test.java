package com.denaliai.fw.metrics;

import com.denaliai.fw.TestBase;
import com.denaliai.fw.utility.concurrent.RCInteger;
import com.denaliai.fw.utility.concurrent.RCPromise;
import com.denaliai.fw.utility.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Metrics_FileWriter_Test extends TestBase {
	@Test
	public void test() throws InterruptedException {
		File path = new File(MetricsEngine.METRICS_PATH);
		if (path.exists()) {
			FileUtils.deleteDir(path);
		}
		MetricsEngine.startTimedSnapshotFileWriter(100);
		Thread.sleep(350);
		CountDownLatch latch = new CountDownLatch(1);
		MetricsEngine.stopTimedSnapshot().addListener((f) -> {
			latch.countDown();
		});
		Assertions.assertTrue(latch.await(500, TimeUnit.MILLISECONDS));

	}

}
