package io.doeasy.redis.client.sentinel;

import io.doeasy.redis.client.GenericRedisClient;
import io.doeasy.redis.model.RedisNodeInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.log4j.Log4j;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import redis.clients.jedis.HostAndPort;

import com.google.common.base.CaseFormat;

/**
 * http://redis.io/topics/sentinel
 * @author kriswang
 *
 */
@Log4j
public class SentinelClient extends GenericRedisClient implements SentinelOperations {
	
	public SentinelClient(String host, int port, int timeout, int database) {
		super(host, port, timeout, database);
		log.info("SentinelClient [" + this + "] is created.");
	}

	@Override
	public String ping() {
		return super.ping();
	}

	@Override
	public List<RedisNodeInfo> masters() {
		List<RedisNodeInfo> masters = null;
		
		List<Map<String, String>> masterList = this.sentinelMasters();

		if (!CollectionUtils.isEmpty(masterList)) {
			masters = new ArrayList<RedisNodeInfo>();
			for (Map<String, String> master : masterList) {
				RedisNodeInfo sentinelMasterNodeInfo = new RedisNodeInfo();
				copyRedisNodeInfoProperties(sentinelMasterNodeInfo, master);
				masters.add(sentinelMasterNodeInfo);
			}
		}

		return masters;
	}

	@Override
	public RedisNodeInfo master(String masterName) {
		List<RedisNodeInfo> masters = this.masters();
		if(!CollectionUtils.isEmpty(masters)) {
			for(RedisNodeInfo master : masters) {
				if(master.getName().equals(masterName)){
					return master;
				}
			}
		}
		return null;
	}

	@Override
	public List<RedisNodeInfo> slaves(String masterName) {
		List<RedisNodeInfo> slaves = null;
		
		List<Map<String, String>> slavesList = this.sentinelSlaves(masterName);
		if (!CollectionUtils.isEmpty(slavesList)) {
			slaves = new ArrayList<RedisNodeInfo>();
			for (Map<String, String> master : slavesList) {
				RedisNodeInfo sentinelSlaveNodeInfo = new RedisNodeInfo();
				copyRedisNodeInfoProperties(sentinelSlaveNodeInfo, master);
				slaves.add(sentinelSlaveNodeInfo);
			}
		}
		return slaves;
	}

	@Override
	public HostAndPort getMasterAddrByName(String masterName) {
		List<String> list = this.sentinelGetMasterAddrByName(masterName);
		HostAndPort hp = new HostAndPort(list.get(0), Integer.parseInt(list.get(1)));
		return hp;
	}

	@Override
	public String failover(String masterName) {
		return this.sentinelFailover(masterName);
	}

	@Override
	public Long reset(String pattern) {
		return this.sentinelReset(pattern);
	}

	@Override
	public String monitor(String masterName, String ip, int port, int quorum) {
		return this.sentinelMonitor(masterName, ip, port, quorum);
	}

	@Override
	public String remove(String masterName) {
		return this.sentinelRemove(masterName);
	}

	@Override
	public String set(String masterName, Map<String, String> parameterMap) {
		return this.sentinelSet(masterName, parameterMap);
	}
	

	/**
	 * 
	 * @param sentinelMasterNodeInfo
	 * @param master
	 */
	private void copyRedisNodeInfoProperties(RedisNodeInfo sentinelMasterNodeInfo, Map<String,String> master){
		try {
			Method[] methods = RedisNodeInfo.class.getMethods();
			for(Method method : methods) {
				String methodName = method.getName();
				if(methodName.startsWith("set")) {
					String value = master.get(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, methodName.substring("set".length())));
					if(log.isDebugEnabled()) {
						log.debug(String.format("method named %s, value is %s", methodName, value));
					}
					if(StringUtils.isEmpty(value)) {
						continue;
					}
					Class<?> clazz = method.getParameterTypes()[0];//because set method just has one parameter.
					method.invoke(sentinelMasterNodeInfo, clazz.getConstructor(String.class).newInstance(value));
				}
			}
		}catch(Exception e) {
			log.error(e.getMessage());
			throw new RuntimeException(e);
		}
	}
	
	public String toString() {
		return super.toString();
	}
}
 