/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.views.editor;

import com.android.contacts.R;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.views.editor.Editor.EditorListener;

import android.content.Context;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Custom view for an entire section of data as segmented by
 * {@link DataKind} around a {@link Data#MIMETYPE}. This view shows a
 * section header and a trigger for adding new {@link Data} rows.
 */
public class KindSectionView extends LinearLayout implements EditorListener {
    private static final String TAG = "KindSectionView";

    private ViewGroup mEditors;
    private ImageButton mAddPlusButton;
    private TextView mTitle;

    private DataKind mKind;
    private EntityDelta mState;
    private boolean mReadOnly;

    private ViewIdGenerator mViewIdGenerator;

    public KindSectionView(Context context) {
        super(context);
    }

    public KindSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        setDrawingCacheEnabled(true);
        setAlwaysDrawnWithCacheEnabled(true);

        mEditors = (ViewGroup)findViewById(R.id.kind_editors);

        mAddPlusButton = (ImageButton) findViewById(R.id.kind_plus);
        mAddPlusButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                addItem();
            }
        });

        mTitle = (TextView)findViewById(R.id.kind_title);
    }

    /** {@inheritDoc} */
    public void onDeleted(Editor editor) {
        updateAddEnabled();
        updateVisible();
    }

    /** {@inheritDoc} */
    public void onRequest(int request) {
        // Ignore requests
    }

    public void setState(DataKind kind, EntityDelta state, boolean readOnly, ViewIdGenerator vig) {
        mKind = kind;
        mState = state;
        mReadOnly = readOnly;
        mViewIdGenerator = vig;

        setId(mViewIdGenerator.getId(state, kind, null, ViewIdGenerator.NO_VIEW_INDEX));

        // TODO: handle resources from remote packages
        mTitle.setText(kind.titleRes);

        // Only show the add button if this is a list
        mAddPlusButton.setVisibility(mKind.isList ? View.VISIBLE : View.INVISIBLE);

        rebuildFromState();
        updateAddEnabled();
        updateVisible();
    }

    public CharSequence getTitle() {
        return mTitle.getText();
    }

    /**
     * Build editors for all current {@link #mState} rows.
     */
    public void rebuildFromState() {
        // Remove any existing editors
        mEditors.removeAllViews();

        // Check if we are displaying anything here
        boolean hasEntries = mState.hasMimeEntries(mKind.mimeType);

        if (hasEntries) {
            for (ValuesDelta entry : mState.getMimeEntries(mKind.mimeType)) {
                // Skip entries that aren't visible
                if (!entry.isVisible()) continue;
                if (isEmptyNoop(entry)) continue;

                createEditorView(entry);
            }
        }
    }


    /**
     * Creates an EditorView for the given entry. This function must be used while constructing
     * the views corresponding to the the object-model. The resulting EditorView is also added
     * to the end of mEditors
     */
    private View createEditorView(ValuesDelta entry) {
        final View view;
        if (mKind.editorClass == null) {
            view = new GenericEditorView(mContext);
        } else {
            try {
                view = mKind.editorClass.getConstructor(Context.class).newInstance(
                        mContext);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Cannot allocate editor for " + mKind.editorClass);
            }
        }

        if (view instanceof Editor) {
            Editor editor = (Editor) view;
            editor.setValues(mKind, entry, mState, mReadOnly, mViewIdGenerator);
            editor.setEditorListener(this);
            editor.setDeletable(true);
        }
        mEditors.addView(view);
        return view;
    }

    /**
     * Tests whether the given item has no changes (so it exists in the database) but is empty
     */
    private boolean isEmptyNoop(ValuesDelta item) {
        if (!item.isNoop()) return false;
        final int fieldCount = mKind.fieldList.size();
        for (int i = 0; i < fieldCount; i++) {
            final String column = mKind.fieldList.get(i).column;
            final String value = item.getAsString(column);
            if (!TextUtils.isEmpty(value)) return false;
        }
        return true;
    }

    private void updateVisible() {
        setVisibility(getEditorCount() != 0 ? VISIBLE : GONE);
    }


    protected void updateAddEnabled() {
        // Set enabled state on the "add" view
        final boolean canInsert = EntityModifier.canInsert(mState, mKind);
        final boolean isEnabled = !mReadOnly && canInsert;
        mAddPlusButton.setEnabled(isEnabled);
    }

    public void addItem() {
        // if this is a list, we can freely add. if not, only allow adding the first
        if (!mKind.isList && getEditorCount() == 1)
            return;

        // Insert a new child, create its view and set its focus
        final ValuesDelta newValues = EntityModifier.insertChild(mState, mKind);
        final View newField = createEditorView(newValues);
        newField.requestFocus();

        // For non-lists (e.g. Notes we can only have one field. in that case we need to disable
        // the add button
        updateAddEnabled();

        // Ensure we are visible
        updateVisible();
    }

    public int getEditorCount() {
        return mEditors.getChildCount();
    }

    public DataKind getKind() {
        return mKind;
    }
}