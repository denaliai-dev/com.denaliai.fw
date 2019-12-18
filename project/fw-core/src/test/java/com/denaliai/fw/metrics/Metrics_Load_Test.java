package com.denaliai.fw.metrics;

import com.denaliai.fw.Application;
import com.denaliai.fw.TestBase;
import com.denaliai.fw.utility.concurrent.RCInteger;
import com.denaliai.fw.utility.concurrent.RCPromise;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Metrics_Load_Test extends TestBase {
	@Test
	public void testAddMetricProgressively() throws InterruptedException {
		RCInteger count1 = RCInteger.create(0);
		RCInteger count2 = RCInteger.create(0);

		WorkerThread[] threads = new WorkerThread[4];
		for(int i=0; i<threads.length; i++) {
			threads[i] = new WorkerThread();
		}
		for(int i=0; i<threads.length; i++) {
			threads[i].start();
		}
		for(int i=0; i<10; i++) {
			Thread.sleep(100);
			run_snapshot(count1, count2);
		}
		for(int i=0; i<threads.length; i++) {
			threads[i].join();
		}
		run_snapshot(count1, count2);
		Assertions.assertEquals(4000, count1.value());
		Assertions.assertEquals(4000, count2.value());
	}

	private void run_snapshot(RCInteger count1, RCInteger count2) throws InterruptedException {
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
		count1.value(count1.value() + metricSnapshot.get("counter.count").intValue());
		count2.value(count2.value() + metricSnapshot.get("rate.count").intValue());
//		for (Map.Entry<String, Long> entry : metricSnapshot.entrySet()) {
//			System.out.println(entry.getKey() + ": " + entry.getValue());
//		}
//		System.out.println("done");
	}

	private static final class WorkerThread extends Thread {
		private static final CounterAndRateMetric m_threadCounter = MetricsEngine.newCounterAndRateMetric("counter");
		private static final DurationRateMetric m_threadDuration = MetricsEngine.newRateMetric("rate");
		final RCPromise promise;

		WorkerThread() {
			this.promise = RCPromise.create();
		}

		@Override
		public void run() {
			try {
				for(int i=0; i<1000; i++) {
					m_threadCounter.increment();
					MetricsEngine.IMetricTimer timer = MetricsEngine.startTimer();
					Thread.sleep(1);
					m_threadDuration.record(timer);
					timer.close();
				}
			} catch(Exception ex) {
				LogManager.getLogger(WorkerThread.class).error("Unhandled", ex);
				this.promise.setFailure(ex);
				return;
			}
			this.promise.setSuccess(null);
		}
	}
}
