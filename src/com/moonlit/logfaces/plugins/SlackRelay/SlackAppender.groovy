package com.moonlit.logfaces.plugins.SlackRelay

import com.moonlit.logfaces.server.core.LogEvent
import com.moonlit.logfaces.server.core.LogFacesPlugin
import java.security.cert.X509Certificate

import javax.net.ssl.SSLContext

import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.ssl.TrustStrategy
import org.apache.http.util.EntityUtils

import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.gson.Gson
import com.google.gson.GsonBuilder

class SlackAppender implements LogFacesPlugin{
	private Properties config;
	private String DEFAULT_URL = "https://hooks.slack.com/services/my/web/hook"; 
	private String DEFAULT_CHANNEL = "#lfs";
	private HttpClient client;
	
	public SlackAppender() {
		config = new Properties();
		config.setProperty("slack.url", DEFAULT_URL);
		config.setProperty("slack.channel", DEFAULT_CHANNEL);
		
		try {
			InputStream is = getClass().getResourceAsStream("/config.properties");
			config.load(is);
		}
		catch(Exception e) {
			// rely on defaults
		}
		
		SSLContext sslctx = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
			@Override
			public boolean isTrusted(X509Certificate[] arg0, String arg1) throws java.security.cert.CertificateException {
				return true;
			}
		}).build();
	
		client = HttpClients.custom()
		                    .setSSLContext(sslctx)
		                    .setSSLHostnameVerifier(new NoopHostnameVerifier())
		                    .build();
	}
    
    @Override
    public String getName() {
        return "Slack relay";
    }

    @Override
    public List<String> getArgs() {
        return Lists.newArrayList("url", "channel");
    }

    @Override
    public Object validate(Map<String, String> args) {
		if(args == null) {
			args = Maps.newHashMap();
	        args.put("url", config.getOrDefault("slack.url", DEFAULT_URL));
	        args.put("channel", config.getOrDefault("slack.channel", DEFAULT_CHANNEL));
		}

		send("This is a plugin validation call", args);
		return "Message was sent to slack channel successfully";
    }

    @Override
    public Object handleEvents(List<LogEvent> events, Map<String, String> args) {
        String message = events.get(0).getMessage();
		send(message, args);
        return "message was sent to slack channel " + args.get("channel");
    }

    private void send(String message, Map<String, String> args) throws Exception{
		String url = args.getOrDefault("url", config.getProperty("slack.url"));	            
		String channel = args.getOrDefault("channel", config.getProperty("slack.channel"));
		
        Map<String, Object> payload = Maps.newHashMap();
        payload.put("text", message);
        payload.put("channel", channel);
        
        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json");
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        StringEntity body = new StringEntity(gson.toJson(payload), "UTF-8");
        body.setContentType("application/json");
        body.setContentEncoding("UTF-8");
        post.setEntity(body);
        
        HttpResponse hr = client.execute(post);
        EntityUtils.consumeQuietly(hr.getEntity());
        
        if(hr.getStatusLine().getStatusCode() != 200)
            throw new Exception(String.format("failed sending slack notification: %d", hr.getStatusLine().getStatusCode()));
    }
}