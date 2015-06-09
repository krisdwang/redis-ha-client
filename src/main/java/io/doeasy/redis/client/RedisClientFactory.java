package io.doeasy.redis.client;

import io.doeasy.redis.ClientFactory;
import io.doeasy.redis.config.HostConfiguration;

public class RedisClientFactory implements ClientFactory<GenericRedisClient> {

	public static final RedisClientFactory INSTANCE = new RedisClientFactory();
	
	@Override
	public GenericRedisClient create(HostConfiguration hostConfiguration) {
		return new GenericRedisClient(hostConfiguration.getHost(),
				hostConfiguration.getPort(),
				hostConfiguration.getTimeout(),
				hostConfiguration.getDatabase());
	}

}
