package io.doeasy.redis.client.sentinel.web;

import io.doeasy.redis.client.sentinel.SentinelServersManager;
import io.doeasy.redis.config.HostConfiguration;

import java.util.List;

/**
 * 
 * @author kriswang
 *
 */
public class RedisServerView {

	private SentinelServersManager manager;
	private String masterName;

	public RedisServerView(SentinelServersManager manager, String masterName) {
		this.manager = manager;
		this.masterName = masterName;
	}

	public HostConfiguration getMaster() {
		return this.manager.getMaster(this.masterName);
	}
	
	public List<HostConfiguration> getSlaves() {
		return this.manager.getSlaves(this.masterName);
	}
}
