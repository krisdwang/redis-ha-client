package io.doeasy.redis.client;

import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryContext;

public class RedisClientRetryRecoveryCallback implements RecoveryCallback<Void>{

	@Override
	public Void recover(RetryContext ctx) throws Exception {
		return null;
	}

}
