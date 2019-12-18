package com.denaliai.fw.metrics;

public class CounterAndRateMetric extends SingleIndexMetricBase {
	private final String m_consumerKey_Count;
	private final String m_consumerKey_Rate;

	CounterAndRateMetric(String name, int index) {
		super(name, index);
		m_consumerKey_Count = name + ".count";
		m_consumerKey_Rate = name + ".rate-per-minute";
	}

	public void increment() {
		MetricsEngine.increment(index);
	}

	@Override
	void report(long[] snapshotData, long snapshotDurationMS, MetricsEngine.ISnapshotConsumer consumer) {
		consumer.apply(m_consumerKey_Count, snapshotData[index]);
		consumer.apply(m_consumerKey_Rate, MetricsEngine.toRatePerMinute(snapshotDurationMS, snapshotData[index]));
	}
}
