package org.wikipedia.readinglist;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.wikipedia.R;
import org.wikipedia.WikipediaApp;
import org.wikipedia.analytics.ReadingListsFunnel;
import org.wikipedia.model.EnumCode;
import org.wikipedia.model.EnumCodeMap;
import org.wikipedia.page.ExtendedBottomSheetDialogFragment;
import org.wikipedia.page.PageActivity;
import org.wikipedia.page.PageTitle;
import org.wikipedia.util.FeedbackUtil;

import java.util.ArrayList;
import java.util.List;

public class AddToReadingListDialog extends ExtendedBottomSheetDialogFragment {
    public enum InvokeSource implements EnumCode {
        BOOKMARK_BUTTON(0),
        CONTEXT_MENU(1),
        LINK_PREVIEW_MENU(2);

        private static final EnumCodeMap<InvokeSource> MAP = new EnumCodeMap<>(InvokeSource.class);

        private final int code;

        public static InvokeSource of(int code) {
            return MAP.get(code);
        }

        @Override public int code() {
            return code;
        }

        InvokeSource(int code) {
            this.code = code;
        }
    }

    private PageTitle pageTitle;
    private ReadingListAdapter adapter;
    private View listsContainer;
    private View onboardingContainer;
    private View onboardingButton;
    private InvokeSource invokeSource;
    private boolean isOnboarding;
    private CreateButtonClickListener createClickListener = new CreateButtonClickListener();
    private List<ReadingList> readingLists = new ArrayList<>();
    private DialogInterface.OnDismissListener dismissListener;

    public static AddToReadingListDialog newInstance(PageTitle title, InvokeSource source) {
        AddToReadingListDialog dialog = new AddToReadingListDialog();
        Bundle args = new Bundle();
        args.putParcelable("title", title);
        args.putInt("source", source.code());
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pageTitle = getArguments().getParcelable("title");
        invokeSource = InvokeSource.of(getArguments().getInt("source"));
        adapter = new ReadingListAdapter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.dialog_add_to_reading_list, container);

        listsContainer = rootView.findViewById(R.id.lists_container);
        onboardingContainer = rootView.findViewById(R.id.onboarding_container);
        onboardingButton = rootView.findViewById(R.id.onboarding_button);
        checkAndShowOnboarding();

        RecyclerView readingListView = (RecyclerView) rootView.findViewById(R.id.list_of_lists);
        readingListView.setLayoutManager(new LinearLayoutManager(getActivity()));
        readingListView.setAdapter(adapter);

        View createButton = rootView.findViewById(R.id.create_button);
        createButton.setOnClickListener(createClickListener);

        View closeButton = rootView.findViewById(R.id.close_button);
        FeedbackUtil.setToolbarButtonLongPressToast(closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        updateLists();
        return rootView;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (dismissListener != null) {
            dismissListener.onDismiss(null);
        }
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        dismissListener = listener;
    }

    private void checkAndShowOnboarding() {
        isOnboarding = WikipediaApp.getInstance().getOnboardingStateMachine().isReadingListTutorialEnabled();
        onboardingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onboardingContainer.setVisibility(View.GONE);
                listsContainer.setVisibility(View.VISIBLE);
                WikipediaApp.getInstance().getOnboardingStateMachine().setReadingListTutorial();
            }
        });
        listsContainer.setVisibility(isOnboarding ? View.GONE : View.VISIBLE);
        onboardingContainer.setVisibility(isOnboarding ? View.VISIBLE : View.GONE);
    }

    private void updateLists() {
        readingLists = ReadingList.DAO.queryMruLists();
        adapter.notifyDataSetChanged();
    }

    private class CreateButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            final ReadingList readingList = new ReadingList();
            readingList.setTitle(getString(R.string.reading_list_name_sample));
            AlertDialog dialog = ReadingListDialogs.createEditDialog(getContext(), readingList, false, new Runnable() {
                @Override
                public void run() {
                    addAndDismiss(readingList);
                }
            }, null);
            dialog.show();
        }
    }

    private void addAndDismiss(ReadingList readingList) {
        if (ReadingList.DAO.listContainsTitle(readingList, pageTitle)) {
            ((PageActivity) getActivity())
                    .showReadingListAddedSnackbar(getString(R.string.reading_list_already_exists), isOnboarding);
        } else {
            ((PageActivity) getActivity())
                    .showReadingListAddedSnackbar(TextUtils.isEmpty(readingList.getTitle())
                            ? getString(R.string.reading_list_added_to_unnamed)
                            : String.format(getString(R.string.reading_list_added_to_named),
                            readingList.getTitle()), isOnboarding);

            new ReadingListsFunnel(pageTitle.getSite()).logAddToList(readingList, readingLists.size(), invokeSource);
            ReadingList.DAO.addTitleToList(readingList, pageTitle);
        }

        ReadingList.DAO.makeListMostRecent(readingList);
        dismiss();
    }

    private class ReadingListItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ReadingListItemView itemView;
        private ReadingList readingList;

        ReadingListItemHolder(ReadingListItemView itemView) {
            super(itemView);
            this.itemView = itemView;
            itemView.setOnClickListener(this);
        }

        public void bindItem(ReadingList readingList) {
            this.readingList = readingList;
            itemView.setReadingList(readingList);
        }

        @Override
        public void onClick(View v) {
            addAndDismiss(readingList);
        }
    }

    private final class ReadingListAdapter extends RecyclerView.Adapter<ReadingListItemHolder> {
        @Override
        public int getItemCount() {
            return readingLists.size();
        }

        @Override
        public ReadingListItemHolder onCreateViewHolder(ViewGroup parent, int pos) {
            ReadingListItemView view = new ReadingListItemView(getContext());
            return new ReadingListItemHolder(view);
        }

        @Override
        public void onBindViewHolder(ReadingListItemHolder holder, int pos) {
            holder.bindItem(readingLists.get(pos));
        }
    }
}
