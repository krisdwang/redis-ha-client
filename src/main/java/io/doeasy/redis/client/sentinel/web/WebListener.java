package io.doeasy.redis.client.sentinel.web;

import io.doeasy.redis.client.sentinel.SentinelServersManager;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;

import lombok.extern.log4j.Log4j;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * 
 * @author kriswang
 *
 */
@Log4j
public class WebListener {
	private final static String TEMPLATE_PATH = "io/doeasy/redis/client/sentinel/web";
	private final static int DEFAULT_PORT = 8080;

	private final SentinelServersManager manager;
	
	private final Server httpServer;

	public WebListener(SentinelServersManager manager) {
		this.manager = manager;
		httpServer = new Server(DEFAULT_PORT);
	}

	public Map<String, HttpServlet> getServletMappings() {
		final SentinelServerServlet sentinelServerServlet = new SentinelServerServlet(this.manager);
		final RedisServerServlet redisServerServlet = new RedisServerServlet(this.manager);

		Map<String, HttpServlet> mappings = new HashMap<>();
		mappings.put("/", new DefaultServlet());
		mappings.put("/sentinel/*", sentinelServerServlet);
		mappings.put("/redis/*", redisServerServlet);

		return mappings;
	}

	public void start() throws Exception {
		log.info("Listening on " + DEFAULT_PORT);

		final HandlerList handlerList = new HandlerList();

		final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		final URL templateURL = classLoader.getResource(TEMPLATE_PATH);

		if (templateURL != null) {
			final String webResourceDir = templateURL.toExternalForm();
			final ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
			servletHandler.setResourceBase(webResourceDir);

			servletHandler.setContextPath("/");
			Map<String, HttpServlet> servletMap = getServletMappings();

			for (Map.Entry<String, HttpServlet> map : servletMap.entrySet()) {
				servletHandler.addServlet(new ServletHolder(map.getValue()), map.getKey());
			}

			handlerList.addHandler(servletHandler);

			httpServer.setHandler(handlerList);
			httpServer.start();
		} else {
			throw new IllegalArgumentException("Template path inaccessible / does not exist.");
		}
	}
	
	public void stop() throws Exception {
		this.httpServer.stop();
		
	}
}
