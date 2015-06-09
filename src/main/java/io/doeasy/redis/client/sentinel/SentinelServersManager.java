package io.doeasy.redis.client.sentinel;

import io.doeasy.redis.client.sentinel.exception.SentinelServerNotAvailibleException;
import io.doeasy.redis.config.HostConfiguration;
import io.doeasy.redis.model.RedisNodeInfo;
import io.doeasy.redis.utils.CircularList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.log4j.Log4j;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import redis.clients.jedis.HostAndPort;

/**
 * Sentinel server heart beat checker, must provide at least sentinel server,
 * two or more sentinel servers will be recommended.
 * @author kriswang
 * 
 */
@Log4j
public class SentinelServersManager {

	private volatile boolean running;
	/**
	 * sentinel hosts configuration
	 */
	private final Collection<HostConfiguration> sentinelHosts;
	
	private final ScheduledThreadPoolExecutor threadPool;
	
	/**
	 * heart beat checking interval, must be set. (MILLISECONDS)
	 */
	private final int interval;
	
	private CircularList<SentinelClient> sentinelServers;

	@Getter
	private List<SentinelClient> availibleSentinelServers = new CopyOnWriteArrayList<SentinelClient>();

	@Getter
	private List<SentinelClient> unavailibleSentinelServers = new CopyOnWriteArrayList<SentinelClient>();

	public SentinelServersManager(Collection<HostConfiguration> sentinelServers, int interval) {
		Assert.notEmpty(sentinelServers, "Must provide at least sentinel server.");
		this.sentinelHosts = sentinelServers;
		this.threadPool = new ScheduledThreadPoolExecutor(this.sentinelHosts.size());
		this.interval = interval;

		for (final HostConfiguration sentinelServer : sentinelHosts) {
			SentinelClient client = SentinelClientFactory.INSTANCE.create(sentinelServer);
			availibleSentinelServers.add(client);
		}
	}

	public void start() {
		this.running = true;
		this.startHeartbeatCheck();
		//
		sentinelServers = new CircularList<SentinelClient>(availibleSentinelServers);
	}

	public void stop() {
		this.running = false;
		for (final SentinelClient sentinelServer : availibleSentinelServers) {
			sentinelServer.quit();
		}
		for (final SentinelClient sentinelServer : unavailibleSentinelServers) {
			sentinelServer.quit();
		}
		this.threadPool.shutdown();
	}
	
	public boolean healthy() {
		return this.sentinelServers.size() > 0;
	}
	
	public HostConfiguration getMaster(String masterName) {
		if(this.healthy()) {
			//get sentinel client by round robin
			SentinelClient sentinel = sentinelServers.next();
			HostAndPort hostAndPort = sentinel.getMasterAddrByName(masterName);
			return new HostConfiguration(hostAndPort.getHost(),hostAndPort.getPort());
		}
		throw new SentinelServerNotAvailibleException("All provided sentinels are not availible.");
	}
	
	public List<HostConfiguration> getSlaves(String masterName) {
		if(this.healthy()) {
			//get sentinel client by round robin
			SentinelClient sentinel = sentinelServers.next();
			List<RedisNodeInfo> slaves = sentinel.slaves(masterName);
			if(!CollectionUtils.isEmpty(slaves)) {
				List<HostConfiguration> result = new ArrayList<HostConfiguration>();
				for(RedisNodeInfo slave : slaves) {
					if(slave.getFlags().equals("slave")) {
						result.add(new HostConfiguration(slave.getIp(),slave.getPort()));
					}
				}
				return result;
			} 
			return null;
		}
		throw new SentinelServerNotAvailibleException("All provided sentinels are not availible.");
	}
	
	

	protected void startHeartbeatCheck() {
		this.threadPool.scheduleAtFixedRate(new Runnable() {
			public void run() {
				if (running) { //ugly, need to be refactor
					for (final SentinelClient sentinelServer : availibleSentinelServers) {
						try {
							if(log.isDebugEnabled()) {
								//log.debug("checking sentinel server [" + sentinelServer + "]...");
							}
							sentinelServer.ping();
						} catch (Exception e) {
							log.error("it seems the sentinel node [" + sentinelServer + "] is not availible, please check it.");
							unavailibleSentinelServers.add(sentinelServer);
							availibleSentinelServers.remove(sentinelServer);
						}
					}
					
					for (final SentinelClient sentinelServer : unavailibleSentinelServers) {
						try {
							if(log.isDebugEnabled()) {
								//log.debug("checking sentinel server [" + sentinelServer + "]...");
							}
							sentinelServer.ping();
							availibleSentinelServers.add(sentinelServer);
							unavailibleSentinelServers.remove(sentinelServer);
						} catch (Exception e) {
							log.error("it seems the sentinel node [" + sentinelServer + "] is not availible, please check it.");
						}
					}
				}
			}
		}, 0, interval, TimeUnit.MILLISECONDS);
	}
}
