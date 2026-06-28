package com.ebookdrive.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String BASE = "https://ebookdrive.vercel.app";
    private static final String PREFS = "ebd";
    private static final String KEY_CODE = "code";

    private EditText codeInput;
    private TextView status;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private final List<String> displayRows = new ArrayList<>();
    private final List<String> fileNames = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        codeInput = findViewById(R.id.code);
        status = findViewById(R.id.status);
        listView = findViewById(R.id.list);
        Button loadBtn = findViewById(R.id.loadBtn);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayRows);
        listView.setAdapter(adapter);

        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_CODE, "");
        if (!TextUtils.isEmpty(saved)) {
            codeInput.setText(saved);
        }

        requestStorageIfNeeded();

        loadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String code = codeInput.getText().toString().trim();
                if (TextUtils.isEmpty(code)) {
                    status.setText("코드를 입력하세요.");
                    return;
                }
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(KEY_CODE, code).apply();
                loadList(code);
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String code = codeInput.getText().toString().trim();
                download(fileNames.get(position), code);
            }
        });

        if (!TextUtils.isEmpty(saved)) {
            loadList(saved);
        }
    }

    private void requestStorageIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    private void loadList(final String code) {
        status.setText("불러오는 중...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> rows = new ArrayList<>();
                final List<String> names = new ArrayList<>();
                String err = null;
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(BASE + "/api/list?code=" + enc(code));
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    int sc = conn.getResponseCode();
                    if (sc == 401) {
                        err = "코드가 올바르지 않습니다.";
                    } else if (sc != 200) {
                        err = "오류: " + sc;
                    } else {
                        String body = readAll(conn.getInputStream());
                        JSONObject obj = new JSONObject(body);
                        JSONArray arr = obj.getJSONArray("files");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject f = arr.getJSONObject(i);
                            String name = f.getString("name");
                            long size = f.optLong("size", -1);
                            names.add(name);
                            rows.add(name + (size >= 0 ? "   (" + fmtSize(size) + ")" : ""));
                        }
                    }
                } catch (Exception e) {
                    err = "네트워크 오류: " + e.getMessage();
                } finally {
                    if (conn != null) conn.disconnect();
                }
                final String fErr = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fErr != null) {
                            status.setText(fErr);
                            return;
                        }
                        fileNames.clear();
                        fileNames.addAll(names);
                        displayRows.clear();
                        displayRows.addAll(rows);
                        adapter.notifyDataSetChanged();
                        status.setText(rows.isEmpty()
                                ? "파일이 없습니다."
                                : "파일을 누르면 Download 폴더에 저장됩니다.");
                    }
                });
            }
        }).start();
    }

    private void download(String name, String code) {
        try {
            String url = BASE + "/api/download?name=" + enc(name) + "&code=" + enc(code);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(name);
            req.setDescription("ebook-drive");
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            req.allowScanningByMediaScanner();
            dm.enqueue(req);
            Toast.makeText(this, "다운로드 시작: " + name, Toast.LENGTH_SHORT).show();
            status.setText("다운로드: Download/" + name);
        } catch (Exception e) {
            Toast.makeText(this, "다운로드 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static String fmtSize(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return (n / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", n / 1024.0 / 1024.0);
    }
}
