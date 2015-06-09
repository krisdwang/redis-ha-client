package io.doeasy.redis.client.sentinel.web;

import io.doeasy.redis.client.sentinel.SentinelClient;
import io.doeasy.redis.client.sentinel.SentinelServersManager;
import io.doeasy.redis.config.HostConfiguration;

import java.util.List;

/**
 * 
 * @author kriswang
 *
 */
public class SentinelServerView {
	
	private SentinelServersManager manager;
	
	public SentinelServerView(SentinelServersManager manager) {
		this.manager = manager;
	}
	
	public List<SentinelClient> getAvailibleServers() {
		return manager.getAvailibleSentinelServers();
	}
	
	public List<SentinelClient> getUnAvailibleServers() {
		return manager.getUnavailibleSentinelServers();
	}
	
	public HostConfiguration getMaster() {
		return manager.getMaster("mymaster");
	}
}
