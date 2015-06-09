package io.doeasy.redis.model;

import lombok.Data;

/**
 * redis node information
 * 
 * @author kriswang
 *
 */
@Data
public class RedisNodeInfo {
	private String name;
	private String ip;
	private Integer port;
	private String runid;
	private String flags;
	private Integer pendingCommands;
	private Integer lastPingSent;
	private Integer lastOkPingReply;
	private Integer lastPingReply;
	private Integer downAfterMilliseconds;
	private Long infoRefresh;
	private String roleReported;
	private Long roleReportedTime;
	private Integer configEpoch;
	private Integer numSlaves;
	private Integer numOtherSentinels;
	private Integer quorum;
	private Long failoverTimeout;
	private Integer parallelSyncs;
}
