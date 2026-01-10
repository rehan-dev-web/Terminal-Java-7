package com.terminal.linux;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod; // PENTING BUAT KLIK LINK
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import org.json.*;

// IMPORT HELPER
import com.terminal.linux.StorageHelper;
import com.terminal.linux.AddonHelper;
import com.terminal.linux.NetworkHelper;
import com.terminal.linux.StalkApiRequest; // Pastikan file StalkApiRequest.java udah dibuat

public class MainActivity extends Activity {

    // KOMPONEN UI
    public TextView tvOutput;
    public EditText etInput;
    public ScrollView scrollView;
    public LinearLayout tabContainer;

    // HELPER OBJECTS(Variabel global)
    private StorageHelper storageHelper; //storage helper
    private AddonHelper addonHelper; // addon helper
    private NetworkHelper networkHelper;// network helper
    private StalkApiRequest stalkHelper; // Helper Stalking
	private FileManager fileManager; // file manager helper

    // DATA TAB
    public ArrayList<StringBuilder> tabData = new ArrayList<>();
    public ArrayList<String> tabNames = new ArrayList<>();
    public int currentTabIndex = 0;

    // DATA HISTORY & FOLDER
    public ArrayList<String> cmdHistory = new ArrayList<>();
    public int historyIndex = -1;
    private String BASE_DIR; 
    private String ADDON_DIR;

