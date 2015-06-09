package io.doeasy.redis;

import io.doeasy.redis.config.HostConfiguration;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import lombok.ToString;

/**
 * 
 * @author kriswang
 *
 */
@ToString
public class ClusterStatus {
	private final HostConfiguration master;
	private final Set<HostConfiguration> slaves = new HashSet<HostConfiguration>();
	private final Set<HostConfiguration> unavailables = new HashSet<HostConfiguration>();

	public ClusterStatus(HostConfiguration master,
			Collection<HostConfiguration> slaves,
			Collection<HostConfiguration> unavailables) {
		this.master = master;
		this.slaves.addAll(slaves);
		this.unavailables.addAll(unavailables);
	}

	public HostConfiguration getMaster() {
		return master;
	}

	public Set<HostConfiguration> getSlaves() {
		return slaves;
	}

	public Set<HostConfiguration> getUnavailables() {
		return unavailables;
	}

	public ClusterStatusType difference(ClusterStatus status) {

		boolean masterChanged = false;
		boolean slavesChanged = !this.slaves.equals(status.slaves);

		if (this.master != null && status.master != null) {
			masterChanged = !this.master.equals(status.master);
		}

		if (masterChanged && slavesChanged) {
			return ClusterStatusType.BOTH;
		} else {
			if (masterChanged) {
				return ClusterStatusType.MASTER;
			} else {
				if (slavesChanged) {
					return ClusterStatusType.SLAVES;
				} else {
					return ClusterStatusType.NO_DIFFERENCE;
				}
			}
		}
	}

	public boolean isEmpty() {
		return this.master == null && this.slaves.isEmpty()
				&& this.unavailables.isEmpty();
	}

	public boolean hasMaster() {
		return this.master != null;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ClusterStatus))
			return false;

		ClusterStatus that = (ClusterStatus) o;

		if (master != null ? !master.equals(that.master) : that.master != null)
			return false;
		if (!slaves.equals(that.slaves))
			return false;
		if (!unavailables.equals(that.unavailables))
			return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = master != null ? master.hashCode() : 0;
		result = 31 * result + slaves.hashCode();
		result = 31 * result + unavailables.hashCode();
		return result;
	}
}
