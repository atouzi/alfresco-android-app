/*******************************************************************************
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco Mobile for Android.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.alfresco.mobile.android.application.capture;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.alfresco.mobile.android.api.model.Folder;
import org.alfresco.mobile.android.application.fragments.fileexplorer.FileExplorerFragment;
import org.alfresco.mobile.android.application.fragments.node.create.AddContentDialogFragment;
import org.alfresco.mobile.android.platform.io.AlfrescoStorageManager;
import org.alfresco.mobile.android.platform.utils.SessionUtils;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public abstract class DeviceCapture implements Serializable
{
    public static final String TIMESTAMP_PATTERN = "yyyyddMM_HHmmss";
    
    private static final long serialVersionUID = 1L;

    protected Folder repositoryFolder = null;

    protected transient Context context = null;

    protected transient Activity parentActivity = null;

    protected File payload = null;

    protected String mimeType = null;

    protected File parentFolder;

    // ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    // ///////////////////////////////////////////////////////////////////////////
    protected DeviceCapture(Activity parentActivity, Folder repositoryFolder)
    {
        this(parentActivity, repositoryFolder, null);
    }

    protected DeviceCapture(Activity parentActivity, Folder repositoryFolder, File parentFolder)
    {
        this.context = parentActivity;
        this.parentActivity = parentActivity;
        this.repositoryFolder = repositoryFolder;
        if (parentFolder == null)
        {
            this.parentFolder = AlfrescoStorageManager.getInstance(parentActivity).getCaptureFolder(
                    SessionUtils.getAccount(parentActivity));
        }
        else
        {
            this.parentFolder = parentFolder;
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // ABSTRACT
    // ///////////////////////////////////////////////////////////////////////////
    public abstract boolean hasDevice();

    public abstract boolean captureData();

    protected abstract boolean payloadCaptured(int requestCode, int resultCode, Intent data);

    // ///////////////////////////////////////////////////////////////////////////
    // PUBLIC
    // ///////////////////////////////////////////////////////////////////////////
    public void capturedCallback(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == getRequestCode() && resultCode == Activity.RESULT_OK)
        {
            if (repositoryFolder != null  &&  payloadCaptured(requestCode, resultCode, data))
            {
                upload();
            }
            else
            {
                FileExplorerFragment frag = (FileExplorerFragment) parentActivity.getFragmentManager()
                        .findFragmentByTag(FileExplorerFragment.TAG);
                
                if (frag != null)
                	frag.refresh();
            }
        }
    }

    private void upload()
    {
        FragmentTransaction ft = parentActivity.getFragmentManager().beginTransaction();
        Fragment prev = parentActivity.getFragmentManager().findFragmentByTag(AddContentDialogFragment.TAG);

        if (prev != null)
        {
            ft.remove(prev);
        }

        AddContentDialogFragment newFragment;

        if (mimeType != null)
        {
            newFragment = AddContentDialogFragment.newInstance(repositoryFolder, payload, mimeType, true);
        }
        else
        {
            newFragment = AddContentDialogFragment.newInstance(repositoryFolder, payload, true);
        }

        newFragment.show(ft, AddContentDialogFragment.TAG);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // UTILS
    // ///////////////////////////////////////////////////////////////////////////
    protected String createFilename(String prefix, String extension)
    {
        String timeStamp = new SimpleDateFormat(TIMESTAMP_PATTERN).format(new Date());

        return prefix + timeStamp + "." + extension;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // FINAL
    // ///////////////////////////////////////////////////////////////////////////
    protected void finalize()
    {
        try
        {
            if (payload != null)
            {
                payload.delete();
                payload = null;
            }
            super.finalize();
        }
        catch (Throwable e)
        {
            Log.w(DeviceCapture.class.getName(), Log.getStackTraceString(e));
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // SETTERS / GETTERS
    // ///////////////////////////////////////////////////////////////////////////
    public void setActivity(Activity parentActivity)
    {
        this.parentActivity = parentActivity;
    }

    public int getRequestCode()
    {
        return Math.abs(getClass().hashCode());
    }
}
