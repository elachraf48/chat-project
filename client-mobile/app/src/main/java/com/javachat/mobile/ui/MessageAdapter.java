package com.javachat.mobile.ui;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
 * RecyclerView adapter for chat messages. Five view types:
 *  0 = VIEW_TYPE_SELF   — own text message  (right, green bubble)
 *  1 = VIEW_TYPE_OTHER  — other text message (left, white bubble)
 *  2 = VIEW_TYPE_SYSTEM — system/event pill  (centered)
 *  3 = VIEW_TYPE_AUDIO  — voice message bubble (self right / other left)
 *  4 = VIEW_TYPE_FILE   — file bubble with Download button (other) or sent label (self)
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_SELF   = 0;
    private static final int VIEW_TYPE_OTHER  = 1;
    private static final int VIEW_TYPE_SYSTEM = 2;
    private static final int VIEW_TYPE_AUDIO  = 3;
    private static final int VIEW_TYPE_FILE   = 4;

    public interface DownloadClickListener {
        void onDownload(Message message);
    }

    private final List<Message> messages;
    private final String myUsername;
    private final DownloadClickListener downloadListener;

    public MessageAdapter(List<Message> messages, String myUsername,
                          DownloadClickListener downloadListener) {
        this.messages         = messages;
        this.myUsername       = myUsername;
        this.downloadListener = downloadListener;
    }

    @Override
    public int getItemViewType(int position) {
        Message m = messages.get(position);
        MessageType type = m.getType();

        if (type == MessageType.AUDIO_COMPLETE) return VIEW_TYPE_AUDIO;
        if (type == MessageType.FILE_COMPLETE)  return VIEW_TYPE_FILE;

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
        int layout;
        switch (viewType) {
            case VIEW_TYPE_AUDIO:  layout = R.layout.item_audio_message; break;
            case VIEW_TYPE_FILE:   layout = R.layout.item_file_message;  break;
            default:               layout = R.layout.item_message;       break;
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message m = messages.get(position);
        int viewType = getItemViewType(position);

        switch (viewType) {

            case VIEW_TYPE_AUDIO: {
                boolean isSelf = myUsername != null && myUsername.equals(m.getSender());
                if (isSelf) {
                    show(holder.layoutAudioSelf);
                    hide(holder.tvAudioSenderName);
                    hide(holder.layoutAudioOther);
                    if (holder.btnPlayAudioSelf != null) {
                        Context appCtx = holder.itemView.getContext().getApplicationContext();
                        holder.btnPlayAudioSelf.setOnClickListener(v ->
                                AudioManager.getInstance().playAudioById(appCtx, m.getFileId()));
                    }
                } else {
                    hide(holder.layoutAudioSelf);
                    show(holder.tvAudioSenderName);
                    show(holder.layoutAudioOther);
                    if (holder.tvAudioSenderName != null)
                        holder.tvAudioSenderName.setText(m.getSender() != null ? m.getSender() : "");
                    if (holder.btnPlayAudioOther != null) {
                        Context appCtxOther = holder.itemView.getContext().getApplicationContext();
                        holder.btnPlayAudioOther.setOnClickListener(v ->
                                AudioManager.getInstance().playAudioById(appCtxOther, m.getFileId()));
                    }
                }
                break;
            }

            case VIEW_TYPE_FILE: {
                boolean isSelf = myUsername != null && myUsername.equals(m.getSender());
                String fileName = m.getFileName() != null ? m.getFileName() : "file";
                String sizeStr  = m.getFileSize() != null
                        ? Formatter.formatShortFileSize(
                                holder.itemView.getContext(), m.getFileSize()) : "";

                if (isSelf) {
                    show(holder.layoutFileSelf);
                    hide(holder.tvFileSenderName);
                    hide(holder.layoutFileOther);
                    if (holder.tvFileNameSelf != null) holder.tvFileNameSelf.setText(fileName);
                    if (holder.tvFileSizeSelf != null) holder.tvFileSizeSelf.setText(sizeStr);
                    if (holder.tvFileTimeSelf != null)
                        holder.tvFileTimeSelf.setText(formatTime(m.getTimestamp()));
                } else {
                    hide(holder.layoutFileSelf);
                    show(holder.tvFileSenderName);
                    show(holder.layoutFileOther);
                    if (holder.tvFileSenderName != null)
                        holder.tvFileSenderName.setText(m.getSender() != null ? m.getSender() : "");
                    if (holder.tvFileNameOther != null) holder.tvFileNameOther.setText(fileName);
                    if (holder.tvFileSizeOther != null) holder.tvFileSizeOther.setText(sizeStr);
                    if (holder.tvFileTimeOther != null)
                        holder.tvFileTimeOther.setText(formatTime(m.getTimestamp()));
                    if (holder.btnDownloadFile != null) {
                        // Always set state and listener on every bind to avoid stale
                        // click listeners from recycled views.
                        boolean saved = "saved".equals(m.getTag());
                        if (saved) {
                            holder.btnDownloadFile.setText(
                                    holder.itemView.getContext().getString(R.string.file_saved_to_downloads));
                            holder.btnDownloadFile.setEnabled(false);
                            holder.btnDownloadFile.setOnClickListener(null);
                        } else {
                            holder.btnDownloadFile.setText(
                                    holder.itemView.getContext().getString(R.string.btn_download));
                            holder.btnDownloadFile.setEnabled(true);
                            holder.btnDownloadFile.setOnClickListener(v -> {
                                if (downloadListener != null) downloadListener.onDownload(m);
                            });
                        }
                    }
                }
                break;
            }

            case VIEW_TYPE_SELF: {
                hide(holder.layoutOther);
                hide(holder.tvSystem);
                show(holder.layoutSelf);
                if (holder.tvTextSelf != null) holder.tvTextSelf.setText(m.getText());
                if (holder.tvTimeSelf != null) holder.tvTimeSelf.setText(formatTime(m.getTimestamp()));
                break;
            }

            case VIEW_TYPE_OTHER: {
                hide(holder.layoutSelf);
                hide(holder.tvSystem);
                show(holder.layoutOther);
                if (holder.tvSenderName != null)
                    holder.tvSenderName.setText(m.getSender() != null ? m.getSender() : "");
                if (holder.tvTextOther != null) holder.tvTextOther.setText(m.getText());
                if (holder.tvTimeOther != null) holder.tvTimeOther.setText(formatTime(m.getTimestamp()));
                break;
            }

            default: { // VIEW_TYPE_SYSTEM
                hide(holder.layoutSelf);
                hide(holder.layoutOther);
                show(holder.tvSystem);
                if (holder.tvSystem != null)
                    holder.tvSystem.setText(m.getText() != null ? m.getText() : "");
                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private String formatTime(String iso) {
        if (iso == null || iso.length() < 16) return "";
        return iso.substring(11, 16);
    }

    private static void show(View v) { if (v != null) v.setVisibility(View.VISIBLE); }
    private static void hide(View v) { if (v != null) v.setVisibility(View.GONE); }

    // -------------------------------------------------------------------------

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        // Text message views (item_message.xml)
        View     layoutSelf, layoutOther;
        TextView tvSystem, tvTextSelf, tvTimeSelf;
        TextView tvSenderName, tvTextOther, tvTimeOther;

        // Audio views (item_audio_message.xml)
        View        layoutAudioSelf, layoutAudioOther;
        TextView    tvAudioSenderName;
        ImageButton btnPlayAudioSelf, btnPlayAudioOther;

        // File views (item_file_message.xml)
        View     layoutFileSelf, layoutFileOther;
        TextView tvFileSenderName;
        TextView tvFileNameSelf, tvFileSizeSelf, tvFileTimeSelf;
        TextView tvFileNameOther, tvFileSizeOther, tvFileTimeOther;
        Button   btnDownloadFile;

        MessageViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            switch (viewType) {
                case 3: // AUDIO
                    layoutAudioSelf    = itemView.findViewById(R.id.layoutAudioSelf);
                    layoutAudioOther   = itemView.findViewById(R.id.layoutAudioOther);
                    tvAudioSenderName  = itemView.findViewById(R.id.tvAudioSenderName);
                    btnPlayAudioSelf   = itemView.findViewById(R.id.btnPlayAudioSelf);
                    btnPlayAudioOther  = itemView.findViewById(R.id.btnPlayAudioOther);
                    break;
                case 4: // FILE
                    layoutFileSelf    = itemView.findViewById(R.id.layoutFileSelf);
                    layoutFileOther   = itemView.findViewById(R.id.layoutFileOther);
                    tvFileSenderName  = itemView.findViewById(R.id.tvFileSenderName);
                    tvFileNameSelf    = itemView.findViewById(R.id.tvFileNameSelf);
                    tvFileSizeSelf    = itemView.findViewById(R.id.tvFileSizeSelf);
                    tvFileTimeSelf    = itemView.findViewById(R.id.tvFileTimeSelf);
                    tvFileNameOther   = itemView.findViewById(R.id.tvFileNameOther);
                    tvFileSizeOther   = itemView.findViewById(R.id.tvFileSizeOther);
                    tvFileTimeOther   = itemView.findViewById(R.id.tvFileTimeOther);
                    btnDownloadFile   = itemView.findViewById(R.id.btnDownloadFile);
                    break;
                default: // TEXT or SYSTEM
                    layoutSelf   = itemView.findViewById(R.id.layoutSelf);
                    layoutOther  = itemView.findViewById(R.id.layoutOther);
                    tvSystem     = itemView.findViewById(R.id.tvSystem);
                    tvTextSelf   = itemView.findViewById(R.id.tvTextSelf);
                    tvTimeSelf   = itemView.findViewById(R.id.tvTimeSelf);
                    tvSenderName = itemView.findViewById(R.id.tvSenderName);
                    tvTextOther  = itemView.findViewById(R.id.tvTextOther);
                    tvTimeOther  = itemView.findViewById(R.id.tvTimeOther);
                    break;
            }
        }
    }
}
