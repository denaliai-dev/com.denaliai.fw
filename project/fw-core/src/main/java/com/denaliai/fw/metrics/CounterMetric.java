package com.denaliai.fw.metrics;

public class CounterMetric extends SingleIndexMetricBase {
	private final String m_consumerKey_Count;

	CounterMetric(String name, int index) {
		super(name, index);
		m_consumerKey_Count = name + ".count";
	}

	public void increment() {
		MetricsEngine.increment(index);
	}

	public void add(long value) {
		MetricsEngine.increment(index);
	}

	@Override
	void report(long[] snapshotData, long snapshotDurationInNS, MetricsEngine.ISnapshotConsumer consumer) {
		consumer.apply(m_consumerKey_Count, snapshotData[index]);
	}
}
