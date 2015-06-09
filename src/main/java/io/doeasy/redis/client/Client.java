package io.doeasy.redis.client;

import io.doeasy.redis.client.sentinel.SentinelClient;
import io.doeasy.redis.client.sentinel.SentinelServersManager;
import io.doeasy.redis.config.HostConfiguration;
import io.doeasy.redis.model.ClientType;
import io.doeasy.redis.utils.CircularList;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.util.CollectionUtils;

import lombok.extern.log4j.Log4j;
import redis.clients.jedis.BinaryClient.LIST_POSITION;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.Tuple;

import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

/**
 * 
 * @author kriswang
 *
 */
@Log4j
public class Client implements RedisClient {

	private RedisClientFactory factory;
	private SentinelServersManager manager;
	private RedisClient master;
	private CircularList<RedisClient> slaves;
	private final String masterName;
	private int cpuNums = Runtime.getRuntime().availableProcessors();
	private List<RedisPubSubConnection<String, String>> pubsubList = new CopyOnWriteArrayList<RedisPubSubConnection<String, String>>();
	private List<com.lambdaworks.redis.RedisClient> clients = new CopyOnWriteArrayList<com.lambdaworks.redis.RedisClient>();
	private List<NioEventLoopGroup> groups = new CopyOnWriteArrayList<NioEventLoopGroup>();

	public Client(RedisClientFactory factory, SentinelServersManager manager, String masterName) {
		this.factory = factory;
		this.manager = manager;
		this.masterName = masterName;
		this.updateMaster();
		this.updateSlaves();
		monitorMasterOrSlavesChanged();
	}
	
	@Override
	public String quit() {
		
		this.quitMaster();
		this.quitSlaves();
		
		if(!CollectionUtils.isEmpty(pubsubList)) {
			for(RedisPubSubConnection<String, String> pubsub : pubsubList) {
				pubsub.close();
			}
		}
		
		if(!CollectionUtils.isEmpty(clients)) {
			for(com.lambdaworks.redis.RedisClient client : clients) {
				client.shutdown();
			}
		}
		
		if(!CollectionUtils.isEmpty(groups)) {
			for(NioEventLoopGroup group : groups) {
				group.shutdownGracefully();
			}
		}
		
		return "OK";
	}

	private void monitorMasterOrSlavesChanged() {
		for (final SentinelClient sentinelClient : this.manager.getAvailibleSentinelServers()) {
			
			NioEventLoopGroup group = new NioEventLoopGroup(cpuNums - 1);
			
			com.lambdaworks.redis.RedisClient client = new com.lambdaworks.redis.RedisClient(group, sentinelClient.getHost(), sentinelClient.getPort(), sentinelClient.getTimeout());

			RedisPubSubConnection<String, String> pubsub = client.connectPubSub();
			pubsub.addListener(new RedisPubSubAdapter<String>() {
				
				public void subscribed(String channel, long count) {
					log.info(String.format("subscribed to channel: %s from Sentinel %s:%s", channel,
							sentinelClient.getHost(), sentinelClient.getPort()));
				}

				public void message(String channel, String msg) {
					if ("+slave".equals(channel)) {
						log.info(String.format("received message [%s] from channel '+slave', slave added...", msg));
						onSlaveAdded(msg);
					}
					if ("+sdown".equals(channel)) {
						log.info(String.format("received message [%s] from channel '+sdown', slave down...", msg));
						onSlaveDown(msg);
					}
					if ("-sdown".equals(channel)) {
						log.info(String.format("received message [%s] from channel '-sdown', slave restart...", msg));
						onSlaveUp(msg);
					}
					if ("+switch-master".equals(channel)) {
						log.info(String.format("received message [%s] from channel '+switch-master', switch master...", msg));
						updateMaster();
					}
				}
			});
			pubsub.subscribe("+switch-master", "+sdown", "-sdown", "+slave");
			pubsubList.add(pubsub);
			clients.add(client);
			groups.add(group);
		}
	}
	
