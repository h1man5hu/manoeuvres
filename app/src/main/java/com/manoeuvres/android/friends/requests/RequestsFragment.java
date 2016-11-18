package com.manoeuvres.android.friends.requests;

import android.widget.Button;

import com.manoeuvres.android.R;
import com.manoeuvres.android.friends.AbstractFriendsFragment;
import com.manoeuvres.android.friends.findfriends.FacebookFriendsPresenter;
import com.manoeuvres.android.friends.requests.RequestsPresenter.RequestsListener;
import com.manoeuvres.android.friends.Friend;
import com.manoeuvres.android.util.UniqueId;

import java.util.List;

public class RequestsFragment extends AbstractFriendsFragment implements RequestsListener {

    private RequestsPresenter mRequestsPresenter;

    public RequestsFragment() {
        // Required empty public constructor
    }

    public static RequestsFragment newInstance() {
        return new RequestsFragment();
    }

    @Override
    public void onStart() {
        super.onStart();

        mRequestsPresenter = RequestsPresenter.getInstance();
        mRequestsPresenter.attach(this);
        mRequestsPresenter.sync();

        if (mRequestsPresenter.isLoaded()) {
            List<Friend> requests = mRequestsPresenter.getAll();
            for (int i = 0; i < requests.size(); i++) {
                Friend friend = requests.get(i);
                cancelNotification(UniqueId.getRequestId(friend));
            }
            onCompleteRequestsLoading();
            getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    protected int getNavigationMenuItemId() {
        return R.id.nav_requests;
    }

    @Override
    protected int getActivityTitleId() {
        return R.string.title_activity_main_requests;
    }

    @Override
    protected String getLoadingText() {
        return String.format(
                getString(R.string.textview_loading_friends),
                getString(R.string.text_loading_friends_requests));
    }

    @Override
    protected int getNoFriendsTextId() {
        return R.string.no_requests;
    }

    @Override
    protected void updateViewHolder(ViewAdapter.RecyclerViewHolder holder, Friend friend) {

    }

    @Override
    protected void initializeButton(Button button) {
        button.setText(R.string.button_text_accept);
    }

    @Override
    protected void onClick(Button button, int adapterPosition) {
        FacebookFriendsPresenter facebookFriendsPresenter = getFacebookFriendsPresenter();
        button.setText(R.string.button_text_accepting);
        final Friend friend = facebookFriendsPresenter.get(
                facebookFriendsPresenter.indexOf(mRequestsPresenter.get(
                        adapterPosition)
                )
        );
        mRequestsPresenter.acceptRequest(friend);
    }

    @Override
    protected Friend getFriend(int position) {
        return mRequestsPresenter.get(position);
    }

    @Override
    protected int getFriendsCount() {
        return mRequestsPresenter.size();
    }

    @Override
    public void onStartRequestsLoading() {

    }

    @Override
    public void onRequestsInitialization() {
        showProgress();
    }

    @Override
    public void onRequestAdded(int index, Friend friend) {
        getAdapter().notifyItemInserted(index);
        cancelNotification(UniqueId.getRequestId(friend));
    }

    @Override
    public void onRequestChanged(int index, Friend friend) {
        getAdapter().notifyItemChanged(index);
    }

    @Override
    public void onRequestRemoved(int index, Friend friend) {
        getAdapter().notifyItemRemoved(index);
        cancelNotification(UniqueId.getRequestId(friend));
    }

    @Override
    public void onCompleteRequestsLoading() {
        hideProgress();
    }

    @Override
    public void onStop() {
        super.onStop();

        mRequestsPresenter.pushSeenRequests(mRequestsPresenter.getAll());
        mRequestsPresenter = (RequestsPresenter) mRequestsPresenter.detach(this);
    }
}
