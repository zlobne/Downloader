package com.zlobne.downloader.lib;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


/*
    Simple download task. Rewrites if file exists
    Usage:
    int i = new DownloadTask.execute(url, outputPath).get()
    returns 0 if ok, 1 if err
 */

public class DownloadTask {

    private DownloadTaskListener listener;
    private DownloadTaskState state;

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
                state = new DownloadTaskState(0, url, fileName);

                int err;
                int count;
                try {
//                    String strUrl = URLEncoder.encode(url, "UTF-8");
//                    strUrl = strUrl.replace("%2F", "/");
//                    strUrl = strUrl.replace("%3A", ":");
                    state.setUrl(url);

                    URL url1 = new URL(url);
                    HttpURLConnection connection = (HttpURLConnection) url1.openConnection();
                    connection.connect();
                    if (connection.getResponseCode() == 200) {
                        // this will be useful so that you can show a typical 0-100%
                        // progress bar
                        state.setTotal(connection.getContentLength());
                        if (listener != null) {
                            listener.onProgressUpdate(state);
                        }
                        // download the file
                        InputStream input = new BufferedInputStream(url1.openStream(),
                                8192);

                        // Output stream
                        OutputStream output = new FileOutputStream(tmpFileName);

                        byte data[] = new byte[1024];

                        while ((count = input.read(data)) != -1) {
                            state.setCurrent(state.getCurrent() + count);
                            output.write(data, 0, count);
                            if (listener != null) {
                                listener.onProgressUpdate(state);
                            }
                        }

                        // flushing output
                        output.flush();

                        // closing streams
                        output.close();
                        input.close();
                        File from = new File(tmpFileName);
                        File to = new File(fileName);
                        if (!from.renameTo(to)) {
                            err = 1;
                            state.setCode(err);
                        }
                    } else {
                        err = 1;
                        state.setCode(err);
                    }
                    connection.disconnect();
                } catch (Exception e) {
                    err = 1;
                    state.setCode(err);
                }

                if (listener != null)
                    if (state.getCode() == 0) {
                        listener.onDownloadTaskComplete(state);
                    } else {
                        listener.onDownloadTaskError(state);
                    }
            }
        }).start();
    }

    public interface DownloadTaskListener {

        public void onDownloadTaskComplete(DownloadTaskState state);

        public void onDownloadTaskError(DownloadTaskState state);

        public void onProgressUpdate(DownloadTaskState state);

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
