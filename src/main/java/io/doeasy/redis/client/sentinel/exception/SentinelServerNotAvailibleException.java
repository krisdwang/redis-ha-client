package io.doeasy.redis.client.sentinel.exception;

/**
 * 
 * @author kriswang
 *
 */
public class SentinelServerNotAvailibleException extends RuntimeException {

	private static final long serialVersionUID = 435524597359137949L;

	public SentinelServerNotAvailibleException(String message){
		super(message);
	}
}
