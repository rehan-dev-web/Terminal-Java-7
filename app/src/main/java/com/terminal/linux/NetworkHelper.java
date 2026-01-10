package com.terminal.linux;

import java.io.*;
import java.net.*;
import com.jcraft.jsch.*;
import org.json.*;

public class NetworkHelper {

    private MainActivity activity;
    private ServerSocket serverSocket;
    private boolean isServerRunning = false;
    private SocksHandler socksHandler; // Pastikan kelas SocksHandler ada/import

    public NetworkHelper(MainActivity activity) {
        this.activity = activity;
    }

    public void runCurl(final String urlString) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						URL url = new URL(urlString);
						HttpURLConnection conn = (HttpURLConnection) url.openConnection();
						conn.setRequestMethod("GET"); conn.setConnectTimeout(15000);
						conn.setRequestProperty("User-Agent", "RH-Terminal/Android");
						BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
						StringBuilder response = new StringBuilder(); String line;
						while ((line = in.readLine()) != null) response.append(line);
						in.close();
						final String res = response.toString();
						activity.runOnUiThread(new Runnable() { public void run() { 
									try { activity.appendRawHtml(activity.formatJsonToTable(res)); } 
									catch (Exception e) { activity.formatAndAppendLog(res); } 
								}});
					} catch (Exception e) { 
						final String err = e.getMessage(); 
						activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog("Error: " + err); }}); 
					}
				}
			}).start();
    }

    public void runSSH(final String u, final String p, final String h, final String c) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						JSch jsch = new JSch();
						Session s = jsch.getSession(u, h, 22);
						s.setPassword(p);
						java.util.Properties cfg = new java.util.Properties();
						cfg.put("StrictHostKeyChecking", "no");
						s.setConfig(cfg);
						activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog("Connecting SSH..."); }});
						s.connect(10000);
						ChannelExec ch = (ChannelExec) s.openChannel("exec");
						ch.setCommand(c); ch.setInputStream(null); ch.setErrStream(System.err);
						BufferedReader in = new BufferedReader(new InputStreamReader(ch.getInputStream()));
						ch.connect();
						String l; while ((l = in.readLine()) != null) {
							final String f = l; activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog(f); }});
						}
						ch.disconnect(); s.disconnect();
						activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog("SSH Closed."); }});
					} catch (Exception e) {
						final String err = e.getMessage();
						activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog("Error SSH: " + err); }});
					}
				}
			}).start();
    }

    public void startWebServer(final int port, final String path) {
        if (isServerRunning) stopWebServer();
        isServerRunning = true;
        activity.formatAndAppendLog("Localhost started on port: " + port);
        new Thread(new Runnable() {
				public void run() {
					try {
						serverSocket = new ServerSocket(port);
						while (isServerRunning) {
							Socket client = serverSocket.accept();
							handleClient(client, path);
						}
					} catch (Exception e) {}
				}
			}).start();
    }

    public void stopWebServer() {
        isServerRunning = false;
        try { if(serverSocket != null) serverSocket.close(); } catch(Exception e){}
        activity.formatAndAppendLog("Server Stopped.");
    }

    private void handleClient(Socket client, String filePath) {
        try {
            File file = new File(filePath);
            if (file.isDirectory()) file = new File(file, "index.html");
            PrintWriter out = new PrintWriter(client.getOutputStream());
            out.println("HTTP/1.1 200 OK"); out.println("Content-Type: text/html\n"); out.flush();
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                byte[] buf = new byte[1024]; int len;
                while ((len = fis.read(buf)) > 0) client.getOutputStream().write(buf, 0, len);
                fis.close();
            } else out.println("<h1>404 Not Found</h1>");
            out.flush(); client.close();
        } catch (Exception e) {}
    }

    // Taruh logic SocksHandler disini kalau mau dipindah juga
}
