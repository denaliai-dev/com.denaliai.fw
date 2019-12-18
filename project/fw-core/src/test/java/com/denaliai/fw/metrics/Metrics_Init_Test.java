package com.denaliai.fw.metrics;

import com.denaliai.fw.TestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Metrics_Init_Test extends TestBase {
	@Test
	public void testAddMetricProgressively() throws InterruptedException {
		MetricsEngine.IMetricTimer timer = MetricsEngine.startTimer();
		CounterAndRateMetric counter = MetricsEngine.newCounterAndRateMetric("counter");
		counter.increment();
		TotalCounterMetric total = MetricsEngine.newTotalCounterMetric("total");
		total.increment();
		total.increment();
		DurationRateMetric rate = MetricsEngine.newRateMetric("rate");
		counter.increment();
		total.decrement();
		rate.record(timer);
		timer.close();

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
		Assertions.assertTrue(doneLatch.await(500, TimeUnit.MILLISECONDS));

		Assertions.assertEquals(2, metricSnapshot.get("counter.count"));
		Assertions.assertEquals(1, metricSnapshot.get("total.count"));
		Assertions.assertEquals(1, metricSnapshot.get("rate.count"));
		for(Map.Entry<String, Long> entry : metricSnapshot.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		System.out.println("done");

		CountDownLatch doneLatch2 = new CountDownLatch(1);
		metricSnapshot.clear();
		MetricsEngine.snapshot(new MetricsEngine.ISnapshotConsumer() {
			@Override
			public void apply(String metricKey, long value) {
				metricSnapshot.put(metricKey, value);
			}

			@Override
			public void done() {
				doneLatch2.countDown();
			}
		});
		Assertions.assertTrue(doneLatch2.await(500, TimeUnit.MILLISECONDS));

		Assertions.assertEquals(0, metricSnapshot.get("counter.count"));
		Assertions.assertEquals(0, metricSnapshot.get("counter.rate-per-minute"));
		Assertions.assertEquals(1, metricSnapshot.get("total.count"));
		Assertions.assertEquals(0, metricSnapshot.get("rate.count"));
		Assertions.assertEquals(0, metricSnapshot.get("rate.max-duration-ms"));
		Assertions.assertEquals(0, metricSnapshot.get("rate.min-duration-ms"));
		Assertions.assertEquals(0, metricSnapshot.get("rate.avg-duration-ms"));
		Assertions.assertEquals(0, metricSnapshot.get("rate.rate-per-minute"));
		for(Map.Entry<String, Long> entry : metricSnapshot.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		System.out.println("done");
	}
}
