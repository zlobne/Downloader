package com.zlobne.downloader.lib;


import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;


/*
    Simple download task. Rewrites if file exists
    Usage:
    int i = new DownloadTask.execute(url, outputPath).get()
    returns 0 if ok, 1 if err
 */

public class DownloadTask {

    private boolean cancel = false;

    private boolean kill = false;

    private DownloadTaskListener listener;
    private DownloadTaskState state;

    private InputStream input;
    private RandomAccessFile output;

    public DownloadTask() { }

    public DownloadTask(DownloadTaskListener listener) {
        this.listener = listener;
    }

    public void execute(String... strings) {
        final String url = strings[0];
        final String fileName = strings[1];
        new Thread(new Runnable() {
            @Override
            public void run() {
                String tmpFileName = fileName + ".tmp";
                String zdlFileName = fileName + ".zdl";
                File zdlFile = new File(zdlFileName);
                File tmpFile = new File(tmpFileName);

//                long fileSize = 0;
                long offset = 0;
                String ifRange = "";

                state = new DownloadTaskState(0, url, fileName);

                int err;
                int count;
                try {
                    state.setUrl(url);

                    if (zdlFile.exists() && tmpFile.exists()) {
                        BufferedReader reader = new BufferedReader(new FileReader(zdlFile));
                        JSONObject jsonObject = new JSONObject(reader.readLine());
//                        fileSize = jsonObject.getLong("fileSize");
                        ifRange = jsonObject.getString("If-Range");
                        offset = tmpFile.length();
                        reader.close();
                    }

                    URL url1 = new URL(url);
                    HttpURLConnection connection = (HttpURLConnection) url1.openConnection();
                    if (offset > 0) {
                        connection.setRequestProperty("Range", "bytes=" + (offset) + "-");
                    }
                    connection.setRequestProperty("If-Range", ifRange);
//                    connection.setConnectTimeout(5000);
                    connection.connect();

                    ifRange = connection.getHeaderField("ETag");
                    if (ifRange == null || ifRange.equals("")) {
                        ifRange = connection.getHeaderField("Last-Modified");
                    }

                    Log.d(Constants.TAG, connection.getResponseCode() + " " + connection.getResponseMessage());

                    Map<String, List<String>> map = connection.getHeaderFields();
                    Log.d(Constants.TAG, "header fields: " + map.toString());

                    long actualSize = connection.getContentLength();
                    state.setTotal(actualSize);

                    if (listener != null) {
                        listener.onProgressUpdate(state);
                    }

                    input = new BufferedInputStream(connection.getInputStream());

//                    fileSize = actualSize;

                    output = new RandomAccessFile(tmpFileName, "rw");

                    if (offset > 0) {
                        output.seek(offset);
                    }

                    byte data[] = new byte[1024];

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (!kill && listener != null) {
                                listener.onProgressUpdate(state);
                                try {
                                    Thread.sleep(700);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).start();

                    while ((count = input.read(data)) > 0 && !(cancel)) {
                        state.setCurrent(state.getCurrent() + count);
                        offset += count;
                        output.write(data, 0, count);
//                        if (listener != null) {
//                            listener.onProgressUpdate(state);
//                        }
                    }

                    output.close();
                    input.close();
                    File from = new File(tmpFileName);
                    File to = new File(fileName);
                    if (!from.renameTo(to)) {
                        err = 2;
                        state.setCode(err);
                    }
                    zdlFile.delete();
                    connection.disconnect();
                } catch (Exception e) {
                    try {
                        input.close();
                        output.close();
                    } catch (Exception e1) {
                        Log.d(Constants.TAG, "" + e.getMessage());
                    }
                    Log.d(Constants.TAG, "" + e.getMessage());
                    err = 1;
                    state.setCode(err);
                }

                kill = true;

                if (listener != null)
                    if (!cancel) {
                        if (state.getCode() == 0) {
                            listener.onDownloadTaskComplete(state);
                        } else {
                            listener.onDownloadTaskError(state);
                            saveStatus(zdlFile, ifRange);
                        }
                    } else {
                        listener.onDownloadTaskCancel(state);
                        saveStatus(zdlFile, ifRange);
                    }
            }
        }).start();
    }

    private void saveStatus(File zdlFile, String ifRange) {
        try {
            zdlFile.delete();
            OutputStream outputStream = new FileOutputStream(zdlFile, false);
            JSONObject jsonObject = new JSONObject();
//                            jsonObject.put("fileSize", fileSize);
            jsonObject.put("If-Range", ifRange);
            outputStream.write(jsonObject.toString().getBytes());
            outputStream.flush();
            outputStream.close();
            Log.d(Constants.TAG, "state saved");
        } catch (IOException e1) {
            Log.d(Constants.TAG, "IO exception " + e1.getMessage());
            e1.printStackTrace();
        } catch (JSONException e1) {
            Log.d(Constants.TAG, "JSON exception " + e1.getMessage());
            e1.printStackTrace();
        }
    }

    public void cancel () { cancel = true; }

    public interface DownloadTaskListener {

        public void onDownloadTaskComplete(DownloadTaskState state);

        public void onDownloadTaskError(DownloadTaskState state);

        public void onProgressUpdate(DownloadTaskState state);

        public void onDownloadTaskCancel(DownloadTaskState state);

    }

    public class DownloadTaskState {
        private int code;
        private String url;
        private String nameFile;
        private long total;
        private long current;

        public DownloadTaskState() { }

        public DownloadTaskState(int code, String url, String nameFile) {
            setCode(code);
            setUrl(url);
            setNameFile(nameFile);
            setCurrent(0);
            setTotal(0);
        }

        public void setCode(int code) {
            this.code = code;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public void setNameFile(String nameFile) {
            this.nameFile = nameFile;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public void setCurrent(long current) {
            this.current = current;
        }

        public int getCode() {
            return this.code;
        }

        public String getUrl() {
            return this.url;
        }

        public String getNameFile() {
            return this.nameFile;
        }

        public long getTotal() {
            return total;
        }

        public long getCurrent() {
            return current;
        }
    }
}
