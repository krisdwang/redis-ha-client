package io.doeasy.redis.client;

import redis.clients.jedis.BinaryJedisCommands;

/**
 * 
 * @author kriswang
 *
 */
public interface RedisClient extends BinaryJedisCommands, RedisOperations{
	
}
