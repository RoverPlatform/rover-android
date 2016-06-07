package com.example.rover;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.example.rover.RegionFragment.OnRegionFragmentInteractionListener;
import com.google.android.gms.location.Geofence;

import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Geofence} and makes a call to the
 * specified {@link OnRegionFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class RegionRecyclerViewAdapter extends RecyclerView.Adapter<RegionRecyclerViewAdapter.ViewHolder> {

    private final List<Geofence> mValues;
    private final OnRegionFragmentInteractionListener mListener;

    public RegionRecyclerViewAdapter(List<Geofence> items, OnRegionFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_region, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        Geofence geofence = mValues.get(position);

        final String id = geofence.getRequestId();
        holder.mItem = mValues.get(position);
        holder.mIdView.setText("G");
        holder.mContentView.setText(id);

        if (mListener != null) {
            holder.mEnterButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onRegionFragmentEnterClick(id);
                }
            });
            holder.mExitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onRegionFragmentExitClick(id);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public void clear() {
        mValues.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Geofence> list) {
        mValues.addAll(list);
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mIdView;
        public final TextView mContentView;
        public final Button mEnterButton;
        public final Button mExitButton;
        public Geofence mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mIdView = (TextView) view.findViewById(R.id.id);
            mContentView = (TextView) view.findViewById(R.id.content);
            mEnterButton = (Button)view.findViewById(R.id.enterButton);
            mExitButton = (Button)view.findViewById(R.id.exitButton);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }
}
