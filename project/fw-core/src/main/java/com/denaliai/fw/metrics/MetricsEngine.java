package com.denaliai.fw.metrics;

import com.denaliai.fw.Application;
import com.denaliai.fw.config.Config;
import com.denaliai.fw.utility.ByteBufUtils;
import com.denaliai.fw.utility.concurrent.PerpetualWork;
import io.netty.buffer.ByteBuf;
import io.netty.util.Recycler;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MetricsEngine {
	public static final String METRICS_PATH = Config.getFWString("MetricsEngine.snapshotPath", "metrics");
	private static final Recycler<TimerInstance> TIMER_RECYCLER = new Recycler<TimerInstance>() {
		@Override
		protected TimerInstance newObject(Recycler.Handle<TimerInstance> handle) {
			return new TimerInstance(handle);
		}
	};
	private static final FastThreadLocal<MetricDataInstance> m_currentMetricData = new FastThreadLocal<>();
	private static final SnapshotWorker m_worker = new SnapshotWorker();
	private static final Queue<Object> m_msgQueue = PlatformDependent.newMpscQueue();
	private static final List<MetricBase> m_metrics = new LinkedList<>();
	private static final AtomicInteger m_snapshotNumber = new AtomicInteger(1);
	private static int m_dataIndex = 0;
	private static final CounterMetric m_numShutdownThreads = newCounterMetric("transient-threads");
	private static final CounterMetric m_numDataInstances = newCounterMetric("new-data-instances");
	private static final CounterMetric m_numNumSnapshotRetry = newCounterMetric("snapshot-retries");

	public synchronized static CounterMetric newCounterMetric(String name) {
		CounterMetric m = new CounterMetric(name, m_dataIndex++);
		m_metrics.add(m);
		return m;
	}

	public synchronized static ValueMetric newValueMetric(String name) {
		ValueMetric m = new ValueMetric(name, m_dataIndex++);
		m_metrics.add(m);
		return m;
	}

	public synchronized static CounterAndRateMetric newCounterAndRateMetric(String name) {
		CounterAndRateMetric m = new CounterAndRateMetric(name, m_dataIndex++);
		m_metrics.add(m);
		return m;
	}

	public synchronized static TotalCounterMetric newTotalCounterMetric(String name) {
		TotalCounterMetric m = new TotalCounterMetric(name, m_dataIndex++);
		m_metrics.add(m);
		return m;
	}

	public synchronized static DurationRateMetric newRateMetric(String name) {
		DurationRateMetric m = new DurationRateMetric(name, m_dataIndex++, m_dataIndex++, m_dataIndex++, m_dataIndex++, m_dataIndex++);
		m_metrics.add(m);
		return m;
	}

	private synchronized static List<MetricBase> currentMetrics() {
		return new ArrayList<>(m_metrics);
	}

	private synchronized static List<MetricBase> currentMetrics(List<MetricBase> prev) {
		if (prev == null || prev.size() != m_metrics.size()) {
			return new ArrayList<>(m_metrics);
		}
		return prev;
	}

	public static void snapshot(ISnapshotConsumer consumer) {
		m_msgQueue.add(consumer);
		m_worker.requestMoreWork();
	}

	private static Promise<Void> m_snapshotStopFuture;
	private static AtomicInteger m_timedSnapshotRefCount = new AtomicInteger();
	private static volatile boolean m_timedSnapshotStop;
	private static ScheduledFuture<?> m_timedSnapshot;
	public synchronized static void startTimedSnapshotFileWriter(long snapshotDurationInMS) {
		File path = new File(METRICS_PATH);
		if (!path.exists() && !path.mkdirs()) {
			throw new RuntimeException("Could not create metrics directory: " + path.getAbsolutePath());
		}
		// One ref for start/stop
		retainTimedSnapshotRef();
		m_snapshotStopFuture = Application.newPromise();
		// One ref will be used on every run of SnapshotFileWriter()
		m_timedSnapshot = Application.getTaskPool().scheduleAtFixedRate(new SnapshotTimer(), snapshotDurationInMS, snapshotDurationInMS, TimeUnit.MILLISECONDS);
	}

	public synchronized static Future<Void> stopTimedSnapshot() {
		if (m_snapshotStopFuture == null) {
			return Application.newSucceededFuture();
		}
		if (!m_timedSnapshotStop) {
			m_timedSnapshotStop = true;
			// Cancel scheduled (which may or may not do anything)
			m_timedSnapshot.cancel(false);
			// Release start/stop ref
			releaseTimedSnapshotRef(true);
		}
		return m_snapshotStopFuture;
	}

	private static int retainTimedSnapshotRef() {
		return m_timedSnapshotRefCount.incrementAndGet();
	}

	private static void revertTimedSnapshotRef() {
		m_timedSnapshotRefCount.decrementAndGet();
	}

	private static boolean releaseTimedSnapshotRef(boolean calledFromStop) {
		final boolean isLast = m_timedSnapshotRefCount.decrementAndGet()==0;
		if (isLast) {
			if (calledFromStop) {
				// We were the last snapshot ref so write a final snapshot
				snapshot(new SnapshotFileWriter());
			}
			m_snapshotStopFuture.setSuccess(null);
			return true;
		}
		return false;
	}

	static final int maxDataIndex() {
		return m_dataIndex;
	}

	static final MetricDataInstance current() {
		MetricDataInstance inst = m_currentMetricData.get();
		if (inst == null) {
			inst = newDataInstance();
		}
		return inst;
	}

	static final int currentSnapshotNumber() {
		return m_snapshotNumber.get();
	}

	static MetricDataInstance newDataInstance() {
		MetricDataInstance inst;
		m_currentMetricData.set(inst = new MetricDataInstance(m_dataIndex, m_snapshotNumber.get()));
		m_msgQueue.add(inst);
		m_worker.requestMoreWork();
		return inst;
	}

	private static final long MS_PER_MINUTE = 1000l * 60l;
	public static long toRatePerMinute(long snapshotDurationMS, long snapshotDatum) {
		long multiplier = MS_PER_MINUTE * 1000l / snapshotDurationMS;
		return snapshotDatum * multiplier / 1000l;
	}

	public interface ISnapshotConsumer {
		void apply(String metricKey, long value);
		void done();
	}


//	// TODO implement batch mode
//
//	static MetricDataInstance newLockedDataInstance() {
//		MetricDataInstance inst;
//		m_currentMetricData.set(inst = new MetricDataInstance(m_dataIndex, m_snapshotNumber.get()));
//		inst.lock();
//		m_msgQueue.add(inst);
//		m_worker.requestMoreWork();
//		return inst;
//	}
//
//	public interface IMetricBatch extends AutoCloseable {
//		void increment(SingleIndexMetricBase metric);
//		void decrement(TotalCounterMetric metric);
//		void record(DurationRateMetric m, TimerInstance timer);
//
//		void close();
//	}
//
//	public IMetricBatch batchMetrics() {
//		// Lock once
//		MetricDataInstance inst = current();
//		if (!inst.lock()) {
//			newLockedDataInstance();
//		}
//		// A locked inst (whether current or new) is in the TLS slot, so we are good to use it
//	}

	static void add(int index, int value) {
		MetricDataInstance data = MetricsEngine.current();
		if (!data.add(index, value)) {
			data = MetricsEngine.newDataInstance();
			if (!data.add(index, value)) {
				LogManager.getLogger(MetricsEngine.class).error("Failed to lock new instance!");
			}
		}
	}

	static void increment(int index) {
		MetricDataInstance data = MetricsEngine.current();
		if (!data.increment(index)) {
			data = MetricsEngine.newDataInstance();
			if (!data.increment(index)) {
				LogManager.getLogger(MetricsEngine.class).error("Failed to lock new instance!");
			}
		}
	}

	static void decrement(int index) {
		MetricDataInstance data = MetricsEngine.current();
		if (!data.decrement(index)) {
			data = MetricsEngine.newDataInstance();
			if (!data.decrement(index)) {
				LogManager.getLogger(MetricsEngine.class).error("Failed to lock new instance!");
			}
		}
	}

	static void record(DurationRateMetric m, MetricsEngine.IMetricTimer timer) {
		TimerInstance ti = (TimerInstance)timer;
		MetricDataInstance data = MetricsEngine.current();
		if (!data.record(m, ti)) {
			data = MetricsEngine.newDataInstance();
			if (!data.record(m, ti)) {
				LogManager.getLogger(MetricsEngine.class).error("Failed to lock new instance!");
			}
		}
	}

	public static IMetricTimer startTimer() {
		return TIMER_RECYCLER.get().init();
	}

	public interface IMetricTimer {
		void close();
	}
	static final class TimerInstance implements IMetricTimer {
		private final Recycler.Handle<TimerInstance> m_handle;
		private long m_start;
		private long m_endTime = -1;

		private TimerInstance(Recycler.Handle<TimerInstance> handle) {
			m_handle = handle;
		}

		private TimerInstance init() {
			m_start = System.nanoTime();
			return this;
		}

		private long endTime() {
			if (m_endTime != -1) {
				return m_endTime;
			}
			return m_endTime = System.nanoTime();
		}

		long duration() {
			return endTime() - m_start;
		}

		public void close() {
			m_endTime = -1;
			m_handle.recycle(this);
		}
	}

	private static final class SnapshotWorker extends PerpetualWork {
		private final List<MetricDataInstance> m_temp = new LinkedList<>();
		private long m_periodStart = System.nanoTime();
		private List<MetricDataInstance> m_currentThreadData = new LinkedList<>();

		private boolean m_runningSnapshot;
		private ISnapshotConsumer m_snapshotConsumer;
		private long m_snapshotDuration;
		private long m_snapshotStart;
		private List<MetricDataInstance> m_snapshotThreadInstances;
		private long[] m_snapshotData;
		private List<MetricBase> m_snapshotMetrics;


		@Override
		protected void _doWork() {
			try {
				internal_doWork();
			} catch(Exception ex) {
				LogManager.getLogger(SnapshotWorker.class).error("Unhandled exception in snapshot worker", ex);
			}
		}

		private void internal_doWork() {
			while(true) {
				Object msg = m_msgQueue.poll();
				if (msg == null) {
					break;
				}
				if (msg instanceof ISnapshotConsumer) {
					if (m_runningSnapshot) {
						// Already running a snapshot
						// TODO perhaps do something different
						((ISnapshotConsumer)msg).done();
						continue;
					}
					m_runningSnapshot = true;
					m_snapshotNumber.incrementAndGet();
					m_snapshotConsumer = (ISnapshotConsumer)msg;
					long nowInNS = System.nanoTime();
					m_snapshotDuration = nowInNS - m_periodStart;
					m_snapshotStart = m_periodStart;
					m_snapshotThreadInstances = m_currentThreadData;
					m_currentThreadData = new LinkedList<>();
					m_periodStart = nowInNS;
					m_snapshotMetrics = currentMetrics(m_snapshotMetrics);
				} else {
					m_currentThreadData.add((MetricDataInstance)msg);
					m_numDataInstances.increment();
				}
			}
			if (!m_runningSnapshot) {
				Iterator<MetricDataInstance> iter = m_currentThreadData.iterator();
				while(iter.hasNext()) {
					MetricDataInstance inst = iter.next();
					if (!inst.ownerStillExists()) {
						m_temp.add(inst);
						iter.remove();
						m_numShutdownThreads.increment();
					}
				}
				if (m_temp.size() != 0) {
					m_snapshotMetrics = currentMetrics(m_snapshotMetrics);
					ensureSnapshotData();
					for(MetricDataInstance inst : m_temp) {
						for (int i = 0; i < m_snapshotMetrics.size(); i++) {
							inst.processData(m_snapshotData, m_snapshotMetrics.get(i));
						}
					}
					m_temp.clear();
				}
				return;
			}
			ensureSnapshotData();
			boolean needToTryAgain = false;
			Iterator<MetricDataInstance> iter = m_snapshotThreadInstances.iterator();
			while(iter.hasNext()) {
				MetricDataInstance inst = iter.next();
				if (!inst.markOld()) {
					needToTryAgain = true;
					continue;
				}
				iter.remove();
				for(int i=0; i<m_snapshotMetrics.size(); i++) {
					inst.processData(m_snapshotData, m_snapshotMetrics.get(i));
				}
			}
			if (needToTryAgain) {
				m_numNumSnapshotRetry.increment();
				requestMoreWork();
				return;
			}
			// We are done
			m_snapshotConsumer.apply("snapshot-start-ms", m_snapshotStart / 1000l / 1000l);
			long snapshotDurationMS = m_snapshotDuration / 1000l / 1000l;
			m_snapshotConsumer.apply("snapshot-duration-ms", snapshotDurationMS);
			for(int i=0; i<m_snapshotMetrics.size(); i++) {
				MetricBase m = m_snapshotMetrics.get(i);
				try {
					m.report(m_snapshotData, snapshotDurationMS, m_snapshotConsumer);
				} catch(Exception ex) {
					LogManager.getLogger(SnapshotWorker.class).error("Unhandled exception in report", ex);
				}
			}
			try {
				m_snapshotConsumer.done();
			} catch(Exception ex) {
				LogManager.getLogger(SnapshotWorker.class).error("Unhandled exception in consumer.done()", ex);
			}
/*
				We need to write the name and processed data somewhere
				DurationRate metric:
					min dur
					max dur
					avg dur
					Rate: num/minute	// in case it is less than 1 per second
					count
				CounterMetric:
					num
				TotalCounterMetric:
					num
 */
			m_snapshotDuration = 0;
			m_snapshotThreadInstances = null;
			for(int i=0; i<m_snapshotMetrics.size(); i++) {
				m_snapshotMetrics.get(i).resetDataForNextSnapshot(m_snapshotData);
			}
			m_snapshotMetrics = null;
			m_runningSnapshot = false;
		}

		private void ensureSnapshotData() {
			if (m_snapshotData == null) {
				m_snapshotData = new long[m_dataIndex];
				return;
			}
			if (m_snapshotData.length >= m_dataIndex) {
				return;
			}
			long[] newSD = new long[m_dataIndex];
			System.arraycopy(m_snapshotData, 0, newSD, 0, m_snapshotData.length);
			m_snapshotData = newSD;
		}
	}

	private static final class SnapshotTimer implements Runnable {

		@Override
		public void run() {
			// If the start/stop ref was released, this value will be 1... just abort
			if (retainTimedSnapshotRef() == 1) {
				revertTimedSnapshotRef();
				return;
			}

			// Our ref stays active until the writer finishes
			snapshot(new SnapshotFileWriter());
		}
	}

	private static final class SnapshotFileWriter extends Thread implements ISnapshotConsumer {
		private long m_snapshotStartMS;
		private char[] m_scratch = new char[ByteBufUtils.MAX_LONG_STRING];
		private ByteBuf m_outputBuf = Application.allocateIOBuffer();

		SnapshotFileWriter() {
			super("SnapshotFileWriter");
		}

		@Override
		public void apply(String metricKey, long value) {
			if (metricKey.equals("snapshot-start-ms")) {
				m_snapshotStartMS = value;
			}
			m_outputBuf.writeCharSequence(metricKey, StandardCharsets.US_ASCII);
			m_outputBuf.writeByte('\t');
			m_scratch = ByteBufUtils.writeString(m_outputBuf, value, m_scratch);
			m_outputBuf.writeByte('\r');
			m_outputBuf.writeByte('\n');
		}

		@Override
		public void done() {
			// Start the dedicated writer thread to keep our worker threads from being blocked
			start();
		}

		@Override
		public void run() {
			try {
				run0();
			} finally {
				releaseTimedSnapshotRef(false);
				m_outputBuf.release();
				m_outputBuf = null;
			}
		}

		private void run0() {
			RandomAccessFile m_out;
			File m_tmp = new File(METRICS_PATH + "/snapshot.tmp");
			m_tmp.delete();
			try {
				m_out = new RandomAccessFile(m_tmp, "rw");
			} catch(Exception ex) {
				LogManager.getLogger(SnapshotFileWriter.class).error("Unhandled exception creating {}", m_tmp.getAbsolutePath(), ex);
				return;
			}
			try {
				m_out.getChannel().write(m_outputBuf.nioBuffer());
				m_out.getChannel().force(true);
			} catch (Exception ex) {
				LogManager.getLogger(SnapshotFileWriter.class).error("Exception writing and flushing {}", m_tmp.getAbsolutePath(), ex);
				try {
					m_out.close();
				} catch (IOException e) {
				}
				m_tmp.delete();
				return;
			}
			try {
				m_out.close();
			} catch (IOException ex) {
				LogManager.getLogger(SnapshotFileWriter.class).error("Exception closing {}", m_tmp.getAbsolutePath(), ex);
				return;
			}
			File metricsFile = new File(METRICS_PATH + "/" + m_snapshotStartMS + ".metrics");
			if (!m_tmp.renameTo(metricsFile)) {
				LogManager.getLogger(SnapshotFileWriter.class).error("Failed renaming '{}' to '{}'", m_tmp.getAbsolutePath(), metricsFile.getAbsolutePath());
				m_tmp.delete();
			}
		}
	}
}
