package io.doeasy.redis.client.sentinel;

import io.doeasy.redis.ClientFactory;
import io.doeasy.redis.config.HostConfiguration;

/**
 * 
 * @author kriswang
 *
 */
public class SentinelClientFactory implements ClientFactory<SentinelClient>{
	//singleton instance
	public static final SentinelClientFactory INSTANCE = new SentinelClientFactory();

	public SentinelClient create(final HostConfiguration sentinelConfigration) {
		return new SentinelClient(sentinelConfigration.getHost(),
				sentinelConfigration.getPort(),
				sentinelConfigration.getTimeout(),
				sentinelConfigration.getDatabase());
	}
}
