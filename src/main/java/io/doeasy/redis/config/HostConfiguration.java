package io.doeasy.redis.config;

import lombok.Data;
import redis.clients.jedis.Protocol;

/**
 * 
 * @author kriswang
 *
 */
@Data
public class HostConfiguration {
	private String host;
	private int port;
	private int database;   //default database is 0
	private int timeout; //1000ms
	
	public HostConfiguration() {
		this("localhost", 0, Protocol.DEFAULT_TIMEOUT, Protocol.DEFAULT_DATABASE);
	}
	
	public HostConfiguration(String host, int port) {
        this( host, port, Protocol.DEFAULT_TIMEOUT, Protocol.DEFAULT_DATABASE );
    }

    public HostConfiguration(String host, int port, int timeout) {
        this( host, port, timeout, Protocol.DEFAULT_DATABASE );
    }

    public HostConfiguration(String host, int port, int timeout, int database) {

        if ( host == null || host.trim().isEmpty() ) {
            throw new IllegalArgumentException("'host' can not be null");
        }

        this.port = port;
        this.timeout = timeout;
        this.host = host;
        this.database = database;
    }
}
