package io.doeasy.redis.client.sentinel;

import io.doeasy.redis.model.RedisNodeInfo;

import java.util.List;
import java.util.Map;

import redis.clients.jedis.HostAndPort;

/**
 * redefine the redis.clients.jedis.SentinelCommands use OO design approach.
 * 
 * @author kriswang
 *
 */
public interface SentinelOperations {

	public String ping();

	public List<RedisNodeInfo> masters();

	public RedisNodeInfo master(String masterName);

	public List<RedisNodeInfo> slaves(String masterName);

	public HostAndPort getMasterAddrByName(String masterName);

	public String failover(String masterName);

	public Long reset(String pattern);

	public String monitor(String masterName, String ip, int port, int quorum);

	public String remove(String masterName);

	public String set(String masterName, Map<String, String> parameterMap);
}
