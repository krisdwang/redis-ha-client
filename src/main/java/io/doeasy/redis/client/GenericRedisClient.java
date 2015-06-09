package io.doeasy.redis.client;

import lombok.Getter;
import lombok.ToString;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

/**
 * 
 * @author kriswang
 *
 */
@ToString
public class GenericRedisClient extends Jedis implements RedisClient{

	@Getter
	private final String host;
	
	@Getter
	private final int port;
	
	@Getter
	private final int timeout;
	
	@Getter
	private final int database;
	
	public GenericRedisClient(String host, int port, int timeout, int database) {
		super(host, port, timeout);
		this.host = host;
		this.port = port;
		this.timeout = timeout;
		if (database != Protocol.DEFAULT_DATABASE) {
			this.select(database);
			this.database = database;
		} else {
			this.database = Protocol.DEFAULT_DATABASE;
		}
	}

}
