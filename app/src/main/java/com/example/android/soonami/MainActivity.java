/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.soonami;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Displays information about a single earthquake.
 */
public class MainActivity extends AppCompatActivity {

    /** Tag for the log messages */
    public static final String LOG_TAG = MainActivity.class.getSimpleName();

    /** URL to query the USGS dataset for earthquake information */
    private static final String USGS_REQUEST_URL =
            "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2012-01-01&endtime=2012-12-01&minmagnitude=6";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TaskRunner runner = new TaskRunner();
        runner.executeAsync(new NetworkTask());
    }

    public static class TaskRunner {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Executor executor = Executors.newCachedThreadPool();

        public <Event> void executeAsync(CustomCallable<com.example.android.soonami.Event> callable) {
            try {
//                callable.setUiForLoading();
                executor.execute(new RunnableTask<com.example.android.soonami.Event>(handler, callable));
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error with executeAsync", e);
            }
        }

        public static class RunnableTask<Event> implements Runnable{
            private final Handler handler;
            private final CustomCallable<com.example.android.soonami.Event> callable;

            public RunnableTask(Handler handler,
                                CustomCallable<com.example.android.soonami.Event> callable) {
                this.handler = handler;
                this.callable = callable;
            }

            @Override
            public void run() {
                try {
                    final com.example.android.soonami.Event result = callable.call();
                    handler.post(new RunnableTaskForHandler(callable, result));
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error with running task", e);
                }
            }
        }

        public static class RunnableTaskForHandler<Event> implements Runnable{

            private final CustomCallable<com.example.android.soonami.Event> callable;
            private final com.example.android.soonami.Event result;

            public RunnableTaskForHandler(CustomCallable<com.example.android.soonami.Event> callable,
                                          com.example.android.soonami.Event result) {
                this.callable = callable;
                this.result = result;
            }

            @Override
            public void run() {
                callable.setDataAfterLoading(result);
            }
        }
    }

    private interface CustomCallable<Event> extends Callable<com.example.android.soonami.Event> {
        void setDataAfterLoading(com.example.android.soonami.Event result);
//        void setUiForLoading();
    }

    public abstract class BaseTask<Event> implements CustomCallable<com.example.android.soonami.Event> {
//        @Override
//        public void setUiForLoading() {
//
//        }

        @Override
        public void setDataAfterLoading(com.example.android.soonami.Event result) {
            if (result == null) {
                return;
            }
            updateUi(result);
        }

        @Override
        public com.example.android.soonami.Event call() throws Exception {
            return null;
        }
    }

    public class NetworkTask extends BaseTask<Event> {

        //        private final iOnDataFetched listener;//listener in fragment that shows and hides ProgressBar
//        public NetworkTask(iOnDataFetched onDataFetchedListener) {
        public NetworkTask() {
//            this.listener = onDataFetchedListener;
        }

        @Override
        public Event call() throws Exception {
            // Create URL object
            URL url = createUrl(USGS_REQUEST_URL);

            // Perform HTTP request to the URL and receive a JSON response back
            String jsonResponse = "";
            try {
                jsonResponse = makeHttpRequest(url);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error with getting JSON response", e);
            }

            // Extract relevant fields from the JSON response and create an {@link Event} object
            return (Event) extractFeatureFromJson(jsonResponse);
        }

        /**
         * Returns new URL object from the given string URL.
         */
        private URL createUrl(String stringUrl) {
            URL url = null;
            try {
                url = new URL(stringUrl);
            } catch (MalformedURLException exception) {
                Log.e(LOG_TAG, "Error with creating URL", exception);
                return null;
            }
            return url;
        }

        /**
         * Make an HTTP request to the given URL and return a String as the response.
         */
        private String makeHttpRequest(URL url) throws IOException {
            String jsonResponse = "";
            HttpURLConnection urlConnection = null;
            InputStream inputStream = null;
            try {
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setReadTimeout(10000 /* milliseconds */);
                urlConnection.setConnectTimeout(15000 /* milliseconds */);
                urlConnection.connect();
                inputStream = urlConnection.getInputStream();
                jsonResponse = readFromStream(inputStream);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Problem with making HTTP request", e);
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (inputStream != null) {
                    // function must handle java.io.IOException here
                    inputStream.close();
                }
            }
            return jsonResponse;
        }

        /**
         * Convert the {@link InputStream} into a String which contains the
         * whole JSON response from the server.
         */
        private String readFromStream(InputStream inputStream) throws IOException {
            StringBuilder output = new StringBuilder();
            if (inputStream != null) {
                InputStreamReader inputStreamReader =
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader);
                String line = reader.readLine();
                while (line != null) {
                    output.append(line);
                    line = reader.readLine();
                }
            }
            return output.toString();
        }

        /**
         * Return an {@link Event} object by parsing out information
         * about the first earthquake from the input earthquakeJSON string.
         */
        private Event extractFeatureFromJson(String earthquakeJSON) {
            try {
                JSONObject baseJsonResponse = new JSONObject(earthquakeJSON);
                JSONArray featureArray = baseJsonResponse.getJSONArray("features");

                // If there are results in the features array
                if (featureArray.length() > 0) {
                    // Extract out the first feature (which is an earthquake)
                    JSONObject firstFeature = featureArray.getJSONObject(0);
                    JSONObject properties = firstFeature.getJSONObject("properties");

                    // Extract out the title, time, and tsunami values
                    String title = properties.getString("title");
                    long time = properties.getLong("time");
                    int tsunamiAlert = properties.getInt("tsunami");

                    // Create a new {@link Event} object
                    return new Event(title, time, tsunamiAlert);
                }
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Problem parsing the earthquake JSON results", e);
            }
            return null;
        }

//        @Override
//        public void setUiForLoading() {
//            listener.showProgressBar();
//        }
//
//        @Override
//        public void setDataAfterLoading(Event result) {
//            listener.setDataInPageWithResult(result);
//            listener.hideProgressBar();
//        }
    }

    /**
     * Update the screen to display information from the given {@link Event}.
     */
    private void updateUi(Event earthquake) {
        // Display the earthquake title in the UI
        TextView titleTextView = (TextView) findViewById(R.id.title);
        titleTextView.setText(earthquake.title);

        // Display the earthquake date in the UI
        TextView dateTextView = (TextView) findViewById(R.id.date);
        dateTextView.setText(getDateString(earthquake.time));

        // Display whether or not there was a tsunami alert in the UI
        TextView tsunamiTextView = (TextView) findViewById(R.id.tsunami_alert);
        tsunamiTextView.setText(getTsunamiAlertString(earthquake.tsunamiAlert));
    }

    /**
     * Returns a formatted date and time string for when the earthquake happened.
     */
    private String getDateString(long timeInMilliseconds) {
        SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy 'at' HH:mm:ss z",
                Locale.getDefault());
        return formatter.format(timeInMilliseconds);
    }

    /**
     * Return the display string for whether or not there was a tsunami alert for an earthquake.
     */
    private String getTsunamiAlertString(int tsunamiAlert) {
        switch (tsunamiAlert) {
            case 0:
                return getString(R.string.alert_no);
            case 1:
                return getString(R.string.alert_yes);
            default:
                return getString(R.string.alert_not_available);
        }
    }

//    public interface iOnDataFetched{
//        void showProgressBar();
//        void hideProgressBar();
//        void setDataInPageWithResult(Event result);
//    }
}
