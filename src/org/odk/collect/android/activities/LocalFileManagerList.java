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

import org.odk.collect.android.database.FileDbAdapter;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import applab.surveys.client.FormUtilities;
import applab.surveys.client.R;

/**
 * Responsible for displaying and deleting all the valid forms in the forms directory.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class LocalFileManagerList extends ListActivity {

    private AlertDialog mAlertDialog;
    private Button mActionButton;

    private SimpleCursorAdapter mInstances;
    private ArrayList<Long> mSelected = new ArrayList<Long>();

    /** List of paths to form definitions that have been selected by the user. */
    private ArrayList<String> mSelectedFormDefPaths = new ArrayList<String>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.local_file_manage_list);
        mActionButton = (Button)findViewById(R.id.delete_button);
        mActionButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {

                if (mSelected.size() > 0) {
                    createDeleteDialog();
                }
                else {
                    Toast.makeText(getApplicationContext(), R.string.noselect_error,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        // buildView takes place in resume
    }

    private void refreshView() {
        // get all mInstances that match the status.
        FileDbAdapter fda = new FileDbAdapter(this);
        fda.open();
        fda.addOrphanForms();
        Cursor c = fda.fetchAllFiles();
        startManagingCursor(c);

        String[] data = new String[] { FileDbAdapter.KEY_DISPLAY, FileDbAdapter.KEY_META };
        int[] view = new int[] { R.id.text1, R.id.text2 };

        // render total instance view
        mInstances =
                new SimpleCursorAdapter(this, R.layout.two_item_multiple_choice, c, data, view);
        setListAdapter(mInstances);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setItemsCanFocus(false);
        mActionButton.setEnabled(!(mInstances.getCount() == 0));

        // cleanup
        fda.close();
    }

    /**
     * Create the file delete dialog
     */
    private void createDeleteDialog() {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setTitle(getString(R.string.delete_file));
        mAlertDialog.setMessage(getString(R.string.delete_confirm, mSelected.size()));
        DialogInterface.OnClickListener dialogYesNoListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        switch (i) {
                            case DialogInterface.BUTTON1: // delete and
                                if (deleteSelectedFiles()) {
                                    refreshData();
                                }
                                break;
                            case DialogInterface.BUTTON2: // do nothing
                                break;
                        }
                    }

                };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.yes), dialogYesNoListener);
        mAlertDialog.setButton2(getString(R.string.no), dialogYesNoListener);
        mAlertDialog.show();
    }

    private void refreshData() {
        if (mInstances != null) {
            mInstances.getCursor().requery();
        }
        mSelected.clear();
        this.mSelectedFormDefPaths.clear();
        refreshView();
    }

    /**
     * Deletes the selected files.First from the database then from the file system
     * 
     * @return true if deleted any files, else false.
     */
    private boolean deleteSelectedFiles() {

        if (FormUtilities.isThereAnySelectedFormWithData(this, mSelectedFormDefPaths, "Cannot be deleted because it has data. Please submit or delete the data first before deleting the form.")) {
            return false;
        }

        FileDbAdapter fda = new FileDbAdapter(this);
        fda.open();

        // delete removes the file from the database first
        int deleted = 0;
        for (int i = 0; i < mSelected.size(); i++) {
            if (fda.deleteFile(mSelected.get(i))) {
                deleted++;
            }
        }

        // remove the actual files and close db
        fda.removeOrphanForms();
        fda.removeOrphanInstances();
        fda.close();

        if (deleted > 0) {
            // all deletes were successful
            Toast.makeText(getApplicationContext(), getString(R.string.file_deleted_ok, deleted),
                    Toast.LENGTH_SHORT).show();
            refreshData();
            if (mInstances.isEmpty()) {
                finish();
            }
        }
        else {
            // had some failures
            Toast.makeText(
                    getApplicationContext(),
                    getString(R.string.file_deleted_error, mSelected.size() - deleted + " of "
                            + mSelected.size()), Toast.LENGTH_LONG).show();
        }

        return true;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        // get row id from db
        Cursor c = (Cursor)getListAdapter().getItem(position);
        long k = c.getLong(c.getColumnIndex(FileDbAdapter.KEY_ID));

        // add/remove from selected list
        if (mSelected.contains(k)) {
            mSelected.remove(k);
        }
        else {
            mSelected.add(k);
        }

        //Check if the selected file is a form definition instead of instance data.
        String filePath = c.getString(c.getColumnIndex(FileDbAdapter.KEY_FILEPATH));
        if (filePath.contains("/forms/") && !filePath.contains("/instances/")) {
            if (mSelectedFormDefPaths.contains(filePath)) {
                this.mSelectedFormDefPaths.remove(filePath);
            }
            else {
                this.mSelectedFormDefPaths.add(filePath);
            }
        }
    }

    @Override
    protected void onPause() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        // update the list (for returning from the remote manager)
        refreshData();
        super.onResume();
    }
}
