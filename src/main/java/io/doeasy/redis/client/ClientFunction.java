package io.doeasy.redis.client;

/**
 * 
 * @author kriswang
 *
 * @param <T>
 */
public interface ClientFunction<T> {

	public T execute(RedisClient client);

}