package com.example.rover;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.rover.MessageFragment.OnListFragmentInteractionListener;

import java.util.List;

import io.rover.model.Message;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Message} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class MyMessageRecyclerViewAdapter extends RecyclerView.Adapter<MyMessageRecyclerViewAdapter.ViewHolder> {

    private final List<Message> mValues;
    private final OnListFragmentInteractionListener mListener;
    private final ViewHolder.OnClickListener mClickListener;

    public MyMessageRecyclerViewAdapter(List<Message> items, OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
        mClickListener = new MessageClickListener();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mTitleView.setText(mValues.get(position).getTitle());
        holder.mContentView.setText(mValues.get(position).getText());
        holder.setOnClickListener(mClickListener);
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public void clear() {
        mValues.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Message> list) {
        mValues.addAll(list);
        notifyDataSetChanged();
    }

    private class MessageClickListener implements ViewHolder.OnClickListener {
        @Override
        public void onClick(View view, int position) {
            Message message = mValues.get(position);
            if (mListener != null) {
                mListener.onListFragmentInteraction(message);
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public interface OnClickListener {
            void onClick(View view, int position);
        }

        public final View mView;
        public final TextView mTitleView;
        public final TextView mContentView;
        public Message mItem;
        private OnClickListener mClickListener;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mTitleView = (TextView) view.findViewById(R.id.titleTextView);
            mContentView = (TextView) view.findViewById(R.id.messageTextView);
            mView.setOnClickListener(this);
        }

        public void setOnClickListener(OnClickListener listener) {
            mClickListener = listener;
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }

        @Override
        public void onClick(View v) {
            if (mClickListener != null) {
                mClickListener.onClick(v, getAdapterPosition());
            }
        }
    }
}
