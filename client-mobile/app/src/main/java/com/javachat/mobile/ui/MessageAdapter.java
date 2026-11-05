package com.javachat.mobile.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.javachat.mobile.R;
import com.javachat.mobile.audio.AudioManager;
import com.javachat.mobile.protocol.Message;
import com.javachat.mobile.protocol.MessageType;

import java.util.List;

/**
 * RecyclerView adapter for chat messages. Four view types:
 *  0 = Self message     (right-aligned, blue)
 *  1 = Other message    (left-aligned, grey)
 *  2 = System/event     (centered, italic)
 *  3 = Audio message    (play button row) — IMP 12
 *
 * Fix BUG 6: system messages now use MessageType.ERROR so USER_JOINED/LEFT
 * can be treated distinctly from generic system text if needed in the future.
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_SELF   = 0;
    private static final int VIEW_TYPE_OTHER  = 1;
    private static final int VIEW_TYPE_SYSTEM = 2;
    private static final int VIEW_TYPE_AUDIO  = 3;  // IMP 12

    private final List<Message> messages;
    private final String myUsername;

    public MessageAdapter(List<Message> messages, String myUsername) {
        this.messages   = messages;
        this.myUsername = myUsername;
    }

    @Override
    public int getItemViewType(int position) {
        Message m = messages.get(position);
        MessageType type = m.getType();

        // IMP 12: audio messages get their own view
        if (type == MessageType.AUDIO_COMPLETE) return VIEW_TYPE_AUDIO;

        // BUG 6: system events — ERROR is used by addSystemMessage()
        if (type == MessageType.USER_JOINED
                || type == MessageType.USER_LEFT
                || type == MessageType.ERROR) {
            return VIEW_TYPE_SYSTEM;
        }

        if (myUsername != null && myUsername.equals(m.getSender())) return VIEW_TYPE_SELF;
        return VIEW_TYPE_OTHER;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == VIEW_TYPE_AUDIO)
                ? R.layout.item_audio_message
                : R.layout.item_message;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message m = messages.get(position);
        int viewType = getItemViewType(position);

        if (viewType == VIEW_TYPE_AUDIO) {
            // IMP 12: bind audio row — play button launches AudioManager.playAudio
            String label = (m.getSender() != null ? m.getSender() + ": " : "")
                    + holder.itemView.getContext().getString(R.string.audio_received);
            if (holder.tvAudioLabel != null) holder.tvAudioLabel.setText(label);
            if (holder.btnPlayAudio != null) {
                holder.btnPlayAudio.setOnClickListener(v -> {
                    // Audio is stored in cache; fileId is used as the filename key
                    // Playback via AudioManager using cached file URI
                    AudioManager.getInstance().playAudioById(holder.itemView.getContext(),
                            m.getFileId());
                });
            }
            return;
        }

        holder.layoutSelf.setVisibility(View.GONE);
        holder.layoutOther.setVisibility(View.GONE);
        holder.tvSystem.setVisibility(View.GONE);

        if (viewType == VIEW_TYPE_SELF) {
            holder.layoutSelf.setVisibility(View.VISIBLE);
            holder.tvTextSelf.setText(m.getText());
            holder.tvTimeSelf.setText(formatTime(m.getTimestamp()));

        } else if (viewType == VIEW_TYPE_OTHER) {
            holder.layoutOther.setVisibility(View.VISIBLE);
            holder.tvSenderName.setText(m.getSender() != null ? m.getSender() : "");
            holder.tvTextOther.setText(m.getText());
            holder.tvTimeOther.setText(formatTime(m.getTimestamp()));

        } else {
            holder.tvSystem.setVisibility(View.VISIBLE);
            holder.tvSystem.setText(m.getText() != null ? m.getText() : "");
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private String formatTime(String iso) {
        if (iso == null || iso.length() < 19) return "";
        return iso.substring(11, 16);
    }

    // -------------------------------------------------------------------------

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        // Normal message views
        View layoutSelf;
        View layoutOther;
        TextView tvSystem;
        TextView tvTextSelf;
        TextView tvTimeSelf;
        TextView tvSenderName;
        TextView tvTextOther;
        TextView tvTimeOther;

        // Audio row views (IMP 12)
        ImageButton btnPlayAudio;
        TextView tvAudioLabel;

        MessageViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            if (viewType == VIEW_TYPE_AUDIO) {
                btnPlayAudio = itemView.findViewById(R.id.btnPlayAudio);
                tvAudioLabel = itemView.findViewById(R.id.tvAudioLabel);
            } else {
                layoutSelf   = itemView.findViewById(R.id.layoutSelf);
                layoutOther  = itemView.findViewById(R.id.layoutOther);
                tvSystem     = itemView.findViewById(R.id.tvSystem);
                tvTextSelf   = itemView.findViewById(R.id.tvTextSelf);
                tvTimeSelf   = itemView.findViewById(R.id.tvTimeSelf);
                tvSenderName = itemView.findViewById(R.id.tvSenderName);
                tvTextOther  = itemView.findViewById(R.id.tvTextOther);
                tvTimeOther  = itemView.findViewById(R.id.tvTimeOther);
            }
        }
    }
}
