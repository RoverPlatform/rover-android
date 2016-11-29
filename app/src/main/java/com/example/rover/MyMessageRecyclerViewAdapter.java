package com.example.rover;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

import io.rover.model.Message;


public class MyMessageRecyclerViewAdapter extends RecyclerView.Adapter<MyMessageRecyclerViewAdapter.ViewHolder> {

    private final List<Message> mValues;
    private OnDeleteListener mDeleteListener;
    private OnClickListener mClickListener;

    public interface OnDeleteListener {
        void onDelete(Message message);
    }

    public interface OnClickListener {
        void onClick(Message message);
    }

    public MyMessageRecyclerViewAdapter(List<Message> items) {
        mValues = items;
    }

    public void setOnDeleteMessageListener(OnDeleteListener deleteListener) {
        mDeleteListener = deleteListener;
    }

    public void setOnClickListener(OnClickListener clickListener) {
        mClickListener = clickListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_message, parent, false);
        final ViewHolder holder = new ViewHolder(view);

        holder.mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int position = holder.getAdapterPosition();
                Message message = mValues.get(position);

                if (mDeleteListener != null) {
                    mDeleteListener.onDelete(message);
                }
            }
        });

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int position = holder.getAdapterPosition();
                Message message = mValues.get(position);

                if (mClickListener != null) {
                    mClickListener.onClick(message);
                }
            }
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        Message message = mValues.get(position);
        holder.mItem = mValues.get(position);
        holder.mTitleView.setText(message.getTitle());
        holder.mContentView.setText(message.getText());

        if (message.isRead()) {
            holder.mReadStatusView.setVisibility(View.INVISIBLE);
        } else {
            holder.mReadStatusView.setVisibility(View.VISIBLE);
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

    public void addAll(List<Message> list) {
        mValues.addAll(list);
        notifyDataSetChanged();
    }

    public void remove(Message message) {
        int position = mValues.indexOf(message);

        if (position >= 0) {
            mValues.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void messageUpdated(Message message) {
        int position = mValues.indexOf(message);

        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final View mView;
        public final TextView mTitleView;
        public final TextView mContentView;
        public final Button mDeleteButton;
        public final View mReadStatusView;

        public Message mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mTitleView = (TextView) view.findViewById(R.id.titleTextView);
            mContentView = (TextView) view.findViewById(R.id.messageTextView);
            mDeleteButton = (Button) view.findViewById(R.id.messageDeleteButton);
            mReadStatusView = view.findViewById(R.id.messageReadStatus);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }
}
