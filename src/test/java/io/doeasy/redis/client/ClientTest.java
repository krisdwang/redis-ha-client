package io.doeasy.redis.client;

import io.doeasy.redis.client.sentinel.SentinelServersManager;
import io.doeasy.redis.client.sentinel.web.WebListener;
import io.doeasy.redis.config.HostConfiguration;
import io.doeasy.retry.spring.Policy;
import io.doeasy.retry.spring.Recovery;
import io.doeasy.retry.spring.SpringRetry;

import java.util.ArrayList;
import java.util.List;

import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * 
 * @author kriswang
 *
 */
public class ClientTest {

	private SentinelServersManager manager;

	private List<HostConfiguration> hosts;

	private Client client;
	
	private WebListener webListener;

	public ClientTest() throws Exception {

		hosts = new ArrayList<HostConfiguration>();
		HostConfiguration hostConfiguration1 = new HostConfiguration();
		hostConfiguration1.setDatabase(0);
		hostConfiguration1.setHost("10.128.17.8");
		hostConfiguration1.setPort(26379);
		hostConfiguration1.setTimeout(1000);
		hosts.add(hostConfiguration1);

		manager = new SentinelServersManager(hosts, 2000);
		manager.start();

		// start sentinel server web listener
		webListener = new WebListener(manager);
		webListener.start();

		client = new Client(RedisClientFactory.INSTANCE, manager, "mymaster");
	}

	public static void main(String[] args) throws Exception {
		
		ClientTest clientTest = new ClientTest();
		//clientTest.redisSet("t1", "v1");
		
	}

	@SpringRetry(policy = @Policy(attempts = 3, type = SimpleRetryPolicy.class), 
			     backoff = @io.doeasy.retry.spring.Backoff(period = 2000), 
			     recovery = { @Recovery(recover = RedisClientRetryRecoveryCallback.class) })
	public void redisSet(String key, String value) throws Exception {
		try {
			client.set(key.getBytes(), value.getBytes());
			Thread.sleep(5000);
		}finally {
			client.quit();
			webListener.stop();
			manager.stop();
		}
	}
}
