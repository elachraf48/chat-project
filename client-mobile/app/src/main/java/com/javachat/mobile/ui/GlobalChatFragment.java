package com.javachat.mobile.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.javachat.mobile.R;
import com.javachat.mobile.ai.GeminiChatManager;
import com.javachat.mobile.audio.AudioManager;
import com.javachat.mobile.auth.AuthenticationManager;
import com.javachat.mobile.network.ConnectionManager;
import com.javachat.mobile.protocol.Message;
import com.javachat.mobile.protocol.MessageType;
import com.javachat.mobile.transfer.FileTransferManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The main chat screen fragment.
 *
 * Fixes applied:
 *  - BUG 3: own messages no longer added locally; sender sees the server echo only.
 *  - BUG 4: AI dialog is stored and checked for isShowing() before updating view.
 *  - BUG 6: system messages use MessageType.ERROR (non-presence type) not USER_JOINED.
 *  - IMP 11: FILE_COMPLETE adds a visible chat entry confirming the file was saved.
 *  - IMP 12: AUDIO_COMPLETE adds a visible chat entry with a play button.
 */
public class GlobalChatFragment extends Fragment {

    private RecyclerView rvMessages;
    private EditText etMessageInput;
    private Button btnSend;
    private ImageButton btnAttach;
    private ImageButton btnRecord;
    private ImageButton btnAi;

    private MessageAdapter messageAdapter;
    private OnlineUsersAdapter onlineUsersAdapter;
    private final List<Message> messages = new ArrayList<>();
    private final List<String> onlineUsers = new ArrayList<>();

    private ActivityResultLauncher<String[]> filePickerLauncher;

