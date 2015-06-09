<#-- @ftlvariable name="" type="io.doeasy.redis.sentinel.web.SentinelServerView" -->

<h2>Sentinel Servers information</h2>

<table>
	<thead>
		<tr>
			<td>host</td>
			<td>port</td>
		</tr>
	</thead>
	<tbody>
		<#list availibleServers as server>
		<tr>
			<td>${server.host}</td>
			<td>${server.port}</td>
		</tr>
		</#list>
	</tbody>
</table>