	private void onSlaveAdded(String message) {
		String[] parts = message.split(" ");
		if(parts.length > 4 && "slave".equals(parts[0])) {
			String host = parts[2];
			String port = parts[3];
			this.slaves.add(this.factory.create(new HostConfiguration(host, Integer.parseInt(port))));
		}
	}
	
	public void onSlaveDown(String message) {
		String[] parts = message.split(" ");
		if(parts.length > 4 && "slave".equals(parts[0])) {
			String host = parts[2];
			String port = parts[3];
			this.slaves.remove(this.factory.create(new HostConfiguration(host, Integer.parseInt(port))));
		}
	}
	
	private void onSlaveUp(String message) {
		String[] parts = message.split(" ");
		if(parts.length > 4 && "slave".equals(parts[0])) {
			String host = parts[2];
			String port = parts[3];
			this.slaves.add(this.factory.create(new HostConfiguration(host, Integer.parseInt(port))));
		}
	}

	private void updateMaster() {
		this.quitMaster();
		this.master = this.factory.create(this.manager.getMaster(masterName));

	}

	private void updateSlaves() {
		this.quitSlaves();

		List<RedisClient> slaveClients = new ArrayList<RedisClient>();

		for (HostConfiguration configuration : this.manager.getSlaves(masterName)) {
			slaveClients.add(this.factory.create(configuration));
		}

		this.slaves = new CircularList<RedisClient>(slaveClients);
	}

	private void quitMaster() {
		if (this.master != null) {
			try {
				this.master.quit();
			} catch (Exception e) {
				log.error("Failed while closing the connection to master", e);
			}
		}
	}

	private void quitSlaves() {
		if (this.slaves != null) {
			for (RedisClient slave : this.slaves) {
				try {
					slave.quit();
				} catch (Exception e) {
					log.error("Failed while closing the connection to the slave", e);
				}
			}
		}
	}

	<T> T doExecute(ClientType type, ClientFunction<T> function) {
		RedisClient client;

		switch (type) {
		case SLAVE:
			client = this.getSlave();
			break;
		default:
			client = master;
			break;
		}

		return function.execute(client);
	}

	RedisClient getSlave() {
		if (this.slaves.empty()) {
			return this.master;
		} else {
			return this.slaves.next();
		}
	}

