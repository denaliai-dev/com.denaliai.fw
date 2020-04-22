package com.denaliai.fw.utility.test;

import com.denaliai.fw.metrics.MetricsEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestUtils {
	public static void snapshotAndPrintCounters() {

		CountDownLatch doneLatch = new CountDownLatch(1);
		Map<String, Long> metricSnapshot = new HashMap<String, Long>();
		MetricsEngine.snapshot(new MetricsEngine.ISnapshotConsumer() {
			@Override
			public void apply(String metricKey, long value) {
				metricSnapshot.put(metricKey, value);
			}

			@Override
			public void done() {
				doneLatch.countDown();
			}
		});
		try {
			if (!doneLatch.await(500, TimeUnit.MILLISECONDS)) {
				throw new RuntimeException("Timeout waiting for snapshot to finish");
			}
		} catch (InterruptedException e) {
			throw new RuntimeException("Problem waiting for snapshot to finish", e);
		}
		List<Map.Entry<String,Long>> sorted = new ArrayList<>(metricSnapshot.entrySet());
		sorted.sort((a, b) -> {return a.getKey().compareTo(b.getKey());});
		for (Map.Entry<String, Long> entry : sorted) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		System.out.println("done");
	}
}
