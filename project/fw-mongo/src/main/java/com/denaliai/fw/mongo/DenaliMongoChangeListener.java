package com.denaliai.fw.mongo;

import com.denaliai.fw.Application;
import com.denaliai.fw.utility.ByteBufUtils;
import com.denaliai.fw.utility.concurrent.PerpetualWork;
import com.denaliai.fw.utility.io.FileUtils;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.reactivestreams.client.ChangeStreamPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;

public class DenaliMongoChangeListener {
	private static final Logger LOG = LogManager.getLogger(DenaliMongoChangeListener.class);

	private final Queue<Object> m_docQueue = PlatformDependent.newSpscQueue();
	private final int m_batchSize;
	private final IChangeListener m_listener;
	private final File m_lastDocumentFile;
	private final File m_lastDocumentFileTmp;
	private final File m_lastDocumentFileOld;
	private final boolean m_useLastChangeForInit;
	private final Worker m_worker = new Worker();
	private final SubscriberWrapper m_wrapper = new SubscriberWrapper();
	private ChangeStreamPublisher<Document> m_runningPub;
	private volatile boolean m_cancel;

	public DenaliMongoChangeListener(int batchSize, IChangeListener listener, String lastDocumentFilename, boolean useLastChangeForInit) {
		m_batchSize = batchSize;
		m_listener = listener;
		m_lastDocumentFile = new File(lastDocumentFilename);
		m_lastDocumentFileTmp = new File(m_lastDocumentFile.getAbsolutePath() + ".tmp");
		m_lastDocumentFileOld = new File(m_lastDocumentFile.getAbsolutePath() + ".old");
		m_useLastChangeForInit = useLastChangeForInit;
	}

	public void cancel() {
		m_cancel = true;
		m_wrapper.cancel();
	}

	public Future<Void> writeResumeToken(String resumeTokenJson) {
		Promise<Void> writeDone = Application.newPromise();
		new TokenFileWriter(resumeTokenJson, writeDone).start();
		return writeDone;
	}

	public boolean writeResumeTokenSync(String resumeTokenJson) {
		Promise<Void> writeDone = Application.newPromise();
		new TokenFileWriter(resumeTokenJson, writeDone).run();
		return writeDone.isSuccess();
	}

	public void start(MongoClient mongoClient, MongoCollection<BsonDocument> collectionToWatch) {
		start(mongoClient, collectionToWatch, null);
	}

	public void start(MongoClient mongoClient, MongoCollection<BsonDocument> collectionToWatch, List<? extends Bson> pipeline) {
		start(mongoClient, collectionToWatch, pipeline);
	}

