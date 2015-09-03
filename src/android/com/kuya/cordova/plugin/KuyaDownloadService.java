package com.kuya.cordova.plugin;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KuyaDownloadService extends IntentService {
    public static final int PROGRESS = 8344;
    public static final int COMPLETED = 8345;
    public static final int ERROR = 8346;

    public static final String TAG = "kuyadownloadservice";

    public KuyaDownloadService() {
        super("KuyaDownloadService");
    }
    @Override
    protected void onHandleIntent(Intent intent) {
        String string_url = intent.getStringExtra("url");
        String dest = intent.getStringExtra("dest");
        JSONObject headers;
        JSONObject options;
        try {
            headers = new JSONObject(intent.getStringExtra("headers"));
        } catch( org.json.JSONException e ) {
            headers = new JSONObject();
        }
        try {
            options = new JSONObject(intent.getStringExtra("options"));
        } catch( org.json.JSONException e ) {
            options = new JSONObject();
        }

        ResultReceiver receiver = (ResultReceiver) intent.getParcelableExtra("receiver");
        try {
            URL url = new URL(string_url);
            URLConnection connection = url.openConnection();

            Iterator<?> keys = headers.keys();
            while( keys.hasNext() ) {
                String key = (String)keys.next();
                try {
                    Log.d(TAG, "setting header "+key+" to "+headers.get(key));
                    connection.addRequestProperty(key, headers.getString(key));
                } catch(org.json.JSONException e) {
                    Log.e(TAG, "error setting request header");
                }
            }

            connection.connect();

            Log.i(TAG, "intent start "+url+" "+dest);

            int fileLength = connection.getContentLength();

            Bundle resultData;

            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new FileOutputStream(dest);

            byte data[] = new byte[1024];
            long total = 0;
            int count;

            long last_progress_time = System.currentTimeMillis();
            long now;

            while ((count = input.read(data)) != -1) {
                total += count;
                output.write(data, 0, count);

                now = System.currentTimeMillis();
                if( now - last_progress_time > 1000 ) {
                    last_progress_time = now;
                    resultData = new Bundle();
                    resultData.putLong("downloaded", total);
                    resultData.putLong("total", fileLength);
                    receiver.send(PROGRESS, resultData);
                }
            }

            output.flush();
            output.close();
            input.close();

            resultData = new Bundle();

            if ( connection instanceof HttpURLConnection)
            {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                resultData.putInt("status", httpConnection.getResponseCode());

                try {
                    JSONObject response_headers = new JSONObject();
                    Map<String, List<String>> headerFields = httpConnection.getHeaderFields();
                    Set<String> headerFieldsSet = headerFields.keySet();
                    Iterator<String> hearerFieldsIter = headerFieldsSet.iterator();
                    while (hearerFieldsIter.hasNext()) {
                        String headerFieldKey = hearerFieldsIter.next();
                        if( headerFieldKey == null ) {
                            continue;
                        }
                        List<String> headerFieldValue = headerFields.get(headerFieldKey);
                        StringBuilder sb = new StringBuilder();
                        for (String value : headerFieldValue) {
                            sb.append(value);
                            sb.append("");
                        }
                        response_headers.put(headerFieldKey, sb.toString());

                    }

                    resultData.putString("headers", response_headers.toString());
                } catch( org.json.JSONException e) {
                    Log.e(TAG, e.toString());
                }
            }
            receiver.send(COMPLETED, resultData);

        } catch (IOException e) {
            e.printStackTrace();
            Bundle resultData = new Bundle();
            resultData.putString("error", e.toString());
            receiver.send(ERROR, resultData);
        }
    }
}
