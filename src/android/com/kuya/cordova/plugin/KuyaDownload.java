package com.kuya.cordova.plugin;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.file.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.content.Intent;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;


/**
 * This class echoes a string called from JavaScript.
 */
public class KuyaDownload extends CordovaPlugin {
    private static final String TAG = "kuyadownload";

    private DownloadManager downloadManager = null;

    HashMap<Long, String> downloads = new HashMap<Long, String>();
    private long download_id = 0;

    /**
     * @param context used to check the device version and DownloadManager information
     * @return true if the download manager is available
     */
    public static boolean isDownloadManagerAvailable(Context context) {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        try {
            if (action.equals("download")) {
                //Uri uri = Uri.parse(args.getString(0));
                String uri = args.getString(0);
                String dest = args.getString(1);
                JSONObject headers;
                JSONObject options;
                if (args.length() > 2) {
                    headers = args.getJSONObject(2);
                } else {
                    headers = new JSONObject();
                }

                if (args.length() > 3) {
                    options = args.getJSONObject(3);
                } else {
                    options = new JSONObject();
                }

                this.download2(uri, dest, headers, options, callbackContext);
            } else if (action.equals("get_file_path")) {
                this.get_file_path(callbackContext);
            } else {
                return false;
            }

            return true;
        } catch( JSONException e) {
            Log.e(TAG, "JSON Exception while parsing arguments " + e.toString() + " " + args.toString());
            throw e;
        }
    }

    private void get_file_path(CallbackContext context) {
        try {
            context.success(this.cordova.getActivity().getApplicationContext().getExternalFilesDir(null).toString());
        } catch( java.lang.NullPointerException e) {
            context.error("NPE");
        }
    }

    private class DownloadReceiver extends ResultReceiver {
        public long download_id;

        public DownloadReceiver(Handler handler, long download_id) {
            super(handler);

            this.download_id = download_id;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);

            try {
                JSONObject ev = new JSONObject()
                        .put("plugin", "kuyadownload");
                JSONObject data = new JSONObject()
                        .put("id", this.download_id);

                switch( resultCode ) {
                    case KuyaDownloadService.ERROR:
                        ev.put("type", "failed");
                        data.put("message", resultData.getString("error"));
                        break;
                    case KuyaDownloadService.COMPLETED:
                        ev.put("type", "finished");
                        data.put("status", resultData.getInt("status"));
                        data.put("headers", new JSONObject(resultData.getString("headers")));
                        downloads.remove(this.download_id);
                        break;
                    case KuyaDownloadService.PROGRESS:
                        ev.put("type", "progress");
                        data.put("downloaded", resultData.getLong("downloaded"));
                        data.put("total", resultData.getLong("total"));
                        break;
                    default:
                        Log.e(TAG, "unhandled resultCode! "+resultCode);
                        return;
                }

                ev.put("data", data);

                final String js = "javascript:window.cordova_plugin._emit("+ev.toString()+");";
                Log.i(TAG, "emitting " + js);

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl(js);
                    }
                });
            } catch( org.json.JSONException e ) {
                Log.e(TAG, "error sending json data "+e);
            }
        }
    }

    private void download2(String url, String local_dest, JSONObject headers, JSONObject options, CallbackContext context) {
        downloads.put(download_id, url);

        Intent intent = new Intent(cordova.getActivity(), KuyaDownloadService.class);
        intent.putExtra("url", url);

        File dest = new File(cordova.getActivity().getFilesDir(), local_dest);

        Log.i(TAG, "download() "+url+" "+dest.toString()+" "+headers.toString()+" "+options.toString());

        intent.putExtra("dest", dest.toString());
        intent.putExtra("headers", headers.toString());
        intent.putExtra("options", options.toString());
        intent.putExtra("receiver", new DownloadReceiver(new Handler(), download_id));
        cordova.getActivity().startService(intent);

        context.success((int) download_id);

        download_id ++;
    }

    private void download(
            Uri uri,
            String dest,
            JSONObject headers,
            JSONObject options,
            CallbackContext callbackContext
    ) {
        Log.i(TAG, "download() "+uri+" "+dest+" "+headers.toString()+" "+options.toString());

        if (this.downloadManager == null) {
            if (!isDownloadManagerAvailable(this.cordova.getActivity().getApplicationContext())) {
                callbackContext.error("download manager is not available");
                return;
            }

            Log.i(TAG, "setting download manager");
            this.downloadManager = (DownloadManager) cordova.getActivity().getSystemService(Context.DOWNLOAD_SERVICE);

            //set filter to only when download is complete and register broadcast receiver
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

            cordova.getActivity().getApplicationContext().registerReceiver(downloadReceiver, filter);
        }

        File dest_file = new File(dest);
        if( dest_file.exists() ) {
            Log.i(TAG, "deleting existing file");
            dest_file.delete();
        }
        String dest_dir = dest_file.getParent();
        String dest_fn = dest_file.getName();

        DownloadManager.Request request = new DownloadManager.Request(uri);

        Log.i(TAG, "downloading "+uri+" to " + dest);

        //Set the local destination for the downloaded file to a path within the application's external files directory
        request.setDestinationInExternalFilesDir(
                this.cordova.getActivity().getApplicationContext(),
                dest_dir,
                dest_fn
        );

        //request.setDestinationUri()

        Iterator<?> keys = headers.keys();
        while( keys.hasNext() ) {
            String key = (String)keys.next();
            try {
                Log.i(TAG, "header setting "+key+" to "+headers.get(key));
                request.addRequestHeader(key, "" + headers.get(key));
            } catch(org.json.JSONException e) {
                Log.e(TAG, "header failed a request header");
            }
        }

        if( options.has("title") ) {
            try {
                request.setTitle(options.getString("title"));
            } catch( JSONException e ) {
                //
            }
        }
        if( options.has("description") ) {
            //Set a description of this download, to be displayed in notifications (if enabled)
            try {
                request.setDescription(options.getString("description"));
            } catch( JSONException e ) {
                //
            }
        }

        //request.setNotificationVisibility(request.VISIBILITY_HIDDEN);

        //request.setVisibleInDownloadsUi()

        long download_id = downloadManager.enqueue(request);
        downloads.put(download_id, uri.toString());
        callbackContext.success((int)download_id);
    }

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long finished_id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if( !downloads.containsKey(finished_id) ) {
                Log.e(TAG, "unknown download completed");
                return;
            }

            Log.i(TAG, "download complete! " + downloadManager.getUriForDownloadedFile(finished_id));
            try {
                JSONObject js_event = new JSONObject()
                        .put("type", "finished")
                        .put("plugin", "kuyadownload")
                        .put("data", new JSONObject()
                            .put("id", finished_id)
                            .put("headers", new JSONObject()
                                .put("Content-Type", downloadManager.getMimeTypeForDownloadedFile(finished_id))
                            )
                            .put("status", 200)
                            .put("download_path", downloadManager.getUriForDownloadedFile(finished_id))
                        )
                 ;

                String js = "javascript:window.cordova_plugin._emit("+js_event.toString()+");";
                Log.i(TAG, "emitting "+js);
                webView.loadUrl(js);
            } catch( JSONException e ) {
                Log.e(TAG, "error emitting download complete event! "+e);
            }

            downloads.remove(finished_id);
        }
    };
}


