/**
 * Copyright (C) 2010 Grameen Foundation
Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
 */

package applab.surveys.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.odk.collect.android.database.FileDbAdapter;
import org.odk.collect.android.utilities.FileUtils;

import android.content.Context;
import android.database.Cursor;
import android.widget.Toast;

/**
 * Utility functions used for managing forms.
 */
public class FormUtilities {

    /**
     * Checks if there is any selected form definition that has instance data.
     * 
     * @param context
     *            is the application context.
     * @param selectedFormDefPaths
     *            is the list of paths to form definitions that have been selected by the user.
     * @param errorMessage
     *            is the error message to display if we come across a form which has data. The name of the form is
     *            prepended to the error message.
     * @return true if there is any, else false if none.
     */
    public static boolean isThereAnySelectedFormWithData(Context context, List<String> selectedFormDefPaths, String errorMessage) {
        List<String> instanceFormDefs = getInstanceFormDefs(context);

        for (String filePath : selectedFormDefPaths) {
            if (instanceFormDefs.contains(filePath)) {
                String message = filePath.substring(filePath.lastIndexOf('/') + 1)
                        + " " + errorMessage;
                message = message.replace(".xml", " Form");
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                return true;
            }
        }

        return false;
    }

    /**
     * Gets a list of form definitions that have instances of data.
     * 
     * @param context
     *            is the application context.
     * @return a list of paths to the form definitions.
     */
    public static List<String> getInstanceFormDefs(Context context) {
        List<String> instanceFormDefs = new ArrayList<String>();

        FileDbAdapter fda = new FileDbAdapter();
        fda.open();

        Cursor cursor = fda.fetchFilesByType(FileDbAdapter.TYPE_INSTANCE, null);
        if (cursor != null) {
            while (!cursor.isAfterLast()) {
                String instancePath = cursor.getString(cursor.getColumnIndex(FileDbAdapter.KEY_FILEPATH));
                String formPath = getFormPathFromInstancePath(instancePath);
                if (!instanceFormDefs.contains(formPath)) {
                    instanceFormDefs.add(formPath);
                }

                cursor.moveToNext();
            }
        }

        fda.close();

        return instanceFormDefs;
    }

    /**
     * Given an instance path, return the full path to the form definition.
     * 
     * @param instancePath
     *            full path to the instance
     * @return formPath full path to the form the instance was generated from
     */
    private static String getFormPathFromInstancePath(String instancePath) {
        // trim the farmer id and timestamp
        String regex = "\\_\\[[a-zA-Z]{2}[0-9]{4,5}+\\]\\_[0-9]{4}\\-[0-9]{2}\\-[0-9]{2}\\_[0-9]{2}\\-[0-9]{2}\\-[0-9]{2}\\.xml$";
        Pattern pattern = Pattern.compile(regex);
        String formName = pattern.split(instancePath)[0];
        formName = formName.substring(formName.lastIndexOf("/") + 1);

        File xmlFile = new File(FileUtils.FORMS_PATH + "/" + formName + ".xml");
        File xhtmlFile = new File(FileUtils.FORMS_PATH + "/" + formName + ".xhtml");

        // form is either xml or xhtml file. find the appropriate one.
        if (xmlFile.exists()) {
            return xmlFile.getAbsolutePath();
        }
        else if (xhtmlFile.exists()) {
            return xhtmlFile.getAbsolutePath();
        }
        else {
            return null;
        }
    }
}
