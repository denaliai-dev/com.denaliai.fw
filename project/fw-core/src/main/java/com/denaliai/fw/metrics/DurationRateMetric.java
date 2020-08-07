package com.denaliai.fw.metrics;

public class DurationRateMetric extends MetricBase {
	private final String m_consumerKey_Count;
	private final String m_consumerKey_Rate;
	private final String m_consumerKey_AvgDur;
	private final String m_consumerKey_MinDur;
	private final String m_consumerKey_MaxDur;
	final int initIndex;
	final int indexOfCount;
	final int indexOfTotal;
	final int indexOfMin;
	final int indexOfMax;

	DurationRateMetric(String name, int initIndex, int indexOfCount, int indexOfTotal, int indexOfMin, int indexOfMax) {
		super(name);
		this.initIndex = initIndex;
		this.indexOfCount = indexOfCount;
		this.indexOfTotal = indexOfTotal;
		this.indexOfMin = indexOfMin;
		this.indexOfMax = indexOfMax;
		if (initIndex > indexOfMax || indexOfCount > indexOfMax || indexOfTotal > indexOfMax || indexOfMin > indexOfMax) {
			throw new IllegalArgumentException("indexOfMax must be the largest index passed in");
		}
		m_consumerKey_Count = name + ".count";
		m_consumerKey_Rate = name + ".rate-per-minute";
		m_consumerKey_AvgDur = name + ".avg-duration-ms";
		m_consumerKey_MinDur = name + ".min-duration-ms";
		m_consumerKey_MaxDur = name + ".max-duration-ms";
	}

	@Override
	void ensureInitialized(long[] data) {
		if (indexOfMax >= data.length) {
			return;
		}
		if (data[initIndex] == 0) {
			resetDataForNextSnapshot(data);
		}
	}

	@Override
	void addDataToSnapshot(long[] snapshotData, long[] instanceData) {
		if (indexOfMax >= instanceData.length) {
			return;
		}
		if (instanceData[indexOfMin] < snapshotData[indexOfMin]) {
			snapshotData[indexOfMin] = instanceData[indexOfMin];
		}
		if (instanceData[indexOfMax] > snapshotData[indexOfMax]) {
			snapshotData[indexOfMax] = instanceData[indexOfMax];
		}
		snapshotData[indexOfTotal] += instanceData[indexOfTotal];
		snapshotData[indexOfCount] += instanceData[indexOfCount];
	}

	@Override
	void resetDataForNextSnapshot(long[] snapshotData) {
		snapshotData[initIndex] = 1;
		snapshotData[indexOfMin] = Long.MAX_VALUE;
		snapshotData[indexOfMax] = Long.MIN_VALUE;
		snapshotData[indexOfTotal] = 0;
		snapshotData[indexOfCount] = 0;
	}

	public void record(MetricsEngine.IMetricTimer timer) {
		MetricsEngine.record(this, timer);
	}

	@Override
	void report(long[] snapshotData, long snapshotDurationMS, MetricsEngine.ISnapshotConsumer consumer) {
		consumer.apply(m_consumerKey_Count, snapshotData[indexOfCount]);
		consumer.apply(m_consumerKey_Rate, MetricsEngine.toRatePerMinute(snapshotDurationMS, snapshotData[indexOfCount]));
		if (snapshotData[indexOfCount] == 0) {
			consumer.apply(m_consumerKey_AvgDur, 0l);
			consumer.apply(m_consumerKey_MinDur, 0l);
			consumer.apply(m_consumerKey_MaxDur, 0l);
		} else {
			consumer.apply(m_consumerKey_AvgDur, snapshotData[indexOfTotal] / snapshotData[indexOfCount] / 1000l / 1000l);
			consumer.apply(m_consumerKey_MinDur, snapshotData[indexOfMin] / 1000l / 1000l);
			consumer.apply(m_consumerKey_MaxDur, snapshotData[indexOfMax] / 1000l / 1000l);
		}
	}
}
