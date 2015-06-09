package io.doeasy.redis.sentinel;

import io.doeasy.redis.client.sentinel.SentinelClient;
import io.doeasy.redis.client.sentinel.SentinelClientFactory;
import io.doeasy.redis.config.HostConfiguration;
import io.doeasy.redis.model.RedisNodeInfo;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Protocol;

public class SentinelClientTest {
	
	private SentinelClient sentinelClient;

	@Before
	public void setUp() throws Exception {
		HostConfiguration config = new HostConfiguration();
		config.setHost("localhost");
		config.setPort(26379);
		config.setTimeout(1000);
		config.setDatabase(Protocol.DEFAULT_DATABASE);
		
		sentinelClient = SentinelClientFactory.INSTANCE.create(config);
	}

	@After
	public void tearDown() throws Exception {
		sentinelClient.quit();
	}

	@Test
	public void testPing() {
		String response = this.sentinelClient.ping();
		org.junit.Assert.assertEquals("PONG", response);
	}

	@Test
	public void testMasters() {
		List<RedisNodeInfo> masters = this.sentinelClient.masters();
		org.junit.Assert.assertEquals(1, masters.size());
		org.junit.Assert.assertEquals("mymaster", masters.get(0).getName());
		System.out.println(masters.get(0));
	}
	
	@Test
	public void testGetMasterAddrByName() {
		HostAndPort hp = this.sentinelClient.getMasterAddrByName("mymaster");
		org.junit.Assert.assertEquals("127.0.0.1", hp.getHost());
	}

}
