package com.lkl.androidtestassisttool.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class PublicIPUtil {
    public static void getPublicIp(Callback callback) {
        new Thread(() -> {
            URL infoUrl = null;
            InputStream inStream = null;
            String line = "";
            try {
                infoUrl = new URL("http://pv.sohu.com/cityjson?ie=utf-8");
                URLConnection connection = infoUrl.openConnection();
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                int responseCode = httpConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inStream = httpConnection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inStream, "utf-8"));
                    StringBuilder strber = new StringBuilder();
                    while ((line = reader.readLine()) != null)
                        strber.append(line + "\n");
                    inStream.close();
                    // 从反馈的结果中提取出IP地址
                    int start = strber.indexOf("{");
                    int end = strber.indexOf("}");
                    String json = strber.substring(start, end + 1);
                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        line = jsonObject.optString("cip");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                line = "";
            } finally {
                callback.onResult(line);
            }
        }).start();
    }

    public interface Callback {
        void onResult(String ip);
    }
}
