package io.doeasy.redis.client.sentinel.web;

import io.doeasy.redis.client.sentinel.SentinelServersManager;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;

/**
 * 
 * @author kriswang
 * 
 */
public class SentinelServerServlet extends HttpServlet {

	private static final long serialVersionUID = 4746952540444485695L;

	private static Configuration cfg;
	private SentinelServersManager manager;

	public SentinelServerServlet(SentinelServersManager manager) {
		cfg = new Configuration();
		this.manager = manager;
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		cfg.setClassForTemplateLoading(SentinelServerServlet.class, "templates");
		cfg.setTemplateUpdateDelay(0);

		try{
			final OutputStream output = resp.getOutputStream();
			OutputStreamWriter wr = new OutputStreamWriter(output);
			cfg.getTemplate("sentinel-bootstrap-sentinel.ftl").process(new SentinelServerView(manager), wr);
		} catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (TemplateException e) {
            e.printStackTrace();
        }
		
	}
}
