/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.tasks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.odk.collect.android.activities.InstanceUploaderList;
import org.odk.collect.android.listeners.InstanceUploaderListener;

import android.os.AsyncTask;
import android.util.Log;
import applab.client.HttpHelpers;
import applab.client.farmerregistration.FarmerRegistrationController;

/**
 * Background task for uploading completed forms.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class InstanceUploaderTask extends AsyncTask<String, Integer, ArrayList<String>> {

    private static String t = "InstanceUploaderTask";
    // private static long MAX_BYTES = 1048576 - 1024; // 1MB less 1KB overhead
    InstanceUploaderListener mStateListener;
    String mUrl;
    private static final int CONNECTION_TIMEOUT = 30000;

    public static final String SURVEY_LOCATION_HEADER = "x-applab-survey-location";
    public static final String INTERVIEWEEID_HEADER = "x-applab-interviewee-id";

    public void setUploadServer(String newServer) {
        mUrl = newServer;
    }

    @Override
    protected ArrayList<String> doInBackground(String... values) {
        // First post any pending farmerRegistrations
        FarmerRegistrationController farmerRegController = new FarmerRegistrationController();
        farmerRegController.postFarmerRegistrationData(mUrl.replace("/submission", "")); // TODO: Need better way of
                                                                                         // getting/setting the URL.
                                                                                         // Can't
                                                                                         // FarmerRegistrationController
                                                                                         // take care of it?

        ArrayList<String> uploadedIntances = new ArrayList<String>();
        int instanceCount = values.length;

        for (int i = 0; i < instanceCount; i++) {
            publishProgress(i + 1, instanceCount);

            // Configure connection
            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
            HttpConnectionParams.setSoTimeout(params, CONNECTION_TIMEOUT);
            HttpClientParams.setRedirecting(params, false);

            // Setup client
            DefaultHttpClient httpclient = new DefaultHttpClient(params);
            HttpPost httppost = new HttpPost(mUrl);
            HttpHelpers.addCommonHeaders(httppost);

            // Get intervieweeId, location and instance file
            String parameters = values[i];
            String[] paramArray = parameters.split(InstanceUploaderList.PARAMETER_SEPARATOR);
            String intervieweeId = "";
            String location = "";
            String path = "";
            if (paramArray.length == 3) {
                intervieweeId = paramArray[0].toString().trim();
                location = paramArray[1].toString().trim();
                path = paramArray[2].toString().trim();
            }
            else {
                location = paramArray[0].toString().trim();
                path = paramArray[1].toString().trim();
            }
            httppost.addHeader(SURVEY_LOCATION_HEADER, location);
            httppost.addHeader(INTERVIEWEEID_HEADER, intervieweeId);

            File file = new File(path);

            // Find all files in parent directory
            File[] files = file.getParentFile().listFiles();
            if (files == null) {
                Log.e(t, "no files to upload");
                cancel(true);
            }

            // Restore the path
            values[i] = path;

            // Mime post
            MultipartEntity entity = new MultipartEntity();
            for (int j = 0; j < files.length; j++) {
                File f = files[j];
                FileBody fb;
                if (f.getName().endsWith(".xml")) {
                    fb = new FileBody(f, "text/xml");
                    entity.addPart("xml_submission_file", fb);
                    Log.i(t, "added xml file " + f.getName());
                }
                else if (f.getName().endsWith(".jpg")) {
                    fb = new FileBody(f, "image/jpeg");
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added image file " + f.getName());
                }
                else if (f.getName().endsWith(".3gpp")) {
                    fb = new FileBody(f, "audio/3gpp");
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added audio file " + f.getName());
                }
                else if (f.getName().endsWith(".3gp")) {
                    fb = new FileBody(f, "video/3gpp");
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added video file " + f.getName());
                }
                else if (f.getName().endsWith(".mp4")) {
                    fb = new FileBody(f, "video/mp4");
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added video file " + f.getName());
                }
                else {
                    Log.w(t, "unsupported file type, not adding file: " + f.getName());
                }
            }
            httppost.setEntity(entity);

            // Pass the interviewee back as a post variable
            params.setParameter("intervieweeId", intervieweeId);
            httppost.setParams(params);

            // Prepare response and return uploaded
            HttpResponse response = null;
            try {
                response = httpclient.execute(httppost);
            }
            catch (ClientProtocolException e) {
                e.printStackTrace();
                return uploadedIntances;
            }
            catch (IOException e) {
                e.printStackTrace();
                return uploadedIntances;
            }
            catch (IllegalStateException e) {
                e.printStackTrace();
                return uploadedIntances;
            }

            // check response.
            // TODO: This isn't handled correctly.
            String serverLocation = null;
            Header[] h = response.getHeaders("Location");
            if (h != null && h.length > 0) {
                serverLocation = h[0].getValue();
            }
            else {
                // something should be done here...
                Log.e(t, "Location header was absent");
            }
            int responseCode = response.getStatusLine().getStatusCode();
            Log.e(t, "Response code:" + responseCode);

            // verify that your response came from a known server
            if (serverLocation != null && mUrl.contains(serverLocation) && responseCode == 201) {
                uploadedIntances.add(values[i]);
            }

        }

        return uploadedIntances;
    }

    @Override
    protected void onPostExecute(ArrayList<String> value) {
        synchronized (this) {
            if (mStateListener != null) {
                mStateListener.uploadingComplete(value);
            }
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        synchronized (this) {
            if (mStateListener != null) {
                // update progress and total
                mStateListener.progressUpdate(values[0].intValue(), values[1].intValue());
            }
        }
    }

    public void setUploaderListener(InstanceUploaderListener sl) {
        synchronized (this) {
            mStateListener = sl;
        }
    }
}
