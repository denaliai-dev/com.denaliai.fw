package com.denaliai.fw.metrics;

public class MinMaxAvgValueMetric extends MetricBase {
	private final String m_consumerKey_Avg;
	private final String m_consumerKey_Min;
	private final String m_consumerKey_Max;
	final int initIndex;
	final int indexOfCount;
	final int indexOfTotal;
	final int indexOfMin;
	final int indexOfMax;

	MinMaxAvgValueMetric(String name, int initIndex, int indexOfCount, int indexOfTotal, int indexOfMin, int indexOfMax) {
		super(name);
		this.initIndex = initIndex;
		this.indexOfCount = indexOfCount;
		this.indexOfTotal = indexOfTotal;
		this.indexOfMin = indexOfMin;
		this.indexOfMax = indexOfMax;
		if (initIndex > indexOfMax || indexOfCount > indexOfMax || indexOfTotal > indexOfMax || indexOfMin > indexOfMax) {
			throw new IllegalArgumentException("indexOfMax must be the largest index passed in");
		}
		m_consumerKey_Avg = name + ".avg";
		m_consumerKey_Min = name + ".min";
		m_consumerKey_Max = name + ".max";
	}

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

	@Override
	void report(long[] snapshotData, long snapshotDurationMS, MetricsEngine.ISnapshotConsumer consumer) {
		if (snapshotData[indexOfCount] == 0) {
			consumer.apply(m_consumerKey_Avg, 0L);
			consumer.apply(m_consumerKey_Min, 0L);
			consumer.apply(m_consumerKey_Max, 0L);
		} else {
			consumer.apply(m_consumerKey_Avg, snapshotData[indexOfTotal] / snapshotData[indexOfCount]);
			consumer.apply(m_consumerKey_Min, snapshotData[indexOfMin]);
			consumer.apply(m_consumerKey_Max, snapshotData[indexOfMax]);
		}
	}
}
