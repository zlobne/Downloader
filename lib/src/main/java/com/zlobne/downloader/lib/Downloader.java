package com.zlobne.downloader.lib;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.Stack;

/**
 * Created by Anton Prozorov on 31.03.14.
 */
public class Downloader {

    private static Downloader self;
    private Context context;

    private static final int MAX_DOWNLOADS = 2;

    private boolean allCompleted = false;
    private boolean firstRun = true;
    private boolean stop = false;
    private boolean pause = false;

    private int downloadsCount = 0;

    private Stack<Download> downloads;
    private ArrayList<String> allDownloads;

    protected Downloader(Context context) {
        this.context = context;
        downloads = new Stack<Download>();
        allDownloads = new ArrayList<String>();
        Log.d("Downloader", "started");
        start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                allDownloads.clear();
                try {
                    Thread.sleep(1000 * 60 * 5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static Downloader instance(Context context) {
        if (self == null) {
            self = new Downloader(context);
        }
        return self;
    }

    public static Downloader instance() {
        if (self == null) {
            Log.d("Downloader", "Can't create new Downloader without context");
        }
        return self;
    }

    public void enqueue(String url, String dest) {
        Download download = new Download(url, dest);
        if (!allDownloads.contains(dest)) {
            downloads.push(download);
            allDownloads.add(dest);
            Log.d("Downloader", "enqueue");
            allCompleted = false;
            firstRun = false;
        } else {
            Log.d("Downloader", "already got this");
        }
    }

    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (!pause && downloadsCount < MAX_DOWNLOADS && !allCompleted && !firstRun) {
                        if (!downloads.isEmpty()) {
                            downloadsCount++;
                            Download download = downloads.pop();
                            Log.d("Downloader", "getting " + download.getDest());
                            new DownloadTask(new DownloadTask.DownloadTaskListener() {
                                @Override
                                public void onDownloadTaskComplete(DownloadTask.DownloadTaskState state) {
                                    downloadsCount--;
                                    Log.d("Downloader", downloadsCount + " downloads, empty list = " + downloads.isEmpty());
                                    Log.d("Downloader", "got " + state.getNameFile());
                                }

                                @Override
                                public void onDownloadTaskError(DownloadTask.DownloadTaskState state) {
                                    downloadsCount--;
                                    Log.d("Downloader", "error " + state.getNameFile() + "; state " + state.getCode());
                                }

                                @Override
                                public void onProgressUpdate(DownloadTask.DownloadTaskState state) {

                                }
                            }).execute(download.getUrl(), download.getDest());
//                            downloads.remove(0);
                        } else {
                            if (downloadsCount == 0) {
                                allCompleted = true;
                                context.sendBroadcast(new Intent(Constants.INTENT_ACTION));
                                Log.d("Downloader", "all downloads completed");
                            }
                        }
                    }
                }

            }
        }).start();
    }

    public void pause() {
        pause = true;
    }

    public void unpause() {
        pause = false;
    }

    public void stop() {
        stop = true;
    }

    private class Download {

        private String url;
        private String dest;

        public Download(String url, String dest) {
            this.url = url;
            this.dest = dest;
        }

        public String getUrl() {
            return url;
        }

        public String getDest() {
            return dest;
        }
    }
}