	public void start(MongoClient mongoClient, MongoCollection<BsonDocument> collectionToWatch, List<? extends Bson> pipeline, Object resumeToken) {
		final ChangeStreamPublisher<Document> pub;
		if (pipeline != null) {
			pub = collectionToWatch.watch(pipeline);
		} else {
			pub = collectionToWatch.watch();
		}

		if (resumeToken != null) {
			LOG.info("Starting change stream using supplied resume token");
			m_runningPub = pub.startAfter( BsonDocument.parse(resumeToken.toString()) );
			m_runningPub.subscribe(m_wrapper);
			return;
		}

		if ((m_lastDocumentFile.exists())) {
			String lastDocJson;
			try {
				lastDocJson = FileUtils.readFile(m_lastDocumentFile.getAbsolutePath(), StandardCharsets.UTF_8);
			} catch(IOException ex) {
				lastDocJson = null;
			}
			if (lastDocJson != null) {
				BsonDocument doc = BsonDocument.parse(lastDocJson);
				LOG.info("Starting change stream using resume token from file '{}'", m_lastDocumentFile);
				m_runningPub = pub.startAfter(doc);
				m_runningPub.subscribe(m_wrapper);
				return;
			}
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
			MongoCollection<BsonDocument> oplog = mongoClient.getDatabase("local").getCollection("oplog.rs", BsonDocument.class);
			oplog.find().first().subscribe(new Subscriber<BsonDocument>() {
				@Override
				public void onSubscribe(Subscription s) {
					LOG.debug("Find first change stream entry - onSubscribe");
					s.request(1);
				}

				@Override
				public void onNext(BsonDocument bsonDocument) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Find first oplog document: {}", bsonDocument.toJson());
					}

					BsonTimestamp startOfOpLog = bsonDocument.getTimestamp("ts");
					LOG.info("Using start timestamp: {}", startOfOpLog);
					// Start at the time just after the first oplog entry.  For some reason, we cannot start AT the first entry
					m_runningPub = pub.startAtOperationTime(new BsonTimestamp(startOfOpLog.getValue() + 1));
					m_runningPub.subscribe(m_wrapper);
				}

				@Override
				public void onError(Throwable t) {
					LOG.error("Could not get first oplog entry: {}", t);
					try {
						m_listener.onError(t);
					} catch(Exception ex) {
						LOG.warn("Unhandled exception in subscriber", ex);
					}
				}

				@Override
				public void onComplete() {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Find first change stream entry - onComplete");
					}
				}
			});
		}

	}

	private class SubscriberWrapper implements Subscriber<ChangeStreamDocument<Document>> {
		private Subscription m_subscription;
		private boolean m_cancel;
		private long m_expectedNum = 0;

		public synchronized void cancel() {
			LOG.debug("DenaliMongoChangeListener.cancel()");
			m_cancel = true;
			if (m_subscription != null) {
				m_subscription.cancel();
			}
		}

		@Override
		public synchronized void onSubscribe(Subscription s) {
			if (m_cancel) {
				LOG.debug("DenaliMongoChangeListener.onSubscribe() cancelling subscription");
				s.cancel();
				return;
			}
			LOG.debug("DenaliMongoChangeListener.onSubscribe() requesting subscription data");
			m_subscription = s;
			//m_expectedNum += m_batchSize;
			s.request(m_expectedNum = Long.MAX_VALUE);
		}

		@Override
		public void onNext(ChangeStreamDocument<Document> document) {
			if (m_cancel) {
				LOG.debug("DenaliMongoChangeListener.onNext() - cancelled");
				return;
			}
			m_expectedNum--;
			LOG.debug("DenaliMongoChangeListener.onNext(), expecting {} more", m_expectedNum);
			try {
				m_docQueue.add(document);
			} catch(Throwable t) {
				LOG.fatal("DenaliMongoChangeListener.onNext()", t);
			}
			m_worker.requestMoreWork();
			// For some reason onComplete isn't being called, so we will do the request here too
			if (m_expectedNum == 0) {
				m_expectedNum = m_batchSize;
				m_subscription.request(m_batchSize);
			}
		}

		@Override
		public void onError(Throwable t) {
			if (m_cancel) {
				LOG.debug("DenaliMongoChangeListener.onError() - cancelled", t);
				return;
			}
			LOG.info("DenaliMongoChangeListener.onError()", t);
			m_docQueue.add(t);
			m_worker.requestMoreWork();
		}

		@Override
		public synchronized void onComplete() {
			if (m_cancel) {
				LOG.debug("DenaliMongoChangeListener.onComplete() - cancelled");
				return;
			}
			m_subscription = null;
			m_docQueue.add(new RuntimeException("Changestream ended"));
			m_worker.requestMoreWork();
		}
	}

	private final class Worker extends PerpetualWork implements IChangeContext {
		private ChangeStreamDocument<Document> m_currentDoc;

		@Override
		protected void _doWork() {
			try {
				work();
			} catch(Throwable t) {
				LOG.error("Uncaught exception", t);
			}
		}

		private void work() {
			if (m_currentDoc != null) {
				return;
			}
			while(true) {
				Object o = m_docQueue.poll();
				if (o == null) {
					return;
				}
				if (m_cancel) {
					return;
				}
				if (o instanceof Throwable) {
					try {
						m_listener.onError((Throwable)o);
					} catch(Exception ex) {
						LOG.warn("Unhandled exception in subscriber", ex);
					}
					return;
				}
				m_currentDoc = (ChangeStreamDocument<Document>)o;
				try {
					m_listener.onChange(m_currentDoc, this);
				} catch(Exception ex) {
					LOG.warn("Unhandled exception in subscriber", ex);
				}
			}
		}

		@Override
		public void changeProcessed() {

			try {
				final String lastDocJson = m_currentDoc.getResumeToken().toJson();
				new TokenFileWriter(lastDocJson, Application.newPromise()).run();
			} catch(Exception ex) {
				LOG.error("", ex);
			}
			m_currentDoc = null;
			requestMoreWork();
		}

		@Override
		public void next() {
			m_currentDoc = null;
			requestMoreWork();
		}

		@Override
		public void abort() {
			m_currentDoc = null;
			cancel();
		}
	}

	public interface IChangeContext {
		/**
		 * Mutually exclusive with next() and abort()
		 */
		void changeProcessed();

		/**
		 * Mutually exclusive with changeProcessed() and abort()
		 */
		void next();

		/**
		 * Mutually exclusive with changeProcessed() and next()
		 */
		void abort();
	}

	public interface IChangeListener {
		void onChange(ChangeStreamDocument<Document> document, IChangeContext context);
		void onError(Throwable t);
	}


	private final class TokenFileWriter extends Thread {
		private final Promise<Void> m_completed;
		private ByteBuf m_buffer = Application.allocateIOBuffer();

		TokenFileWriter(String json, Promise<Void> completed) {
			super("TokenFileWriter");
			m_completed = completed;
			m_buffer.writeBytes(json.getBytes());
		}

		@Override
		public void run() {
			try {
				run0();
			} finally {
				m_buffer.release();
				m_buffer = null;
			}
		}

		public void run0() {
			RandomAccessFile m_out;
			m_lastDocumentFileTmp.delete();
			try {
				m_out = new RandomAccessFile(m_lastDocumentFileTmp, "rw");
			} catch(Exception ex) {
				LogManager.getLogger(TokenFileWriter.class).error("Unhandled exception creating {}", m_lastDocumentFileTmp.getAbsolutePath(), ex);
				m_completed.setFailure(ex);
				return;
			}
			try {
				m_out.getChannel().write(m_buffer.nioBuffer());
				m_out.getChannel().force(true);
			} catch (Exception ex) {
				LogManager.getLogger(TokenFileWriter.class).error("Exception writing and flushing {}", m_lastDocumentFileTmp.getAbsolutePath(), ex);
				try {
					m_out.close();
				} catch (IOException e) {
				}
				m_lastDocumentFileTmp.delete();
				m_completed.setFailure(ex);
				return;
			}
			try {
				m_out.close();
			} catch (IOException ex) {
				LogManager.getLogger(TokenFileWriter.class).error("Exception closing {}", m_lastDocumentFileTmp.getAbsolutePath(), ex);
				m_completed.setFailure(ex);
				return;
			}
			m_lastDocumentFileOld.delete();
			if (m_lastDocumentFile.exists() && !m_lastDocumentFile.renameTo(m_lastDocumentFileOld)) {
				LogManager.getLogger(TokenFileWriter.class).error("Failed renaming '{}' to '{}'", m_lastDocumentFile.getAbsolutePath(), m_lastDocumentFileOld.getAbsolutePath());
				m_completed.setFailure(new RuntimeException());
				return;
			}
			if (!m_lastDocumentFileTmp.renameTo(m_lastDocumentFile)) {
				LogManager.getLogger(TokenFileWriter.class).error("Failed renaming '{}' to '{}'", m_lastDocumentFileTmp.getAbsolutePath(), m_lastDocumentFile.getAbsolutePath());
				m_completed.setFailure(new RuntimeException());
				return;
			}
			m_completed.setSuccess(null);
		}
	}
}
