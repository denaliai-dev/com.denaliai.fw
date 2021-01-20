package com.denaliai.fw.mongo;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.reactivestreams.client.ChangeStreamPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DenaliMongoChangeListener {
	private static final Logger LOG = LoggerFactory.getLogger(DenaliMongoChangeListener.class);

	private final int m_batchSize;
	private final IChangeListener m_listener;
	private final SubscriberWrapper m_wrapper = new SubscriberWrapper();
	private final boolean m_useLastChangeForInit;
	private final boolean m_includeFullDocument;
	private ChangeStreamPublisher<Document> m_runningPub;
	private volatile boolean m_cancel;

	public DenaliMongoChangeListener(int batchSize, IChangeListener listener, boolean useLastChangeForInit, boolean includeFullDocument) {
		m_batchSize = batchSize;
		m_listener = listener;
		m_useLastChangeForInit = useLastChangeForInit;
		m_includeFullDocument = includeFullDocument;
	}

	public void cancel() {
		m_cancel = true;
		m_wrapper.cancel();
	}

	public void start(MongoClient mongoClient, MongoCollection<Document> collectionToWatch, List<? extends Bson> pipeline, String resumeTokenJSON) {
		final ChangeStreamPublisher<Document> pub;
		if (pipeline != null) {
			if (m_includeFullDocument) {
				pub = collectionToWatch.watch(pipeline).batchSize(m_batchSize).fullDocument(FullDocument.DEFAULT);
			} else {
				pub = collectionToWatch.watch(pipeline).batchSize(m_batchSize);
			}
		} else {
			if (m_includeFullDocument) {
				pub = collectionToWatch.watch().batchSize(m_batchSize).fullDocument(FullDocument.DEFAULT);
			} else {
				pub = collectionToWatch.watch().batchSize(m_batchSize);
			}
		}

		if (resumeTokenJSON != null) {
			LOG.info("Starting change stream using supplied resume token: {}", resumeTokenJSON);
			m_runningPub = pub.startAfter( BsonDocument.parse(resumeTokenJSON) );
			m_runningPub.subscribe(m_wrapper);
			return;
		}

		if (m_useLastChangeForInit) {
			LOG.info("Find last change stream entry");
			MongoCollection<BsonDocument> oplog = mongoClient.getDatabase("local").getCollection("oplog.rs", BsonDocument.class);
			oplog.find().limit(1).sort(new BsonDocument("$natural", new BsonInt32(-1))).subscribe(new Subscriber<BsonDocument>() {
				@Override
				public void onSubscribe(Subscription s) {
					LOG.debug("Find last change stream entry - onSubscribe");
					s.request(1);
				}

				@Override
				public void onNext(BsonDocument bsonDocument) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Find last oplog document: {}", bsonDocument.toJson());
					}

					BsonTimestamp startOfOpLog = bsonDocument.getTimestamp("ts");
					LOG.info("Using start timestamp: {}", startOfOpLog);
					m_runningPub = pub.startAtOperationTime(startOfOpLog);
					m_runningPub.subscribe(m_wrapper);
				}

				@Override
				public void onError(Throwable t) {
					LOG.error("Could not get last oplog entry: {}", t);
					try {
						m_listener.onError(t);
					} catch(Exception ex) {
						LOG.warn("Unhandled exception in subscriber", ex);
					}
				}

				@Override
				public void onComplete() {
					LOG.debug("Find last change stream entry - onComplete");
				}
			});

		} else {
			LOG.info("Find first change stream entry");
			pub.subscribe(m_wrapper);
//
//			MongoCollection<BsonDocument> oplog = mongoClient.getDatabase("local").getCollection("oplog.rs", BsonDocument.class);
//			oplog.find().first().subscribe(new Subscriber<BsonDocument>() {
//				@Override
//				public void onSubscribe(Subscription s) {
//					LOG.debug("Find first change stream entry - onSubscribe");
//					s.request(1);
//				}
//
//				@Override
//				public void onNext(BsonDocument bsonDocument) {
//					if (LOG.isDebugEnabled()) {
//						LOG.debug("Find first oplog document: {}", bsonDocument.toJson());
//					}
//
//					BsonTimestamp startOfOpLog = bsonDocument.getTimestamp("ts");
//					LOG.info("Using start timestamp: {}", startOfOpLog);
//					// Start at the time just after the first oplog entry.  For some reason, we cannot start AT the first entry
//					m_runningPub = pub.startAtOperationTime(new BsonTimestamp(startOfOpLog.getValue() + 1));
//					m_runningPub.subscribe(m_wrapper);
//				}
//
//				@Override
//				public void onError(Throwable t) {
//					LOG.error("Could not get first oplog entry: {}", t);
//					try {
//						m_listener.onError(t);
//					} catch(Exception ex) {
//						LOG.warn("Unhandled exception in subscriber", ex);
//					}
//				}
//
//				@Override
//				public void onComplete() {
//					if (LOG.isDebugEnabled()) {
//						LOG.debug("Find first change stream entry - onComplete");
//					}
//				}
//			});
		}

	}

	private class SubscriberWrapper implements Subscriber<ChangeStreamDocument<Document>> {
		private Subscription m_subscription;
		private boolean m_cancel;
		private boolean m_closeCalled;
		private long m_expectedNum = 0;

		public synchronized void cancel() {
			LOG.debug("DenaliMongoChangeListener.cancel()");
			m_cancel = true;
			if (m_subscription != null) {
				m_subscription.cancel();
				m_subscription = null;
			}
			close0();
		}

		public void cancel0() {
			LOG.debug("DenaliMongoChangeListener.cancel0()");
			m_cancel = true;
			if (m_subscription != null) {
				m_subscription.cancel();
				m_subscription = null;
			}
		}

		private synchronized void close0() {
			if (m_closeCalled) {
				return;
			}
			m_cancel = true; // Set this if it isn't
			m_closeCalled = true;
			try {
				m_listener.onClosed();
			} catch(Throwable t) {
				LOG.error("Unhandled exception in client code", t);
			}

		}

		@Override
		public synchronized void onSubscribe(Subscription s) {
			if (m_cancel) {
				LOG.debug("DenaliMongoChangeListener.onSubscribe() cancelling subscription");
				s.cancel();
				close0();
				return;
			}
			LOG.debug("DenaliMongoChangeListener.onSubscribe() requesting subscription data");
			m_subscription = s;
			s.request(m_expectedNum = m_batchSize);
		}

		@Override
		public synchronized void onNext(ChangeStreamDocument<Document> document) {
			if (m_cancel) {
				LOG.debug("DenaliMongoChangeListener.onNext() - cancelled");
				return;
			}
			m_expectedNum--;
			LOG.debug("DenaliMongoChangeListener.onNext(), expecting {} more", m_expectedNum);
			try {
				m_listener.onChange(document);
			} catch(Throwable t) {
				try {
					m_listener.onError(t);
				} catch(Throwable t2) {
					LOG.error("Unhandled exception in client code", t2);
				}
				if (LOG.isDebugEnabled()) {
					LOG.debug("Document={}", document);
				}
			}
			// For some reason onComplete isn't being called, so we will do the request here too
			if (m_expectedNum == 0) {
				m_expectedNum = m_batchSize;
				m_subscription.request(m_batchSize);
			}
		}

		@Override
		public synchronized void onError(Throwable t) {
			if (m_cancel) {
				LOG.debug("DenaliMongoChangeListener.onError() - cancelled", t);
				close0();
				return;
			}
			LOG.info("DenaliMongoChangeListener.onError()", t);
			try {
				m_listener.onError(t);
			} catch(Throwable t2) {
				LOG.error("Unhandled exception in client code", t2);
			}
		}

		@Override
		public synchronized void onComplete() {
			if (m_cancel) {
				LOG.debug("DenaliMongoChangeListener.onComplete() - cancelled");
				close0();
				return;
			}
			LOG.info("DenaliMongoChangeListener.onComplete()");
			m_subscription = null;
			try {
				m_listener.onError(new RuntimeException("Changestream ended"));
			} catch(Throwable t) {
				LOG.error("Unhandled exception in client code", t);
			}
		}
	}

	public interface IChangeListener {
		void onChange(ChangeStreamDocument<Document> document);
		void onError(Throwable t);
		void onClosed();
	}

}
