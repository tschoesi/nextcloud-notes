package it.niedermann.owncloud.notes.android.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.model.Item;
import it.niedermann.owncloud.notes.model.ItemAdapter;
import it.niedermann.owncloud.notes.model.Note;
import it.niedermann.owncloud.notes.model.SectionItem;
import it.niedermann.owncloud.notes.persistence.NoteSQLiteOpenHelper;
import it.niedermann.owncloud.notes.util.ICallback;

public class NotesListViewActivity extends AppCompatActivity implements
        OnItemClickListener, View.OnClickListener {

    public final static String SELECTED_NOTE = "it.niedermann.owncloud.notes.clicked_note";
    public final static String CREATED_NOTE = "it.niedermann.owncloud.notes.created_notes";
    public final static String SELECTED_NOTE_POSITION = "it.niedermann.owncloud.notes.clicked_note_position";
    public final static String CREDENTIALS_CHANGED = "it.niedermann.owncloud.notes.CREDENTIALS_CHANGED";

    private final static int create_note_cmd = 0;
    private final static int show_single_note_cmd = 1;
    private final static int server_settings = 2;
    private final static int about = 3;

    private ListView listView = null;
    private ItemAdapter adapter = null;
    private ActionMode mActionMode;
    private SwipeRefreshLayout swipeRefreshLayout = null;
    private NoteSQLiteOpenHelper db = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // First Run Wizard
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        if(preferences.getBoolean(SettingsActivity.SETTINGS_FIRST_RUN, true)) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivityForResult(settingsIntent, server_settings);
        }

        setContentView(R.layout.activity_notes_list_view);

        // Display Data
        db = new NoteSQLiteOpenHelper(this);
        db.synchronizeWithServer();
        setListView(db.getNotes());

        // Pull to Refresh
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefreshlayout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.d("Swipe", "Refreshing Notes");
                db.synchronizeWithServer();
                db.getNoteServerSyncHelper().addCallback(new ICallback() {
                    @Override
                    public void onFinish() {
                        swipeRefreshLayout.setRefreshing(false);
                        setListView(db.getNotes());
                    }
                });
            }
        });

        // Floating Action Button
        findViewById(R.id.fab_create).setOnClickListener(this);
    }

    /**
     * Click listener for <strong>Floating Action Button</strong>
     * <p/>
     * Creates a new Instance of CreateNoteActivity.
     *
     * @param v View
     */
    @Override
    public void onClick(View v) {
        Intent createIntent = new Intent(this, CreateNoteActivity.class);
        startActivityForResult(createIntent, create_note_cmd);
    }

    /**
     * Allows other classes to set a List of Notes.
     *
     * @param noteList List&lt;Note&gt;
     */
    @SuppressWarnings("WeakerAccess")
    public void setListView(List<Note> noteList) {
        List<Item> itemList = new ArrayList<>();
        // #12 Create Sections depending on Time
        // TODO Move to ItemAdapter?
        boolean recentSet, todaySet, yesterdaySet, weekSet, monthSet, earlierSet;
        recentSet = todaySet = yesterdaySet = weekSet = monthSet = earlierSet = false;
        Calendar recent = Calendar.getInstance();
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Calendar yesterday = Calendar.getInstance();
        yesterday.set(Calendar.DAY_OF_YEAR, yesterday.get(Calendar.DAY_OF_YEAR) - 1);
        yesterday.set(Calendar.HOUR_OF_DAY, 0);
        yesterday.set(Calendar.MINUTE, 0);
        yesterday.set(Calendar.SECOND, 0);
        yesterday.set(Calendar.MILLISECOND, 0);
        Calendar week = Calendar.getInstance();
        week.set(Calendar.DAY_OF_WEEK, week.getFirstDayOfWeek());
        week.set(Calendar.HOUR_OF_DAY, 0);
        week.set(Calendar.MINUTE, 0);
        week.set(Calendar.SECOND, 0);
        week.set(Calendar.MILLISECOND, 0);
        Calendar month = Calendar.getInstance();
        month.set(Calendar.DAY_OF_MONTH, 0);
        month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0);
        month.set(Calendar.SECOND, 0);
        month.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < noteList.size(); i++) {
            Note currentNote = noteList.get(i);
            if (!recentSet && recent.getTimeInMillis() - currentNote.getModified().getTimeInMillis() < 600000) {
                // < 10 minutes
                itemList.add(new SectionItem(getResources().getString(R.string.listview_updated_recent)));
                recentSet = true;
            } else if (!todaySet && recent.getTimeInMillis() - currentNote.getModified().getTimeInMillis() >= 600000 && currentNote.getModified().getTimeInMillis() >= today.getTimeInMillis()) {
                // < 10 minutes but after 00:00 today
                itemList.add(new SectionItem(getResources().getString(R.string.listview_updated_today)));
                todaySet = true;
            } else if (!yesterdaySet && currentNote.getModified().getTimeInMillis() < today.getTimeInMillis() && currentNote.getModified().getTimeInMillis() >= yesterday.getTimeInMillis()) {
                // between today 00:00 and yesterday 00:00
                itemList.add(new SectionItem(getResources().getString(R.string.listview_updated_yesterday)));
                yesterdaySet = true;
            } else if (!weekSet && currentNote.getModified().getTimeInMillis() < yesterday.getTimeInMillis() && currentNote.getModified().getTimeInMillis() >= week.getTimeInMillis()) {
                // between yesterday 00:00 and start of the week 00:00
                itemList.add(new SectionItem(getResources().getString(R.string.listview_updated_this_week)));
                weekSet = true;
            } else if (!monthSet && currentNote.getModified().getTimeInMillis() < week.getTimeInMillis() && currentNote.getModified().getTimeInMillis() >= month.getTimeInMillis()) {
                // between start of the week 00:00 and start of the month 00:00
                itemList.add(new SectionItem(getResources().getString(R.string.listview_updated_this_month)));
                monthSet = true;
            } else if (!earlierSet && currentNote.getModified().getTimeInMillis() < month.getTimeInMillis()) {
                // before start of the month 00:00
                itemList.add(new SectionItem(getResources().getString(R.string.listview_updated_earlier)));
                earlierSet = true;
            }
            itemList.add(currentNote);
        }

        adapter = new ItemAdapter(getApplicationContext(), itemList);
        listView = (ListView) findViewById(R.id.list_view);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        listView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                onListItemSelect(position);
                return true;
            }
        });
    }

    /**
     * A short click on one list item. Creates a new instance of NoteActivity.
     */
    @Override
    public void onItemClick(AdapterView<?> parentView, View childView,
                            int position, long id) {
        Item item = adapter.getItem(position);
        if (!item.isSection()) {
            listView.setItemChecked(position, !listView.isItemChecked(position));
            Log.v("Note", "getCheckedItemCount " + listView.getCheckedItemCount());
            if (listView.getCheckedItemCount() < 1) {
                removeSelection();
                Intent intent = new Intent(getApplicationContext(),
                        NoteActivity.class);
                if (!item.isSection()) {
                    intent.putExtra(SELECTED_NOTE, (Note) item);
                    intent.putExtra(SELECTED_NOTE_POSITION, position);
                    Log.v("Note",
                            "notePosition | NotesListViewActivity wurde abgesendet "
                                    + position);
                    startActivityForResult(intent, show_single_note_cmd);
                }
            } else { // perform long click if already something is selected
                onListItemSelect(position);
            }
        } else {
            listView.setItemChecked(position, false);
        }
    }

    /**
     * Adds the Menu Items to the Action Bar.
     *
     * @param menu Menu
     * @return boolean
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_list_view, menu);
        return true;
    }

    /**
     * Handels click events on the Buttons in the Action Bar.
     *
     * @param item MenuItem - the clicked menu item
     * @return boolean
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivityForResult(settingsIntent, server_settings);
                return true;
            case R.id.action_about:
                Intent aboutIntent = new Intent(this, AboutActivity.class);
                startActivityForResult(aboutIntent, about);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Handles the Results of started Sub Activities (Created Note, Edited Note)
     *
     * @param requestCode int to distinguish between the different Sub Activities
     * @param resultCode  int Return Code
     * @param data        Intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == create_note_cmd) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Note createdNote = (Note) data.getExtras().getSerializable(
                        CREATED_NOTE);
                adapter.add(createdNote);
            }
        } else if (requestCode == NoteActivity.EDIT_NOTE_CMD) {
            if (resultCode == RESULT_OK) {
                Note editedNote = (Note) data.getExtras().getSerializable(
                        NoteActivity.EDIT_NOTE);
                int notePosition = data.getExtras().getInt(
                        SELECTED_NOTE_POSITION);
                adapter.remove(adapter.getItem(notePosition));
                adapter.add(editedNote);
            }
        } else if (requestCode == SettingsActivity.CREDENTIALS_CHANGED) {
            db = new NoteSQLiteOpenHelper(this);
            db.synchronizeWithServer(); // Needed to instanciate new NotesClient with new URL
        }
        //TODO Maybe only if previous activity == settings activity?
        setListView(db.getNotes());
    }

    /**
     * Long click on one item in the list view. It starts the Action Mode and allows selecting more
     * items and execute bulk functions (e. g. delete)
     *
     * @param position int - position of the clicked item
     */
    private void onListItemSelect(int position) {
        if (!adapter.getItem(position).isSection()) {
            listView.setItemChecked(position, !listView.isItemChecked(position));
            int checkedItemCount = listView.getCheckedItemCount();
            boolean hasCheckedItems = checkedItemCount > 0;

            if (hasCheckedItems && mActionMode == null) {
                // TODO differ if one or more items are selected
                // if (checkedItemCount == 1) {
                // mActionMode = startActionMode(new
                // SingleSelectedActionModeCallback());
                // } else {
                // there are some selected items, start the actionMode
                mActionMode = startSupportActionMode(new MultiSelectedActionModeCallback());
                // }
            } else if (!hasCheckedItems && mActionMode != null) {
                // there no selected items, finish the actionMode
                mActionMode.finish();
            }

            if (mActionMode != null) {
                mActionMode.setTitle(String.valueOf(listView.getCheckedItemCount())
                        + " " + getString(R.string.ab_selected));
            }
        } else {
            listView.setItemChecked(position, false);
        }
    }

    /**
     * Removes all selections.
     */
    private void removeSelection() {
        SparseBooleanArray checkedItemPositions = listView
                .getCheckedItemPositions();
        for (int i = 0; i < checkedItemPositions.size(); i++) {
            listView.setItemChecked(i, false);
        }
    }

    /**
     * Handler for the MultiSelect Actions
     */
    private class MultiSelectedActionModeCallback implements
            ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // inflate contextual menu
            mode.getMenuInflater().inflate(R.menu.menu_list_context_multiple,
                    menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        /**
         * @param mode ActionMode - used to close the Action Bar after all work is done.
         * @param item MenuItem - the item in the List that contains the Node
         * @return boolean
         */
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_delete:
                    SparseBooleanArray checkedItemPositions = listView
                            .getCheckedItemPositions();
                    for (int i = (checkedItemPositions.size() - 1); i >= 0; i--) {
                        if (checkedItemPositions.valueAt(i)) {
                            Note note = (Note) adapter.getItem(checkedItemPositions
                                    .keyAt(i));
                            db.deleteNoteAndSync(note.getId());
                            adapter.remove(note);
                        }
                    }
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            removeSelection();
            mActionMode = null;
            adapter.notifyDataSetChanged();
        }
    }
}