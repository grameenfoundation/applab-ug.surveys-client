/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.activities;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.odk.collect.android.database.FileDbAdapter;
import org.odk.collect.android.logic.GlobalConstants;
import org.odk.collect.android.preferences.ServerPreferences;
import org.odk.collect.android.utilities.FileUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import applab.client.ApplabActivity;
import applab.client.BrowserActivity;
import applab.client.BrowserResultDialog;
import applab.client.Handset;
import applab.client.controller.FarmerRegistrationController;
import applab.surveys.client.R;

/**
 * Responsible for displaying buttons to launch the major activities. Launches some activities based on returns of
 * others.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class MainMenuActivity extends Activity implements Runnable {

    // request codes for returning chosen form to main menu.
    private static final int FORM_CHOOSER = 0;
    private static final int INSTANCE_CHOOSER_TABS = 1;
    private static final int INSTANCE_UPLOADER = 2;
    private static final int REGISTRATION_CODE = 3;
    private static final int FORGOT_ID_CODE = 4;

    // menu options
    private static final int MENU_PREFERENCES = Menu.FIRST;

    // buttons
    private Button mEnterDataButton;
    private Button mManageFilesButton;
    private Button mSendDataButton;
    private Button mReviewDataButton;
    private Button registerFarmerButton;
    private Button forgotIdButton;

    private EditText farmerNameEditBox;

    // counts for buttons
    private static int mSavedCount;
    private static int mCompletedCount;
    private static int mAvailableCount;
    private static int mFormsCount;

    private FarmerRegistrationController farmerRegController;

    private int requestCode;
    private ProgressDialog progressDialog;
    private String errorMessage;
    private static final int PROGRESS_DIALOG = 1;

    public MainMenuActivity() {
        this.farmerRegController = new FarmerRegistrationController();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_RIGHT_ICON);
        setContentView(R.layout.main_menu);
        setFeatureDrawableResource(Window.FEATURE_RIGHT_ICON, R.drawable.notes);
        setTitle(getString(R.string.app_name) + " > "
                + getString(R.string.main_menu));

        this.farmerNameEditBox = (EditText)findViewById(R.id.id_field);
        this.farmerNameEditBox
                .setFilters(new InputFilter[] { getFarmerInputFilter() });
        // enter data button. expects a result.
        mEnterDataButton = (Button)findViewById(R.id.enter_data);
        mEnterDataButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                String farmerName = farmerNameEditBox.getText().toString()
                        .trim();
                if (farmerName.length() > 0) {
                    if (checkID(farmerName)) {
                        GlobalConstants.intervieweeName = farmerName;
                        tryOpenFormChooser();
                    }
                    else {
                        showTestSurveyDialog();
                    }
                }
                else {
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Farmer\'s ID cannot be empty", Toast.LENGTH_SHORT);
                    toast.show();
                }
            }
        });

        registerFarmerButton = (Button)findViewById(R.id.register_farmer_button);
        registerFarmerButton.setText("Register New Farmer");
        registerFarmerButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                onRequestBrowserIntentButtonClick("getFarmerRegistrationForm",
                        REGISTRATION_CODE);
            }
        });

        forgotIdButton = (Button)findViewById(R.id.forgot_id_button);
        forgotIdButton.setText("Forgot Farmer's ID");
        forgotIdButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                onRequestBrowserIntentButtonClick("findFarmerId",
                        FORGOT_ID_CODE);
            }
        });

        // review data button. expects a result.
        mReviewDataButton = (Button)findViewById(R.id.review_data);
        mReviewDataButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if ((mSavedCount + mCompletedCount) == 0) {
                    Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.no_items_error,
                                    getString(R.string.review)),
                                    Toast.LENGTH_SHORT).show();
                }
                else {
                    Intent i = new Intent(getApplicationContext(),
                            InstanceChooserTabs.class);
                    startActivityForResult(i, INSTANCE_CHOOSER_TABS);
                }

            }
        });

        // send data button. expects a result.
        mSendDataButton = (Button)findViewById(R.id.send_data);
        mSendDataButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mCompletedCount == 0) {
                    Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.no_items_error,
                                    getString(R.string.send)),
                                    Toast.LENGTH_SHORT).show();
                }
                else {
                    Intent i = new Intent(getApplicationContext(),
                            InstanceUploaderList.class);
                    startActivityForResult(i, INSTANCE_UPLOADER);
                }

            }
        });

        // manage forms button. no result expected.
        mManageFilesButton = (Button)findViewById(R.id.manage_forms);
        mManageFilesButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(),
                        FileManagerTabs.class);
                startActivity(i);
            }
        });

        // Initialize Applab Specific stuff (we do it here because this is the entry activity for ODK)
        // Allows us to access some variables like Handset and version
        ApplabActivity.initialize(this);

        // Set the app version
        ApplabActivity.setAppVersion(this.getString(R.string.app_version));
    }

    private void tryOpenFormChooser() {
        // make sure we haven't added forms
        ArrayList<String> forms = FileUtils
                .getFilesAsArrayList(GlobalConstants.FORMS_PATH);
        if (forms != null) {
            mFormsCount = forms.size();
        }
        else {
            mFormsCount = 0;
        }

        if (mFormsCount == 0 && mAvailableCount == 0) {
            Toast.makeText(
                    getApplicationContext(),
                    getString(R.string.no_items_error,
                            getString(R.string.enter)), Toast.LENGTH_SHORT)
                            .show();
        }
        else {
            Intent i = new Intent(getApplicationContext(),
                    FormChooserList.class);
            startActivityForResult(i, FORM_CHOOSER);
        }
    }

    /**
     * Common code for handling button clicks that start a browser activity for a result.
     * 
     * @param urlPattern
     *            The @BrowserActivity.EXTRA_URL_INTENT related url pattern
     * @param requestCode
     *            Code identifying the button that invoked the browser call. This so that the result is handled
     *            accordingly in the parent activity.
     */
    private void onRequestBrowserIntentButtonClick(String urlPattern,
                                                   int requestCode) {
        String farmerName = farmerNameEditBox.getText().toString().trim();
        if (farmerName.length() > 0 || urlPattern.contentEquals("findFarmerId")) {
            if (urlPattern.contentEquals("findFarmerId") || checkID(farmerName)) {
                // Set the farmer ID
                GlobalConstants.intervieweeName = farmerName;
                Intent webActivity = new Intent(getApplicationContext(),
                        BrowserActivity.class);

                SharedPreferences settings = PreferenceManager
                        .getDefaultSharedPreferences(getBaseContext());
                String serverUrl = settings.getString(
                        ServerPreferences.KEY_SERVER,
                        getString(R.string.default_server));

                if (requestCode == REGISTRATION_CODE) {
                    showDialog(PROGRESS_DIALOG);
                    this.requestCode = requestCode;
                    new Thread(this).start();
                }
                else {
                    // Temporary edit for base url to use services instead of zebra
                    serverUrl = serverUrl.substring(0, serverUrl.length() - 5);
                    webActivity.putExtra(BrowserActivity.EXTRA_URL_INTENT,
                            serverUrl + "services/" + urlPattern + "?handsetId="
                                    + Handset.getImei(this) + "&farmerId="
                                    + farmerName);
                    
                    startActivityForResult(webActivity, requestCode);
                }    
            }
            else {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Invalid Farmer ID.", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
        else {
            Toast toast = Toast.makeText(getApplicationContext(),
                    "Farmer\'s ID cannot be empty", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private boolean checkID(String text) {
        Pattern pattern = Pattern.compile("[a-zA-Z]{2}[0-9]{4,5}+");
        Matcher matcher = pattern.matcher(text);
        return matcher.matches();
    }

    void showTestSurveyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.help);
        builder.setTitle("Perform Test Survey?");
        builder.setMessage(
                "The ID you entered is not valid, "
                        + "it should be 2 letters followed by at least 4 numbers."
                        + "\nWould you like to do a test survey instead?"
                        + " NOTE: You will NOT be compensated for doing a test survey.")
                .setCancelable(false)
                .setPositiveButton("Yes",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                GlobalConstants.intervieweeName = "TEST";
                                tryOpenFormChooser();
                                dialog.cancel();
                            }
                        })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Return an InputFilter that can validate farmer id characters entered in the UI
     */
    public InputFilter getFarmerInputFilter() {
        return new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned destination, int destinationStart,
                                       int destinationEnd) {
                for (int characterIndex = start; characterIndex < end; characterIndex++) {
                    char currentCharacter = source.charAt(characterIndex);
                    if (Character.isLetterOrDigit(currentCharacter)) {
                        continue;
                    }

                    if (Character.isWhitespace(currentCharacter)) {
                        continue;
                    }
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Invalid Character", Toast.LENGTH_SHORT);
                    toast.show();
                    return "";
                }
                return null;
            }
        };
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        refreshView();
    }

    /**
     * Upon return, check intent for data needed to launch other activities.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        if (resultCode == RESULT_CANCELED && requestCode < REGISTRATION_CODE) {
            return;
        }

        String formPath = null;
        Intent i = null;
        switch (requestCode) {
            // returns with a form path, start entry
            case FORM_CHOOSER:
                formPath = intent.getStringExtra(GlobalConstants.KEY_FORMPATH);
                i = new Intent(this, FormEntryActivity.class);
                i.putExtra(GlobalConstants.KEY_FORMPATH, formPath);
                startActivity(i);
                break;
            // returns with an instance path, start entry
            case INSTANCE_CHOOSER_TABS:
                formPath = intent.getStringExtra(GlobalConstants.KEY_FORMPATH);
                String instancePath = intent
                        .getStringExtra(GlobalConstants.KEY_INSTANCEPATH);
                i = new Intent(this, FormEntryActivity.class);
                i.putExtra(GlobalConstants.KEY_FORMPATH, formPath);
                i.putExtra(GlobalConstants.KEY_INSTANCEPATH, instancePath);
                startActivity(i);
                break;
            default:
                break;
            case REGISTRATION_CODE:
                if (resultCode == RESULT_OK) {
                    BrowserResultDialog.show(this, "Registration successful.",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    tryOpenFormChooser();
                                    dialog.cancel();
                                }
                            });
                }
                else if (resultCode == RESULT_CANCELED) {
                    // reset the Farmer ID
                    GlobalConstants.intervieweeName = "";
                    // Show error dialog
                    BrowserResultDialog
                            .show(this,
                                    "Unable to register farmer. \nCheck the ID or try again later.");
                }
                break;
            case FORGOT_ID_CODE:
                if (resultCode == RESULT_OK) {
                    if (intent != null) {
                        final String farmerId = intent.getStringExtra("data");
                        BrowserResultDialog.show(this, "Selected ID: " + farmerId,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int id) {
                                        GlobalConstants.intervieweeName = farmerId;
                                        tryOpenFormChooser();
                                        dialog.cancel();
                                    }
                                });
                    }
                }
                else if (resultCode == RESULT_CANCELED) {
                    // reset the Farmer ID
                    GlobalConstants.intervieweeName = "";
                    BrowserResultDialog.show(this,
                            "Unable to find ID. Try again later.");
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        FileDbAdapter fda = new FileDbAdapter(this);
        fda.open();
        fda.removeOrphanFormDefs();
        fda.close();
    }

    private void refreshView() {
        updateButtonCount();
    }

    /**
     * Updates the button count and sets the text in the buttons.
     */
    private void updateButtonCount() {
        // create adapter
        FileDbAdapter fda = new FileDbAdapter(this);
        fda.open();

        // count for saved instances
        Cursor c = fda.fetchFilesByType(FileDbAdapter.TYPE_INSTANCE,
                FileDbAdapter.STATUS_INCOMPLETE);
        mSavedCount = c.getCount();
        c.close();

        // count for completed instances
        c = fda.fetchFilesByType(FileDbAdapter.TYPE_INSTANCE,
                FileDbAdapter.STATUS_COMPLETE);
        mCompletedCount = c.getCount();
        c.close();

        // count for downloaded forms
        ArrayList<String> forms = FileUtils
                .getFilesAsArrayList(GlobalConstants.FORMS_PATH);
        if (forms != null) {
            mFormsCount = forms.size();
        }
        else {
            mFormsCount = 0;
        }

        // count for available forms
        c = fda.fetchFilesByType(FileDbAdapter.TYPE_FORM,
                FileDbAdapter.STATUS_AVAILABLE);
        mAvailableCount = c.getCount();
        c.close();
        fda.close();

        // update button text
        if (mAvailableCount == mFormsCount) {
            mEnterDataButton.setText(getString(R.string.enter_data_button,
                    mAvailableCount));
        }
        else {
            mEnterDataButton.setText(getString(R.string.enter_data));
        }

        mManageFilesButton.setText(getString(R.string.manage_files));
        mSendDataButton.setText(getString(R.string.send_data_button,
                mCompletedCount));
        mReviewDataButton.setText(getString(R.string.review_data_button,
                mSavedCount + mCompletedCount));
    }

    private void createPreferencesMenu() {
        Intent i = new Intent(this, ServerPreferences.class);
        startActivity(i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_PREFERENCES, 0, getString(R.string.server_preferences))
                .setIcon(android.R.drawable.ic_menu_preferences);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                createPreferencesMenu();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void run() {

        if (requestCode == REGISTRATION_CODE) {

            errorMessage = null;

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            String serverUrl = settings.getString(ServerPreferences.KEY_SERVER, getString(R.string.default_server));

            String html = this.farmerRegController.getFormHtml(GlobalConstants.intervieweeName, serverUrl);

            if (html != null) {
                Intent webActivity = new Intent(getApplicationContext(), BrowserActivity.class);
                webActivity.putExtra(BrowserActivity.EXTRA_HTML_INTENT, html);
                startActivityForResult(webActivity, requestCode);
            }
            else {
                errorMessage = "Failed to get the farmer registration form. Please try again.";
            }

            // Dismiss the progress window.
            handler.sendEmptyMessage(0);
        }
    }

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            dismissDialog(PROGRESS_DIALOG);

            if (errorMessage != null)
                BrowserResultDialog.show(ApplabActivity.getGlobalContext(), errorMessage);
        }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(getTitle());
        progressDialog.setMessage("Loading Form. Please wait ...");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);

        return progressDialog;
    }
}
