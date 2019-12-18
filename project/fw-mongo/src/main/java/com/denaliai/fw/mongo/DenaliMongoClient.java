package com.denaliai.fw.mongo;

import com.denaliai.fw.Application;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.connection.netty.NettyStreamFactoryFactory;
import com.mongodb.event.*;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class DenaliMongoClient {
	private static final Logger LOG = LogManager.getLogger(DenaliMongoClient.class);
	private final List<ServerAddress> m_clusterHosts;
	private final IMongoClientConnect m_connectHandler;
	private final IMongoClientDisconnect m_disconnectHandler;
	private MongoClient m_mongoClient;

	private DenaliMongoClient(DenaliMongoClientBuilder builder) {
		m_clusterHosts = builder.m_clusterHosts;
		m_connectHandler = builder.m_connectHandler;
		m_disconnectHandler = builder.m_disconnectHandler;
	}

	public static DenaliMongoClientBuilder builder(List<MongoServerAddress> clusterHosts) {
		return new DenaliMongoClientBuilder(clusterHosts);
	}

	public synchronized MongoClient start() {
		if (m_mongoClient != null) {
			throw new IllegalStateException("DenaliMongoClient is already started");
		}
		MongoClientSettings settings = MongoClientSettings.builder()
			.streamFactoryFactory(NettyStreamFactoryFactory.builder().allocator(Application.allocator()).eventLoopGroup(Application.getIOPool()).build())
//			.applyToServerSettings((serverSettings) -> {
//				serverSettings.addServerListener(new ServerListener() {
//					@Override
//					public void serverOpening(ServerOpeningEvent serverOpeningEvent) {
//						if (LOG.isDebugEnabled()) {
//							LOG.debug("Mongo server opening: {}", serverOpeningEvent);
//						}
//					}
//
//					@Override
//					public void serverClosed(ServerClosedEvent serverClosedEvent) {
//						if (LOG.isDebugEnabled()) {
//							LOG.debug("Mongo server closed: {}", serverClosedEvent);
//						}
//					}
//
//					@Override
//					public void serverDescriptionChanged(ServerDescriptionChangedEvent serverDescriptionChangedEvent) {
//						if (LOG.isTraceEnabled()) {
//							LOG.trace("Mongo server description changed: {}", serverDescriptionChangedEvent);
//						}
//					}
//				});
//				serverSettings.addServerMonitorListener(new ServerMonitorListener() {
//					@Override
//					public void serverHearbeatStarted(ServerHeartbeatStartedEvent serverHeartbeatStartedEvent) {
//						if (LOG.isDebugEnabled()) {
//							LOG.debug("Mongo server heartbeat started: {}", serverHeartbeatStartedEvent);
//						}
//					}
//
//					@Override
//					public void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent serverHeartbeatSucceededEvent) {
//						if (LOG.isDebugEnabled()) {
//							LOG.debug("Mongo server heartbeat succeeded: {}", serverHeartbeatSucceededEvent);
//						}
//						try {
//							m_connectHandler.onConnect();
//						} catch(Exception ex) {
//							LOG.warn("Uncaught exception in m_connectHandler callback", ex);
//						}
//					}
//
//					@Override
//					public void serverHeartbeatFailed(ServerHeartbeatFailedEvent serverHeartbeatFailedEvent) {
//						LOG.info("Mongo server heartbeat failed: {}", serverHeartbeatFailedEvent);
//						try {
//							m_disconnectHandler.onDisconnect();
//						} catch(Exception ex) {
//							LOG.warn("Uncaught exception in m_disconnectHandler callback", ex);
//						}
//					}
//				});
//			})
			.applyToClusterSettings((clusterSettings) -> {
				clusterSettings.hosts(m_clusterHosts);
//				clusterSettings.addClusterListener(new ClusterListener() {
//					@Override
//					public void clusterOpening(ClusterOpeningEvent clusterOpeningEvent) {
//						if (LOG.isDebugEnabled()) {
//							LOG.debug("Mongo cluster opening: {}", clusterOpeningEvent);
//						}
//					}
//
//					@Override
//					public void clusterClosed(ClusterClosedEvent clusterClosedEvent) {
//						if (LOG.isDebugEnabled()) {
//							LOG.debug("Mongo cluster closed: {}", clusterClosedEvent);
//						}
//					}
//
//					@Override
//					public void clusterDescriptionChanged(ClusterDescriptionChangedEvent clusterDescriptionChangedEvent) {
//						if (LOG.isDebugEnabled()) {
//							LOG.debug("Mongo cluster description changed: {}", clusterDescriptionChangedEvent);
//						}
//					}
//				});
			})
			.build();
		m_mongoClient = MongoClients.create(settings);
		return m_mongoClient;
	}

//	public synchronized void stop() {
//		if (m_mongoClient != null) {
//			m_mongoClient.close();
//			m_mongoClient = null;
//		}
//	}

	public static class MongoServerAddress {
		final String hostName;
		final int port;

		public MongoServerAddress(String hostName) {
			this(hostName, 27017);
		}

		public MongoServerAddress(String hostName, int port) {
			this.hostName = hostName;
			this.port = port;
		}
	}

	public static class DenaliMongoClientBuilder {
		private final List<ServerAddress> m_clusterHosts;
		private IMongoClientConnect m_connectHandler = new StubHandler();
		private IMongoClientDisconnect m_disconnectHandler = (IMongoClientDisconnect)m_connectHandler;

		private DenaliMongoClientBuilder(List<MongoServerAddress> clusterHosts) {
			m_clusterHosts = new ArrayList<>();
			for(MongoServerAddress addr : clusterHosts) {
				m_clusterHosts.add(new ServerAddress(addr.hostName, addr.port));
			}
		}
//
//		public DenaliMongoClientBuilder onConnect(IMongoClientConnect handler) {
//			m_connectHandler = handler;
//			return this;
//		}
//
//		public DenaliMongoClientBuilder onDisconnect(IMongoClientDisconnect handler) {
//			m_disconnectHandler = handler;
//			return this;
//		}

		public DenaliMongoClient build() {
			return new DenaliMongoClient(this);
		}

	}

	public interface IMongoClientConnect {
		void onConnect();
	}
	public interface IMongoClientDisconnect {
		void onDisconnect();
	}

	private static class StubHandler implements IMongoClientConnect, IMongoClientDisconnect {

		@Override
		public void onConnect() {
			LOG.info("Connected to MongoDB");
		}

		@Override
		public void onDisconnect() {
			LOG.info("Disconnected from MongoDB");
		}
	}
}