	@Override
	public String set(final byte[] key, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<String>() {
            @Override
            public String execute(RedisClient client) {
            	if(log.isDebugEnabled()) {
            		log.debug(String.format("set key, value %s, %s to master %", key, value, master));
            	}
                return client.set(key, value);
            }
        });
	}

	@Override
	public byte[] get(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<byte[]>() {
			@Override
			public byte[] execute(RedisClient client) {
				if(log.isDebugEnabled()) {
            		log.debug(String.format("get key %s from slave %", key, master));
            	}
				return client.get(key);
			}
		});
	}

	@Override
	public Boolean exists(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Boolean>(){
			@Override
			public Boolean execute(RedisClient client) {
				return client.exists(key);
			}
		});
	}

	@Override
	public Long persist(final byte[] key) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.persist(key);
			}
		});
	}

	@Override
	public String type(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<String>(){
			@Override
			public String execute(RedisClient client) {
				return client.type(key);
			}
		});
	}

	@Override
	public Long expire(final byte[] key, final int seconds) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.expire(key, seconds);
			}
		});
	}

	@Override
	public Long expireAt(final byte[] key, final long unixTime) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>() {
			@Override
			public Long execute(RedisClient client) {
				return client.expireAt(key, unixTime);
			}	
		});
	}

	@Override
	public Long ttl(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.ttl(key);
			}
		});
	}

	@Override
	public Boolean setbit(final byte[] key, final long offset, final boolean value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Boolean>(){
			@Override
			public Boolean execute(RedisClient client) {
				return client.setbit(key, offset, value);
			}
		});
	}

	@Override
	public Boolean setbit(final byte[] key, final long offset, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Boolean>(){
			@Override
			public Boolean execute(RedisClient client) {
				return client.setbit(key, offset, value);
			}
			
		});
	}

	@Override
	public Boolean getbit(final byte[] key, final long offset) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Boolean>() {
			public Boolean execute(RedisClient client) {
				return client.getbit(key, offset);
			}
		});
	}

	@Override
	public Long setrange(final byte[] key, final long offset, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.setrange(key, offset, value);
			}	
		});
	}

	@Override
	public byte[] getrange(final byte[] key, final long startOffset, final long endOffset) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.getrange(key, startOffset, endOffset);
			}	
		});
	}

	@Override
	public byte[] getSet(final byte[] key, final byte[] value) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.getSet(key, value);
			}
		});
	}

	@Override
	public Long setnx(final byte[] key, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.setnx(key, value);
			}
		});
	}

	@Override
	public String setex(final byte[] key, final int seconds, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<String>(){
			@Override
			public String execute(RedisClient client) {
				return client.setex(key, seconds, value);
			}
		});
	}

	@Override
	public Long decrBy(final byte[] key, final long integer) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.decrBy(key, integer);
			}
		});
	}

	@Override
	public Long decr(final byte[] key) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.decr(key);
			}
		});
	}

	@Override
	public Long incrBy(final byte[] key, final long integer) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.incrBy(key, integer);
			}
		});
	}

	@Override
	public Double incrByFloat(final byte[] key, final double value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Double>(){
			@Override
			public Double execute(RedisClient client) {
				return client.incrByFloat(key, value);
			}
		});
	}

	@Override
	public Long incr(final byte[] key) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.incr(key);
			}
		});
	}

	@Override
	public Long append(final byte[] key, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.append(key, value);
			}
		});
	}

	@Override
	public byte[] substr(final byte[] key, final int start, final int end) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.substr(key, start, end);
			}
		});
	}

	@Override
	public Long hset(final byte[] key, final byte[] field, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.hset(key, field, value);
			}
		});
	}

	@Override
	public byte[] hget(final byte[] key, final byte[] field) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.hget(key, field);
			}
		});
	}

	@Override
	public Long hsetnx(final byte[] key, final byte[] field, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.hsetnx(key, field, value);
			}
		});
	}

	@Override
	public String hmset(final byte[] key, final Map<byte[], byte[]> hash) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<String>(){
			@Override
			public String execute(RedisClient client) {
				return client.hmset(key, hash);
			}
		});
	}

	@Override
	public List<byte[]> hmget(final byte[] key, final byte[]... fields) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<List<byte[]>>(){
			@Override
			public List<byte[]> execute(RedisClient client) {
				return client.hmget(key, fields);
			}
		});
	}

	@Override
	public Long hincrBy(final byte[] key, final byte[] field, final long value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.hincrBy(key, field, value);
			}
		});
	}

	@Override
	public Double hincrByFloat(final byte[] key, final byte[] field, final double value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Double>(){
			@Override
			public Double execute(RedisClient client) {
				return client.hincrByFloat(key, field, value);
			}
		});
	}

	@Override
	public Boolean hexists(final byte[] key, final byte[] field) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Boolean>(){
			@Override
			public Boolean execute(RedisClient client) {
				return client.hexists(key, field);
			}
		});
	}

	@Override
	public Long hdel(final byte[] key, final byte[]... field) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.hdel(key, field);
			}
		});
	}

	@Override
	public Long hlen(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.hlen(key);
			}
		});
	}

	@Override
	public Set<byte[]> hkeys(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.hkeys(key);
			}
		});
	}

	@Override
	public Collection<byte[]> hvals(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Collection<byte[]>>(){
			@Override
			public Collection<byte[]> execute(RedisClient client) {
				return client.hvals(key);
			}
		});
	}

	@Override
	public Map<byte[], byte[]> hgetAll(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Map<byte[], byte[]>>(){
			@Override
			public  Map<byte[], byte[]> execute(RedisClient client) {
				return client.hgetAll(key);
			}
		});
	}

	@Override
	public Long rpush(final byte[] key, final byte[]... args) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public  Long execute(RedisClient client) {
				return client.rpush(key, args);
			}
		});
	}

	@Override
	public Long lpush(final byte[] key, final byte[]... args) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.lpush(key, args);
			}
		});
	}

	@Override
	public Long llen(final byte[] key) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.llen(key);
			}
		}); 
	}

	@Override
	public List<byte[]> lrange(final byte[] key, final long start, final long end) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<List<byte[]>>(){
			@Override
			public List<byte[]> execute(RedisClient client) {
				return client.lrange(key, start, end);
			}
		}); 
	}

	@Override
	public String ltrim(final byte[] key, final long start, final long end) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<String>(){
			@Override
			public String execute(RedisClient client) {
				return client.ltrim(key, start, end);
			}
		}); 
	}

	@Override
	public byte[] lindex(final byte[] key, final long index) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.lindex(key, index);
			}
		}); 
	}

	@Override
	public String lset(final byte[] key, final long index, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<String>(){
			@Override
			public String execute(RedisClient client) {
				return client.lset(key, index, value);
			}
		}); 
	}

	@Override
	public Long lrem(final byte[] key, final long count, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.lrem(key, count, value);
			}
		}); 
	}

	@Override
	public byte[] lpop(final byte[] key) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.lpop(key);
			}
		});
	}

	@Override
	public byte[] rpop(final byte[] key) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.rpop(key);
			}
		});
	}

	@Override
	public Long sadd(final byte[] key, final byte[]... member) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.sadd(key, member);
			}
		});
	}

	@Override
	public Set<byte[]> smembers(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.smembers(key);
			}
		});
	}

	@Override
	public Long srem(final byte[] key, final byte[]... member) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.srem(key, member);
			}
		});
	}

	@Override
	public byte[] spop(final byte[] key) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.spop(key);
			}
		});
	}

	@Override
	public Long scard(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.scard(key);
			}
		});
	}

	@Override
	public Boolean sismember(final byte[] key, final byte[] member) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Boolean>(){
			@Override
			public Boolean execute(RedisClient client) {
				return client.sismember(key, member);
			}
		});
	}

	@Override
	public byte[] srandmember(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.srandmember(key);
			}
		});
	}

	@Override
	public List<byte[]> srandmember(final byte[] key, final int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<List<byte[]>>(){
			@Override
			public List<byte[]> execute(RedisClient client) {
				return client.srandmember(key, count);
			}
		});
	}

	@Override
	public Long strlen(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.strlen(key);
			}
		});
	}

	@Override
	public Long zadd(final byte[] key, final double score, final byte[] member) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zadd(key, score, member);
			}
		});
	}

	@Override
	public Long zadd(final byte[] key, final Map<byte[], Double> scoreMembers) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zadd(key, scoreMembers);
			}
		});
	}

	@Override
	public Set<byte[]> zrange(final byte[] key, final long start, final long end) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrange(key, start, end);
			}
		});
	}

	@Override
	public Long zrem(final byte[] key, final byte[]... member) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zrem(key, member);
			}
		});
	}

	@Override
	public Double zincrby(final byte[] key, final double score, final byte[] member) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Double>(){
			@Override
			public Double execute(RedisClient client) {
				return client.zincrby(key, score, member);
			}
		});
	}

	@Override
	public Long zrank(final byte[] key, final byte[] member) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zrank(key, member);
			}
		});
	}

	@Override
	public Long zrevrank(final byte[] key, final byte[] member) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zrevrank(key, member);
			}
		});
	}

	@Override
	public Set<byte[]> zrevrange(final byte[] key, final long start, final long end) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrevrange(key, start, end);
			}
		});
	}

	@Override
	public Set<Tuple> zrangeWithScores(final byte[] key, final long start, final long end) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrangeWithScores(key, start, end);
			}
		});
	}

	@Override
	public Set<Tuple> zrevrangeWithScores(final byte[] key, final long start, final long end) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrevrangeWithScores(key, start, end);
			}
		});
	}

	@Override
	public Long zcard(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zcard(key);
			}
		});
	}

	@Override
	public Double zscore(final byte[] key, final byte[] member) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Double>(){
			@Override
			public Double execute(RedisClient client) {
				return client.zscore(key, member);
			}
		});
	}

	@Override
	public List<byte[]> sort(final byte[] key) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<List<byte[]>>(){
			@Override
			public List<byte[]> execute(RedisClient client) {
				return client.sort(key);
			}
		});
	}

	@Override
	public List<byte[]> sort(final byte[] key, final SortingParams sortingParameters) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<List<byte[]>>(){
			@Override
			public List<byte[]> execute(RedisClient client) {
				return client.sort(key, sortingParameters);
			}
		});
	}

	@Override
	public Long zcount(final byte[] key, final double min, final double max) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zcount(key, min, max);
			}
		});
	}

	@Override
	public Long zcount(final byte[] key, final byte[] min, final byte[] max) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zcount(key, min, max);
			}
		});
	}

	@Override
	public Set<byte[]> zrangeByScore(final byte[] key, final double min, final double max) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrangeByScore(key, min, max);
			}
		});
	}

	@Override
	public Set<byte[]> zrangeByScore(final byte[] key,final  byte[] min,final  byte[] max) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrangeByScore(key, min, max);
			}
		});
	}

	@Override
	public Set<byte[]> zrevrangeByScore(final byte[] key,final  double max, final double min) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrevrangeByScore(key, max, min);
			}
		});
	}

	@Override
	public Set<byte[]> zrangeByScore(final byte[] key, final double min, final double max, final int offset, final int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrangeByScore(key, min, max, offset, count);
			}
		});
	}

	@Override
	public Set<byte[]> zrevrangeByScore(final byte[] key, final byte[] max, final byte[] min) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrevrangeByScore(key, max, min);
			}
		});
	}

	@Override
	public Set<byte[]> zrangeByScore(final byte[] key, final byte[] min, final byte[] max, final int offset, final int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrangeByScore(key, min, max, offset, count);
			}
		});
	}

	@Override
	public Set<byte[]> zrevrangeByScore(final byte[] key, final double max, final double min, final int offset,final  int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrevrangeByScore(key, max, min, offset, count);
			}
		});
	}

	@Override
	public Set<Tuple> zrangeByScoreWithScores(final byte[] key, final double min, final double max) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrangeByScoreWithScores(key, min, max);
			}
		});
	}

	@Override
	public Set<Tuple> zrevrangeByScoreWithScores(final byte[] key, final double max, final double min) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrevrangeByScoreWithScores(key, max, min);
			}
		});
	}

	@Override
	public Set<Tuple> zrangeByScoreWithScores(final byte[] key,final double min, final double max, final int offset, final int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrangeByScoreWithScores(key, min, max, offset, count);
			}
		});
	}

	@Override
	public Set<byte[]> zrevrangeByScore(final byte[] key, final byte[] max, final byte[] min, final int offset, final int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrevrangeByScore(key, max, min, offset, count);
			}
		});
	}

	@Override
	public Set<Tuple> zrangeByScoreWithScores(final byte[] key, final byte[] min, final byte[] max) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrangeByScoreWithScores(key, min, max);
			}
		});
	}

	@Override
	public Set<Tuple> zrevrangeByScoreWithScores(final byte[] key, final byte[] max, final byte[] min) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrevrangeByScoreWithScores(key, max, min);
			}
		});
	}

	@Override
	public Set<Tuple> zrangeByScoreWithScores(final byte[] key, final byte[] min, final byte[] max, final int offset, final int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrangeByScoreWithScores(key, min, max, offset, count);
			}
		});
	}

	@Override
	public Set<Tuple> zrevrangeByScoreWithScores(final byte[] key, final double max, final double min, final int offset, final int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrevrangeByScoreWithScores(key, max, min, offset, count);
			}
		});
	}

	@Override
	public Set<Tuple> zrevrangeByScoreWithScores(final byte[] key, final byte[] max, final byte[] min, final int offset, final int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<Tuple>>(){
			@Override
			public Set<Tuple> execute(RedisClient client) {
				return client.zrevrangeByScoreWithScores(key, max, min, offset, count);
			}
		});
	}

	@Override
	public Long zremrangeByRank(final byte[] key, final long start, final long end) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>() {
			@Override
			public Long execute(RedisClient client) {
				return client.zremrangeByRank(key, start, end);
			}
		});
	}

	@Override
	public Long zremrangeByScore(final byte[] key, final double start, final double end) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zremrangeByScore(key, start, end);
			}	
		});
	}

	@Override
	public Long zremrangeByScore(final byte[] key, final byte[] start, final byte[] end) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zremrangeByScore(key, start, end);
			}	
		});
	}

	@Override
	public Long zlexcount(final byte[] key, final byte[] min, final byte[] max) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zlexcount(key, min, max);
			}	
		});
	}

	@Override
	public Set<byte[]> zrangeByLex(final byte[] key, final byte[] min, final byte[] max) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrangeByLex(key, min, max);
			}	
		});
	}

	@Override
	public Set<byte[]> zrangeByLex(final byte[] key, final byte[] min, final byte[] max, final int offset, final int count) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Set<byte[]>>(){
			@Override
			public Set<byte[]> execute(RedisClient client) {
				return client.zrangeByLex(key, min, max, offset, count);
			}	
		});
	}

	@Override
	public Long zremrangeByLex(final byte[] key, final byte[] min, final byte[] max) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.zremrangeByLex(key, min, max);
			}		
		});
	}

	@Override
	public Long linsert(final byte[] key, final LIST_POSITION where, final byte[] pivot, final byte[] value) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.linsert(key, where, pivot, value);
			}		
		});
	}

	@Override
	public Long lpushx(final byte[] key, final byte[]... arg) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.lpushx(key, arg);
			}		
		});
	}

	@Override
	public Long rpushx(final byte[] key, final byte[]... arg) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.rpushx(key, arg);
			}		
		});
	}

	@Override
	public List<byte[]> blpop(final byte[] arg) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<List<byte[]>>(){
			@SuppressWarnings("deprecation")
			@Override
			public List<byte[]> execute(RedisClient client) {
				log.warn("blpop method has been deprecated.");
				return client.blpop(arg);
			}		
		});
	}

	@Override
	public List<byte[]> brpop(final byte[] arg) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<List<byte[]>>(){
			@SuppressWarnings("deprecation")
			@Override
			public List<byte[]> execute(RedisClient client) {
				log.warn("brpop method has been deprecated.");
				return client.blpop(arg);
			}		
		});
	}

	@Override
	public Long del(final byte[] key) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.del(key);
			}	
		});
	}

	@Override
	public byte[] echo(final byte[] arg) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<byte[]>(){
			@Override
			public byte[] execute(RedisClient client) {
				return client.echo(arg);
			}	
		});
	}

	@Override
	public Long move(final byte[] key, final int dbIndex) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.move(key, dbIndex);
			}	
		});
	}

	@Override
	public Long bitcount(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.bitcount(key);
			}	
		});
	}

	@Override
	public Long bitcount(final byte[] key, final long start, final long end) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.bitcount(key, start, end);
			}	
		});
	}

	@Override
	public Long pfadd(final byte[] key, final byte[]... elements) {
		return this.doExecute(ClientType.MASTER, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.pfadd(key, elements);
			}	
		});
	}

	@Override
	public long pfcount(final byte[] key) {
		return this.doExecute(ClientType.SLAVE, new ClientFunction<Long>(){
			@Override
			public Long execute(RedisClient client) {
				return client.pfcount(key);
			}	
		});
	}

}
