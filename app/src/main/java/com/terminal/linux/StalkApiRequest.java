package com.terminal.linux;

import java.io.*;
import java.net.*;
import java.util.Iterator;
import org.json.JSONObject;
import android.text.Html;

public class StalkApiRequest {

    private MainActivity activity;

    public StalkApiRequest(MainActivity activity) {
        this.activity = activity;
    }

    public void execute(final String platform, final String param) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						// 1. TENTUKAN URL
						String apiUrl = "";
						String p = platform.toLowerCase();

						if (p.equals("tiktok") || p.equals("tt")) 
							apiUrl = "https://api.givy.my.id/stalker/tiktok?username=" + param;
						else if (p.equals("ig") || p.equals("instagram")) 
							apiUrl = "https://api.givy.my.id/stalk/ig?username=" + param;
						else if (p.equals("ff") || p.equals("freefire")) 
							apiUrl = "https://api.givy.my.id/stalk/ff?playerid=" + param;
						else if (p.equals("roblox") || p.equals("rb")) 
							apiUrl = "https://api.givy.my.id/stalk/roblox?username=" + param;
						else {
							logToUi("Platform tidak dikenal! (Gunakan: tiktok, ig, ff, roblox)");
							return;
						}

						logToUi("Stalking " + platform + ": " + param + "...");

						// 2. REQUEST DATA
						URL url = new URL(apiUrl);
						HttpURLConnection conn = (HttpURLConnection) url.openConnection();
						conn.setRequestMethod("GET");
						conn.setConnectTimeout(15000); // 15 detik timeout

						BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
						StringBuilder response = new StringBuilder();
						String line;
						while ((line = in.readLine()) != null) response.append(line);
						in.close();

						// 3. PARSING JSON KE HTML CANTIK
						JSONObject json = new JSONObject(response.toString());
						final String htmlResult = parseJsonToHtml(json);

						// 4. KIRIM KE UI
						activity.runOnUiThread(new Runnable() {
								public void run() {
									activity.updateLastLine(htmlResult + "<br><br>");
								}
							});

					} catch (Exception e) {
						logToUi("Gagal Stalking: " + e.getMessage());
					}
				}
			}).start();
    }

    // === METHOD PINTAR PARSING JSON & GAMBAR ===
    private String parseJsonToHtml(JSONObject json) throws Exception {
        StringBuilder sb = new StringBuilder();
        Iterator<String> keys = json.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);

            // Bikin Key warna Cyan
            sb.append("<font color='#00FFFF'>").append(key).append("</font>: ");

            if (value instanceof JSONObject) {
                // Kalo isinya JSON lagi (Nested), panggil diri sendiri (Recursive)
                sb.append("<br>").append(parseJsonToHtml((JSONObject) value));
            } else {
                String valStr = value.toString();

                // CEK APAKAH INI LINK MEDIA?
                if (valStr.startsWith("http")) {
                    String ext = "";
                    if (valStr.contains(".")) {
						ext = valStr.substring(valStr.lastIndexOf(".") + 1).toLowerCase();
                    }

                    // A. KALO GAMBAR -> DOWNLOAD & TAMPILIN
                    if (ext.startsWith("jpg") || ext.startsWith("jpeg") || ext.startsWith("png") || ext.startsWith("webp")) {
                        String localPath = downloadImage(valStr);
                        if (localPath != null) {
                            // ImageGetter di MainActivity otomatis baca path ini
                            sb.append("<br><img src='" + localPath + "'><br>");
                        } else {
                            sb.append("<a href='" + valStr + "'>[LINK FOTO]</a>");
                        }
                    }
                    // B. KALO VIDEO/AUDIO -> BIKIN KLIKABLE LINK
                    else if (ext.startsWith("mp4") || ext.startsWith("mp3") || ext.startsWith("mkv")) {
						// Warna biru linknya
						sb.append("<br><a href='" + valStr + "'><b>[ ▶ PUTAR MEDIA ]</b></a>");
                    }
                    else {
                        // Link biasa
                        sb.append("<a href='" + valStr + "'>" + valStr + "</a>");
                    }
                } else {
                    // Teks biasa warna putih
                    sb.append("<font color='#FFFFFF'>").append(valStr).append("</font>");
                }
            }
            sb.append("<br>");
        }
        return sb.toString();
    }

    // === DOWNLOADER KHUSUS GAMBAR (CACHE) ===
    private String downloadImage(String urlStr) {
        try {
            URL url = new URL(urlStr);
            InputStream in = url.openStream();

            // Nama file unik pake waktu sekarang
            String fileName = "stalk_" + System.currentTimeMillis() + ".png";
            File cacheDir = activity.getCacheDir();
            File outFile = new File(cacheDir, fileName);

            FileOutputStream out = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);

            in.close();
            out.close();

            return outFile.getAbsolutePath(); // Return path lokal buat ImageGetter
        } catch (Exception e) {
            return null;
        }
    }

    private void logToUi(final String msg) {
        activity.runOnUiThread(new Runnable() {
				public void run() { activity.formatAndAppendLog(msg); }
			});
    }
}
