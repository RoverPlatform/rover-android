package com.example.rover;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import io.rover.ExperienceActivity;
import io.rover.RemoteScreenActivity;
import io.rover.model.Message;
import io.rover.Rover;

public class MessageFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, MyMessageRecyclerViewAdapter.OnClickListener, MyMessageRecyclerViewAdapter.OnDeleteListener {

    // TODO: Customize parameter argument names
    private static final String ARG_COLUMN_COUNT = "column-count";
    // TODO: Customize parameters
    private int mColumnCount = 1;
    private SwipeRefreshLayout mSwipeLayout;
    private MyMessageRecyclerViewAdapter mAdapter;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public MessageFragment() {
    }

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static MessageFragment newInstance(int columnCount) {
        MessageFragment fragment = new MessageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_message_list, container, false);

        if (view instanceof SwipeRefreshLayout) {
            Context context = view.getContext();
            mSwipeLayout = (SwipeRefreshLayout)view;

            mSwipeLayout.setOnRefreshListener(this);
            mSwipeLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light,
                    android.R.color.holo_red_light);

            // Set the adapter
            RecyclerView recyclerView = (RecyclerView)view.findViewById(R.id.list);
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            mAdapter = new MyMessageRecyclerViewAdapter(new ArrayList<Message>());
            mAdapter.setOnClickListener(this);
            mAdapter.setOnDeleteMessageListener(this);
            recyclerView.setAdapter(mAdapter);
        }
        return view;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        onRefresh();
    }

    @Override
    public void onRefresh() {
        Rover.reloadInbox(new Rover.OnInboxReloadListener() {
            public void onSuccess(List<Message> messages) {
                mAdapter.clear();
                mAdapter.addAll(messages);
                mSwipeLayout.setRefreshing(false);
            }

            public void onFailure() {
                mSwipeLayout.setRefreshing(false);
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onClick(Message message) {
        switch (message.getAction()) {
            case LandingPage: {
                Intent intent = new Intent(getActivity(), RemoteScreenActivity.class);
                intent.putExtra(RemoteScreenActivity.INTENT_EXTRA_SCREEN, message.getLandingPage());
                startActivity(intent);
                break;
            }
            case Experience: {
                Intent intent = new Intent(getActivity(), ExperienceActivity.class);
                intent.setData(message.getExperienceUri());
                startActivity(intent);
                break;
            }
        }
    }

    @Override
    public void onDelete(final Message message) {
        Rover.deleteMessage(message, new Rover.OnDeleteMessageListener() {
            @Override
            public void onSuccess() {
                mAdapter.remove(message);
            }

            @Override
            public void onFailure() {
                Log.w("Rover", "Failed to delete message");
            }
        });
    }
}
