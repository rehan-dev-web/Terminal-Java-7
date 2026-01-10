
package com.terminal.linux;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONObject;
import bsh.Interpreter;

public class AddonHelper {

    private MainActivity activity;
    private String ADDON_DIR;

    public AddonHelper(MainActivity activity, String addonDir) {
        this.activity = activity;
        this.ADDON_DIR = addonDir;
    }

    public void installAddon(final String zipPath) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						activity.runOnUiThread(new Runnable() { public void run() { activity.appendRawHtml("Installing... <font color='#FFFF00'>0%</font>"); }});

						File zipFile = new File(zipPath);
						ZipFile zip = new ZipFile(zipFile);
						int totalFiles = zip.size();
						int count = 0;
						final String addonName = zipFile.getName().replace(".zip", "");
						File targetDir = new File(ADDON_DIR, addonName);

						if (targetDir.exists()) deleteRecursive(targetDir);
						targetDir.mkdirs();

						Enumeration<? extends ZipEntry> entries = zip.entries();
						while (entries.hasMoreElements()) {
							ZipEntry entry = entries.nextElement();
							File destFile = new File(targetDir, entry.getName());
							if (entry.isDirectory()) { destFile.mkdirs(); } 
							else {
								destFile.getParentFile().mkdirs();
								InputStream is = zip.getInputStream(entry);
								FileOutputStream fos = new FileOutputStream(destFile);
								byte[] buffer = new byte[1024]; int len;
								while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
								fos.close(); is.close();
							}
							count++;
							final int progress = (int) ((count / (float) totalFiles) * 100);
							activity.runOnUiThread(new Runnable() { public void run() { activity.updateLastLine("Installing... <font color='#FFFF00'>" + progress + "%</font>"); } });
						}
						zip.close();

						// VALIDASI & BACA INFO ADDON
						File defFile = new File(targetDir, "definition.json");
						if (!defFile.exists()) throw new Exception("definition.json hilang!");

						BufferedReader br = new BufferedReader(new FileReader(defFile));
						StringBuilder jsonSb = new StringBuilder(); String line;
						while ((line = br.readLine()) != null) jsonSb.append(line);
						br.close();

						JSONObject json = new JSONObject(jsonSb.toString());
						String scriptName = json.getString("mainScriptName");
						String iconName = json.getString("iconAddon");
                        String description = json.optString("description", "No description provided.");

						if (!new File(targetDir, scriptName).exists()) throw new Exception("Script tidak ditemukan!");
						File iconFile = new File(targetDir, iconName);
						if (!iconFile.exists()) throw new Exception("Icon tidak ditemukan!");

                        // SIAPKAN HTML UNTUK TAMPILAN KEREN
                        final String iconPath = iconFile.getAbsolutePath();
                        final String finalName = addonName.toUpperCase();
                        final String finalDesc = description;

						activity.runOnUiThread(new Runnable() { 
								public void run() { 
									// Render HTML dengan Gambar & Styling
									String html = 
										"<br>" + 
										"<img src='" + iconPath + "'><br>" + 
										"<font color='#00FF00'><b>" + finalName + "</b></font><br>" +
										"<i><small><font color='#AAAAAA'>" + finalDesc + "</font></small></i><br>" +
										"<font color='#FFFF00'>Successfully Installed!</font><br>";
									activity.updateLastLine(html); 
								}
							});

					} catch (Exception e) {
						final String err = e.getMessage();
						activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog("<font color='#FF0000'>Install Gagal: " + err + "</font>"); }});
					}
				}
			}).start();
    }

    public void runAddonScript(final String addonName, final String[] args) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						File addonFolder = new File(ADDON_DIR, addonName);
						File defFile = new File(addonFolder, "definition.json");
						if (!defFile.exists()) { activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog("Error: Addon rusak."); }}); return; }

						BufferedReader br = new BufferedReader(new FileReader(defFile));
						StringBuilder jsonSb = new StringBuilder(); String line;
						while ((line = br.readLine()) != null) jsonSb.append(line);
						br.close();

						JSONObject json = new JSONObject(jsonSb.toString());
						String scriptName = json.getString("mainScriptName");
						File scriptFile = new File(addonFolder, scriptName);

						Interpreter i = new Interpreter();
						i.set("context", activity); // PASSING CONTEXT PENTING
						i.set("args", args);
						i.set("addonPath", addonFolder.getAbsolutePath());
						i.eval("void print(String s) { context.runOnUiThread(new Runnable() { public void run() { context.formatAndAppendLog(s); } }); }");
						i.source(scriptFile.getAbsolutePath());

					} catch (Exception e) {
						final String err = e.getMessage();
						activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog("Addon Error: " + err); }});
					}
				}
			}).start();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) for (File child : fileOrDirectory.listFiles()) deleteRecursive(child);
        fileOrDirectory.delete();
    }
}
