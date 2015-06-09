package io.doeasy.redis;

import io.doeasy.redis.config.HostConfiguration;

/**
 * 
 * @author kriswang
 *
 */
public interface ClientFactory<T> {
	T create(HostConfiguration hostConfiguration);
}