    // === JURUS IMAGE GETTER (Biar TextView bisa baca gambar lokal) ===
    private Html.ImageGetter imageGetter = new Html.ImageGetter() {
        @Override
        public Drawable getDrawable(String source) {
            try {
                // Source = path file di HP
                Drawable d = Drawable.createFromPath(source);
                if (d != null) {
                    // Atur ukuran icon biar enak diliat (120x120 pixel)
                    d.setBounds(0, 0, 120, 120); 
                }
                return d;
            } catch (Exception e) { return null; }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // 1. SETUP FOLDER
        BASE_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/terminal";
        ADDON_DIR = getFilesDir().getAbsolutePath() + "/addons"; 
        new File(BASE_DIR).mkdirs();
        new File(ADDON_DIR).mkdirs();

        // 2. LINK UI
        tvOutput = findViewById(R.id.tvOutput);
        etInput = findViewById(R.id.etInput);
        scrollView = findViewById(R.id.scroll);
        tabContainer = findViewById(R.id.tabContainer);

        // CONFIG TV OUTPUT (Biar link video bisa diklik)
        tvOutput.setMovementMethod(LinkMovementMethod.getInstance());

        // 3. INIT HELPER
        storageHelper = new StorageHelper(this);
        addonHelper = new AddonHelper(this, ADDON_DIR);
        networkHelper = new NetworkHelper(this); // Init network.
        stalkHelper = new StalkApiRequest(this); // Init Stalker.
		fileManager = new FileManager(this); // Init file manager.

        // 4. SETUP TOMBOL SHORTCUT (HISTORY & LAINNYA)
        setupShortcutButtons();

        // 5. LOAD SESSION
        storageHelper.loadSessions(); 

        // 6. LISTENER KEYBOARD
        etInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE || 
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                        String cmd = etInput.getText().toString();
                        appendRawHtml("<font color='#00FFFF'><b>$</b></font> <font color='#FFFFFF'>" + cmd + "</font><br>");
                        processCommand(cmd);
                        etInput.setText("");
                        return true;
                    }
                    return false;
                }
            });

        View.OnClickListener focusListener = new View.OnClickListener() {
            public void onClick(View v) { focusInput(); }
        };
        findViewById(R.id.rootLayout).setOnClickListener(focusListener);
        tvOutput.setOnClickListener(focusListener);
    }

    // === LOGIC TOMBOL SHORTCUT (History, Esc, dll) ===
    private void setupShortcutButtons() {
        Button btnUp = findViewById(R.id.btnUp);
        Button btnDown = findViewById(R.id.btnDown);
        Button btnEsc = findViewById(R.id.btnEsc);
        Button btnLs = findViewById(R.id.btnLs);

        // History Back (Panah Atas)
        btnUp.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (cmdHistory.isEmpty()) return;
					if (historyIndex > 0) {
						historyIndex--;
						etInput.setText(cmdHistory.get(historyIndex));
						etInput.setSelection(etInput.getText().length());
					}
				}
			});

        // History Forward (Panah Bawah)
        btnDown.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (cmdHistory.isEmpty()) return;
					if (historyIndex < cmdHistory.size() - 1) {
						historyIndex++;
						etInput.setText(cmdHistory.get(historyIndex));
					} else {
						historyIndex = cmdHistory.size();
						etInput.setText("");
					}
					etInput.setSelection(etInput.getText().length());
				}
			});

        // Tombol ESC (Clear Input)
        btnEsc.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { etInput.setText(""); }
			});

        // Tombol LS (Shortcut command)
        btnLs.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					processCommand("ls");
					appendRawHtml("<font color='#00FFFF'><b>$</b></font> <font color='#FFFFFF'>ls</font><br>");
				}
			});
    }

    // === OTAK UTAMA (PROCESS COMMAND) ===
    private void processCommand(String cmd) {
        // 1. Simpan History
        if (!cmd.trim().isEmpty()) {
            cmdHistory.add(cmd);
            historyIndex = cmdHistory.size();
        }

        String[] parts = cmd.split(" ");
        String baseCmd = parts[0];

        // === FITUR STALKER ===
        if (baseCmd.equals("stalk")) {
            if (parts.length >= 3) {
                String platform = parts[1];
                String target = parts[2];
                stalkHelper.execute(platform, target);
            } else {
                formatAndAppendLog("Usage: stalk <ig|tiktok|ff|roblox> <username/id>");
            }
        }

        // === FITUR DEFCURL (MACRO) ===
        else if (baseCmd.equals("defcurl")) {
            if (parts.length >= 3) {
                String alias = parts[1];
                String url = parts[2];
                getSharedPreferences("TerminalMacros", MODE_PRIVATE)
                    .edit().putString(alias, url).apply();
                formatAndAppendLog("Shortcut disimpan: <font color='#00FF00'>" + alias + "</font>");
            } else {
                formatAndAppendLog("Usage: defcurl <nama> <url_tanda_{}>");
            }
        }

        // === FITUR CURL (SMART) ===
        else if (baseCmd.equals("curl")) {
            if (parts.length >= 2) {
                String target = parts[1];
                String savedUrl = getSharedPreferences("TerminalMacros", MODE_PRIVATE).getString(target, null);
                if (savedUrl != null) {
                    String finalUrl = savedUrl;
                    int argIndex = 2;
                    while (finalUrl.contains("{}") && argIndex < parts.length) {
                        finalUrl = finalUrl.replaceFirst("\\{\\}", parts[argIndex]);
                        argIndex++;
                    }
                    formatAndAppendLog("Running Task: " + target);
                    networkHelper.runCurl(finalUrl);
                } else {
                    networkHelper.runCurl(target);
                }
            } else {
                formatAndAppendLog("Usage: curl <link_atau_nama_tugas> [args]");
            }
        }

        // === FITUR ADDON ===
        else if (baseCmd.equals("import")) {
            if (parts.length >= 3 && parts[1].equals("addon")) {
                String zipPath = BASE_DIR + "/" + parts[2];
                if (new File(zipPath).exists()) addonHelper.installAddon(zipPath);
                else formatAndAppendLog("Error: File zip tidak ditemukan.");
            } else {
                formatAndAppendLog("Usage: import addon <namafile.zip>");
            }
        }
        else if (baseCmd.equals("run")) {
            if (parts.length >= 2) {
                addonHelper.runAddonScript(parts[1], parts);
            } else {
                formatAndAppendLog("Usage: run <nama_addon> [args]");
            }
        }

        // === FITUR NETWORK (SSH & HOST) ===
        else if (baseCmd.equals("ssh")) {
            if (parts.length >= 4) {
                networkHelper.runSSH(parts[1], parts[2], parts[3], combineArgs(parts,4));
            } else {
                formatAndAppendLog("Usage: ssh <user> <pass> <host> <cmd>");
            }
        }
        else if (baseCmd.equals("host")) {
            if (parts.length >= 3) {
                networkHelper.startWebServer(Integer.parseInt(parts[1]), cmd.substring(cmd.indexOf(parts[2])));
            } else {
                formatAndAppendLog("Usage: host <port> <path>");
            }
        }
        else if (cmd.equals("stop host")) {
            networkHelper.stopWebServer();
        }
		
		else if (baseCmd.equals("file")) {
            // Perintah ini langsung ngelempar semua argumen ke FileManager
            // Contoh input: file rename /sdcard/foto -n liburan_{nb} -f image
            fileManager.execute(parts); 
        }

        // === STORAGE & SYSTEM ===
        else if (baseCmd.equals("saf")) {
            try { startActivity(new Intent(this, SafActivity.class)); } 
            catch(Exception e) { formatAndAppendLog("Error: SafActivity belum dibuat."); }
        }
        else if (baseCmd.equals("clear")) {
            tabData.set(currentTabIndex, new StringBuilder());
            tvOutput.setText("");
            storageHelper.saveSessions();
        }
        else if (!cmd.trim().isEmpty()) {
            runLocalCommand(cmd);
        }
    }

    // === OUTPUT MANAGER ===
    public void appendRawHtml(String html) {
        if (currentTabIndex < tabData.size()) {
            StringBuilder sb = tabData.get(currentTabIndex);
            boolean butuhEnter = false;
            if (sb.length() > 0) {
                String buntut = sb.length() >= 4 ? sb.substring(sb.length() - 4) : sb.toString();
                if (!buntut.equalsIgnoreCase("<br>")) butuhEnter = true;
            }
            if (butuhEnter) html = "<br>" + html;
            sb.append(html);
        }
        tvOutput.append(Html.fromHtml(html, imageGetter, null));
        scrollToBottom();
        storageHelper.saveSessions();
    }

    public void formatAndAppendLog(String text) {
        text = text.replaceAll("((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])", "<font color='#4488FF'>$1</font>");
        text = text.replace("Error:", "<font color='#FF5555'>Error:</font>");
        text = text.replace("Success:", "<font color='#00FF00'>Success:</font>");
        appendRawHtml("<font color='#CCCCCC'>" + text + "</font><br>");
    }

    public void updateLastLine(String newHtml) {
        String fullHtml = "";
        if (currentTabIndex < tabData.size()) fullHtml = tabData.get(currentTabIndex).toString();

        // Hapus indikator loading lama kalau ada
        if (fullHtml.contains("Installing...")) fullHtml = fullHtml.substring(0, fullHtml.lastIndexOf("Installing..."));
        if (fullHtml.contains("Stalking")) { /* Opsional: Hapus teks loading stalking jika mau */ }

        fullHtml += newHtml;
        if (currentTabIndex < tabData.size()) tabData.set(currentTabIndex, new StringBuilder(fullHtml));

        tvOutput.setText(Html.fromHtml(fullHtml, imageGetter, null));
        scrollToBottom();
    }

    // === JSON COLORING ===
    public String formatJsonToTable(String json) throws JSONException {
        try {
            String formatted = "";
            if (json.startsWith("{")) formatted = new JSONObject(json).toString(2);
            else if (json.startsWith("[")) formatted = new JSONArray(json).toString(2);
            else return json; 

            formatted = formatted.replaceAll("\"([^\"]*)\":", "<font color='#4DD0E1'>\"$1\"</font>:"); 
            formatted = formatted.replaceAll(": \"([^\"]*)\"", ": <font color='#A5D6A7'>\"$1\"</font>"); 
            formatted = formatted.replaceAll(": (true|false)", ": <font color='#EA80FC'><b>$1</b></font>"); 
            formatted = formatted.replaceAll(": (\\d+)", ": <font color='#FFB74D'>$1</font>"); 
            formatted = formatted.replaceAll(": null", ": <font color='#EF5350'>null</font>"); 

            return formatted.replace(" ", "&nbsp;").replace("\n", "<br>");
        } catch (Exception e) { return json; }
    }

    // === JEMBATAN HELPERS ===
    public boolean safRename(String path, String newName) { return storageHelper.safRename(path, newName); }
    public boolean safMove(String src, String dest) { return storageHelper.safMove(src, dest); }

    // === TAB SYSTEM ===
    public void renderTabs() {
        tabContainer.removeAllViews();
        for (int i = 0; i < tabData.size(); i++) {
            final int index = i;
            Button btn = new Button(this);
            btn.setText(tabNames.get(i));
            btn.setTextSize(12);

            if (i == currentTabIndex) {
                btn.setTextColor(Color.GREEN);
                btn.setBackgroundColor(Color.parseColor("#444444"));
            } else {
                btn.setTextColor(Color.GRAY);
                btn.setBackgroundColor(Color.TRANSPARENT);
            }
            btn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { switchTab(index); } });
            btn.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { showRenameDialog(index); return true; } });
            tabContainer.addView(btn);
        }
        Button btnAdd = new Button(this);
        btnAdd.setText("+");
        btnAdd.setTextColor(Color.CYAN);
        btnAdd.setBackgroundColor(Color.TRANSPARENT);
        btnAdd.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { addNewTab(); }});
        tabContainer.addView(btnAdd);
    }

    private void showRenameDialog(final int index) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Atur Sesi");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(tabNames.get(index));
        builder.setView(input);
        builder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) {
					String newName = input.getText().toString();
					if(!newName.isEmpty()) { tabNames.set(index, newName); renderTabs(); storageHelper.saveSessions(); }
				}});
        builder.setNeutralButton("HAPUS", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { closeTab(index); }});
        builder.setNegativeButton("Batal", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { dialog.cancel(); }});
        builder.create().show();
    }

    private void closeTab(int index) {
        if (tabData.size() <= 1) {
            tabData.set(0, new StringBuilder()); tabNames.set(0, "SESI 1"); tvOutput.setText(""); switchTab(0);
        } else {
            tabData.remove(index); tabNames.remove(index);
            if (currentTabIndex >= tabData.size()) currentTabIndex = tabData.size() - 1;
            else if (index < currentTabIndex) currentTabIndex--;
            switchTab(currentTabIndex);
        }
        storageHelper.saveSessions();
    }

    public void switchTab(int index) {
        if (index < 0 || index >= tabData.size()) return;
        currentTabIndex = index;
        tvOutput.setText(Html.fromHtml(tabData.get(index).toString(), imageGetter, null));
        renderTabs();
        scrollToBottom();
    }

    private void addNewTab() {
        tabData.add(new StringBuilder("New Session...<br>"));
        tabNames.add("SESI " + (tabData.size()));
        switchTab(tabData.size() - 1);
        storageHelper.saveSessions();
    }

    // === UTILS ===
    private void scrollToBottom() {
        scrollView.post(new Runnable() { public void run() { scrollView.fullScroll(View.FOCUS_DOWN); focusInput(); }});
    }

    private void focusInput() {
        etInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private String combineArgs(String[] parts, int start) { 
        StringBuilder sb = new StringBuilder(); 
        for (int i = start; i < parts.length; i++) sb.append(parts[i]).append(" "); 
        return sb.toString(); 
    }

    private void runLocalCommand(final String cmd) { 
        new Thread(new Runnable() { public void run() { 
					try { 
						Process p = Runtime.getRuntime().exec(cmd); 
						java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream())); 
						String l; while((l=r.readLine())!=null) { 
							final String f=l; runOnUiThread(new Runnable() { public void run() { formatAndAppendLog(f); }}); 
						} 
					} catch(Exception e) {} 
				} }).start(); 
    }
}
