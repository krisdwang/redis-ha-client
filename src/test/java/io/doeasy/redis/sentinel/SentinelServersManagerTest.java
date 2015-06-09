package io.doeasy.redis.sentinel;

import io.doeasy.redis.client.sentinel.SentinelServersManager;
import io.doeasy.redis.client.sentinel.web.WebListener;
import io.doeasy.redis.config.HostConfiguration;

import java.util.ArrayList;
import java.util.List;

public class SentinelServersManagerTest {
	
	public static void main(String[] args) throws Exception{
		List<HostConfiguration> hosts = new ArrayList<HostConfiguration>();
		HostConfiguration hostConfiguration1 = new HostConfiguration();
		hostConfiguration1.setDatabase(0);
		hostConfiguration1.setHost("localhost");
		hostConfiguration1.setPort(26379);
		hostConfiguration1.setTimeout(1000);

		HostConfiguration hostConfiguration2 = new HostConfiguration();
		hostConfiguration2.setDatabase(0);
		hostConfiguration2.setHost("localhost");
		hostConfiguration2.setPort(26380);
		hostConfiguration2.setTimeout(1000);
		hosts.add(hostConfiguration1);
		hosts.add(hostConfiguration2);

		SentinelServersManager checker = new SentinelServersManager(hosts, 2000);
		checker.start();
		
		WebListener webListener = new WebListener(checker);
		webListener.start();
	}

}