    /** BUG 4: hold reference to the AI dialog so we can guard against dangling views. */
    private AlertDialog aiDialog;
    private TextView tvAiResponse;
    private ProgressBar progressAi;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onFileSelected);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_global_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rvMessages     = view.findViewById(R.id.rvMessages);
        etMessageInput = view.findViewById(R.id.etMessageInput);
        btnSend        = view.findViewById(R.id.btnSend);
        btnAttach      = view.findViewById(R.id.btnAttach);
        btnRecord      = view.findViewById(R.id.btnRecord);
        btnAi          = view.findViewById(R.id.btnAi);

        String myUsername = AuthenticationManager.getInstance().getUsername();
        messageAdapter = new MessageAdapter(messages, myUsername, this::onDownloadFile);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(messageAdapter);

        onlineUsersAdapter = new OnlineUsersAdapter(onlineUsers);
        RecyclerView rvOnlineUsers = requireActivity().findViewById(R.id.rvOnlineUsers);
        if (rvOnlineUsers != null) {
            rvOnlineUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvOnlineUsers.setAdapter(onlineUsersAdapter);
        }

        btnSend.setOnClickListener(v -> sendTextMessage());
        btnAttach.setOnClickListener(v -> openFilePicker());
        btnRecord.setOnClickListener(v -> showAudioDialog());
        btnAi.setOnClickListener(v -> showAiDialog());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // BUG 4: dismiss dialog when view is destroyed to avoid window leak
        if (aiDialog != null && aiDialog.isShowing()) {
            aiDialog.dismiss();
        }
        aiDialog = null;
    }

    // -------------------------------------------------------------------------
    // Public API — called by ChatActivity
    // -------------------------------------------------------------------------

    public void handleMessage(Message message) {
        if (message == null) return;

        switch (message.getType()) {
            case CHAT:
                addMessage(message);
                break;

            case USER_JOINED:
                addSystemMessage(message.getSender() + " joined");
                addOnlineUser(message.getSender());
                break;

            case USER_LEFT:
                addSystemMessage(message.getSender() + " left");
                removeOnlineUser(message.getSender());
                break;

            case FILE_META:
            case FILE_CHUNK:
                FileTransferManager.getInstance().handleIncoming(message, requireContext());
                break;

            case FILE_COMPLETE: {
                // Assemble bytes first, then show a file bubble (receiver taps Download to save)
                String fileName = FileTransferManager.getInstance()
                        .getFileNameForId(message.getFileId());
                FileTransferManager.getInstance().handleIncoming(message, requireContext());
                addFileMessage(message.getSender(), message.getFileId(),
                        fileName, message.getTimestamp());
                break;
            }

            case AUDIO_META:
            case AUDIO_CHUNK:
                AudioManager.getInstance().handleIncoming(message, requireContext());
                break;

            case AUDIO_COMPLETE:
                // IMP 12: show an audio message entry in the chat
                AudioManager.getInstance().handleIncoming(message, requireContext());
                addAudioMessage(message);
                break;

            case AI_RESPONSE:
                GeminiChatManager.getInstance().handleResponse(message);
                break;

            default:
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Send
    // -------------------------------------------------------------------------

    private void sendTextMessage() {
        String text = etMessageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        String username = AuthenticationManager.getInstance().getUsername();
        Message msg = Message.chat(username, text);
        ConnectionManager.getInstance().send(msg);
        etMessageInput.setText("");
        addMessage(msg);   // BUG 1 fix: server uses broadcastExcept, so add own message locally
    }

    private void openFilePicker() {
        filePickerLauncher.launch(new String[]{"*/*"});
    }

    private void onFileSelected(Uri uri) {
        if (uri == null) return;
        String myUsername = AuthenticationManager.getInstance().getUsername();
        FileTransferManager.getInstance().sendFile(uri, requireContext(),
                new FileTransferManager.TransferCallback() {
                    @Override
                    public void onSendComplete(String fileName) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            addFileMessage(myUsername, null, fileName,
                                    java.time.Instant.now().toString());
                        });
                    }
                    @Override
                    public void onSendFailed(String fileName) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            addSystemMessage(getString(R.string.file_send_failed, fileName));
                        });
                    }
                });
    }

    private void showAudioDialog() {
        AudioManager.getInstance().showRecordDialog(requireActivity(), this::onAudioReady);
    }

    private void onAudioReady(Uri audioUri) {
        String myUsername = AuthenticationManager.getInstance().getUsername();
        Context ctx = requireContext().getApplicationContext();   // safe across config changes
        AudioManager.getInstance().sendAudio(audioUri, ctx, fileId -> {
            // IMP 2 fix: show an audio bubble for the sender after upload completes
            if (!isAdded()) return;                               // fragment may be detached by now
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                Message m = new Message();
                m.setType(MessageType.AUDIO_COMPLETE);
                m.setSender(myUsername);
                m.setFileId(fileId);
                m.setTimestamp(java.time.Instant.now().toString());
                addMessage(m);
            });
        });
    }

    private void showAiDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_ai, null);
        tvAiResponse = dialogView.findViewById(R.id.tvAiResponse);
        progressAi   = dialogView.findViewById(R.id.progressAi);
        EditText etPrompt = dialogView.findViewById(R.id.etAiPrompt);
        Button btnAsk = dialogView.findViewById(R.id.btnAskAi);

        // Use a dialog with only a Close button. Asking is done via btnAsk inside
        // the dialog view so we can control dismissal ourselves (setPositiveButton
        // auto-dismisses before the listener fires, making isShowing() always false).
        aiDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ai_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        if (btnAsk != null) {
            btnAsk.setOnClickListener(v -> {
                String prompt = etPrompt.getText().toString().trim();
                if (prompt.isEmpty()) return;
                if (progressAi != null) progressAi.setVisibility(View.VISIBLE);
                if (tvAiResponse != null) tvAiResponse.setVisibility(View.GONE);
                btnAsk.setEnabled(false);
                GeminiChatManager.getInstance().sendRequest(prompt, response -> {
                    if (aiDialog != null && aiDialog.isShowing()) {
                        if (tvAiResponse != null) {
                            tvAiResponse.setText(response);
                            tvAiResponse.setVisibility(View.VISIBLE);
                        }
                        if (progressAi != null) progressAi.setVisibility(View.GONE);
                        btnAsk.setEnabled(true);
                    }
                });
            });
        }

        aiDialog.show();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void addMessage(Message message) {
        messages.add(message);
        int pos = messages.size() - 1;
        messageAdapter.notifyItemInserted(pos);
        // scrollToPosition is instant but may not update the viewport if the layout
        // has not yet measured the new item. Post to queue to run after layout pass.
        rvMessages.post(() -> rvMessages.scrollToPosition(pos));
    }

    /**
     * BUG 6: system events use MessageType.ERROR (not USER_JOINED) so that
     * MessageAdapter correctly classifies them as VIEW_TYPE_SYSTEM regardless
     * of future changes to USER_JOINED handling.
     */
    private void addSystemMessage(String text) {
        Message m = new Message();
        m.setType(MessageType.ERROR);   // ERROR → VIEW_TYPE_SYSTEM in adapter
        m.setText(text);
        addMessage(m);
    }

    /**
     * Add a FILE_COMPLETE entry so MessageAdapter renders a file bubble.
     * Sender (isSelf) sees a right-aligned bubble; receiver sees left-aligned + Download button.
     */
    private void addFileMessage(String sender, String fileId, String fileName, String timestamp) {
        Message m = new Message();
        m.setType(MessageType.FILE_COMPLETE);
        m.setSender(sender);
        m.setFileId(fileId);
        m.setFileName(fileName != null ? fileName : "file");
        m.setTimestamp(timestamp);
        addMessage(m);
    }

    /**
     * Add an AUDIO_COMPLETE entry that MessageAdapter renders with a play button.
     */
    private void addAudioMessage(Message message) {
        // Wrap as AUDIO_COMPLETE so MessageAdapter can show the audio item layout
        Message m = new Message();
        m.setType(MessageType.AUDIO_COMPLETE);
        m.setSender(message.getSender());
        m.setFileId(message.getFileId());
        m.setTimestamp(message.getTimestamp());
        addMessage(m);
    }

    /**
     * DownloadClickListener — called by MessageAdapter when user taps Download on a file bubble.
     * Saves the file, then marks the message tag="saved" so the button changes to "✓ Saved".
     */
    private void onDownloadFile(Message message) {
        FileTransferManager.getInstance().saveReceivedFile(message.getFileId(), requireContext());
        message.setTag("saved");
        // Refresh the specific item so the Download button updates to "✓ Saved"
        int idx = messages.indexOf(message);
        if (idx >= 0) messageAdapter.notifyItemChanged(idx);
    }

    /** Called by ChatActivity after the fragment is attached so the user sees themselves in the list. */
    public void addSelf(String username) {
        addOnlineUser(username);
    }

    private void addOnlineUser(String username) {
        if (!onlineUsers.contains(username)) {
            onlineUsers.add(username);
            onlineUsersAdapter.notifyItemInserted(onlineUsers.size() - 1);
        }
    }

    private void removeOnlineUser(String username) {
        int idx = onlineUsers.indexOf(username);
        if (idx >= 0) {
            onlineUsers.remove(idx);
            onlineUsersAdapter.notifyItemRemoved(idx);
        }
    }
}
