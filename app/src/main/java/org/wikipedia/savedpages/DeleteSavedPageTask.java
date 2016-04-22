package org.wikipedia.savedpages;

import android.content.Context;

import org.wikipedia.WikipediaApp;
import org.wikipedia.concurrency.SaneAsyncTask;
import org.wikipedia.database.DatabaseClient;
import org.wikipedia.database.contract.SavedPageContract;

public class DeleteSavedPageTask extends SaneAsyncTask<Boolean> {
    private final WikipediaApp app;
    private final SavedPage savedPage;

    public DeleteSavedPageTask(Context context, SavedPage savedPage) {
        app = (WikipediaApp) context.getApplicationContext();
        this.savedPage = savedPage;
    }

    @Override
    public Boolean performTask() throws Throwable {
        savedPage.deleteFromFileSystem();
        DatabaseClient<SavedPage> client = app.getDatabaseClient(SavedPage.class);
        client.delete(savedPage, SavedPageContract.Page.SELECTION);
        WikipediaApp.getInstance().getFunnelManager().getSavedPagesFunnel(savedPage.getTitle().getSite()).logDelete();
        return true;
    }
}
