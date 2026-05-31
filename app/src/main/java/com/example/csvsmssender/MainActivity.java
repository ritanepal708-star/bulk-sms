package com.example.csvsmssender;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_CSV = 1001;
    private static final int REQUEST_SEND_SMS_PERMISSION = 2001;

    private final ArrayList<Contact> contacts = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText messageTemplateEdit;
    private EditText delaySecondsEdit;
    private TextView fileStatusText;
    private TextView previewText;
    private TextView logText;
    private Button sendButton;
    private Button stopButton;

    private boolean isSending = false;
    private int sendIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("CSV SMS Sender");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("Use only for contacts who agreed to receive your messages. CSV columns can be name, phone or just name,phone.");
        note.setTextSize(14);
        note.setPadding(0, 0, 0, dp(12));
        root.addView(note);

        TextView templateLabel = label("Message template. Use {name} for each contact name:");
        root.addView(templateLabel);

        messageTemplateEdit = new EditText(this);
        messageTemplateEdit.setMinLines(4);
        messageTemplateEdit.setGravity(Gravity.TOP | Gravity.START);
        messageTemplateEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageTemplateEdit.setText("Hi {name}, this is a message from CSV SMS Sender.");
        root.addView(messageTemplateEdit, matchWrap());

        TextView delayLabel = label("Delay between SMS messages, in seconds:");
        delayLabel.setPadding(0, dp(12), 0, 0);
        root.addView(delayLabel);

        delaySecondsEdit = new EditText(this);
        delaySecondsEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        delaySecondsEdit.setText("3");
        root.addView(delaySecondsEdit, matchWrap());

        Button pickButton = new Button(this);
        pickButton.setText("Choose CSV File");
        pickButton.setOnClickListener(v -> pickCsvFile());
        root.addView(pickButton, matchWrap());

        fileStatusText = new TextView(this);
        fileStatusText.setText("No CSV selected yet.");
        fileStatusText.setPadding(0, dp(8), 0, dp(8));
        root.addView(fileStatusText);

        previewText = new TextView(this);
        previewText.setText("Preview will appear here.");
        previewText.setTextIsSelectable(true);
        previewText.setPadding(0, dp(8), 0, dp(8));
        root.addView(previewText);

        sendButton = new Button(this);
        sendButton.setText("Preview & Send SMS");
        sendButton.setEnabled(false);
        sendButton.setOnClickListener(v -> startSendFlow());
        root.addView(sendButton, matchWrap());

        stopButton = new Button(this);
        stopButton.setText("Stop Sending");
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopSending());
        root.addView(stopButton, matchWrap());

        TextView logLabel = label("Log:");
        logLabel.setPadding(0, dp(12), 0, 0);
        root.addView(logLabel);

        logText = new TextView(this);
        logText.setText("Ready.");
        logText.setTextIsSelectable(true);
        root.addView(logText);

        return scrollView;
    }

    private TextView label(String value) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(15);
        return textView;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void pickCsvFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_PICK_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_CSV && resultCode == RESULT_OK && data != null && data.getData() != null) {
            loadCsv(data.getData());
        }
    }

    private void loadCsv(Uri uri) {
        try {
            List<List<String>> rows = readCsvRows(uri);
            contacts.clear();

            if (rows.isEmpty()) {
                showToast("CSV file is empty.");
                refreshPreview();
                return;
            }

            CsvColumns columns = detectColumns(rows.get(0));
            int startRow = columns.hasHeader ? 1 : 0;

            for (int i = startRow; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.isEmpty()) {
                    continue;
                }

                String name = getColumn(row, columns.nameIndex).trim();
                String phone = normalizePhone(getColumn(row, columns.phoneIndex));

                if (name.isEmpty()) {
                    name = "there";
                }

                if (isValidPhone(phone)) {
                    contacts.add(new Contact(name, phone));
                }
            }

            fileStatusText.setText("Loaded " + contacts.size() + " valid contacts.");
            sendButton.setEnabled(!contacts.isEmpty() && !isSending);
            refreshPreview();
        } catch (Exception e) {
            fileStatusText.setText("Could not read CSV: " + e.getMessage());
            contacts.clear();
            sendButton.setEnabled(false);
            refreshPreview();
        }
    }

    private List<List<String>> readCsvRows(Uri uri) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Unable to open selected file.");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }
        }
        return parseCsv(builder.toString());
    }

    private List<List<String>> parseCsv(String csv) {
        ArrayList<List<String>> rows = new ArrayList<>();
        ArrayList<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                currentRow.add(currentField.toString());
                currentField.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !inQuotes) {
                if (ch == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
                currentRow.add(currentField.toString());
                currentField.setLength(0);
                if (!isBlankRow(currentRow)) {
                    rows.add(new ArrayList<>(currentRow));
                }
                currentRow.clear();
            } else {
                currentField.append(ch);
            }
        }

        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString());
            if (!isBlankRow(currentRow)) {
                rows.add(currentRow);
            }
        }

        return rows;
    }

    private boolean isBlankRow(List<String> row) {
        for (String value : row) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private CsvColumns detectColumns(List<String> firstRow) {
        int nameIndex = -1;
        int phoneIndex = -1;

        for (int i = 0; i < firstRow.size(); i++) {
            String value = firstRow.get(i).trim().toLowerCase(Locale.US);
            if (nameIndex == -1 && (value.equals("name") || value.equals("full name") || value.equals("fullname"))) {
                nameIndex = i;
            }
            if (phoneIndex == -1 && (value.equals("phone") || value.equals("phone number") || value.equals("mobile") || value.equals("number") || value.equals("contact"))) {
                phoneIndex = i;
            }
        }

        boolean hasHeader = nameIndex != -1 || phoneIndex != -1;

        if (nameIndex == -1) {
            nameIndex = 0;
        }
        if (phoneIndex == -1) {
            phoneIndex = firstRow.size() > 1 ? 1 : 0;
        }

        return new CsvColumns(nameIndex, phoneIndex, hasHeader);
    }

    private String getColumn(List<String> row, int index) {
        if (index < 0 || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index);
    }

    private String normalizePhone(String rawPhone) {
        String phone = rawPhone == null ? "" : rawPhone.trim();
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < phone.length(); i++) {
            char ch = phone.charAt(i);
            if (ch == '+' && builder.length() == 0) {
                builder.append(ch);
            } else if (Character.isDigit(ch)) {
                builder.append(ch);
            }
        }

        return builder.toString();
    }

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^\\+?[0-9]{7,15}$");
    }

    private void refreshPreview() {
        if (contacts.isEmpty()) {
            previewText.setText("Preview will appear here.");
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("First contacts loaded:\n");
        int max = Math.min(5, contacts.size());
        for (int i = 0; i < max; i++) {
            Contact contact = contacts.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(contact.name)
                    .append(" — ")
                    .append(contact.phone)
                    .append('\n');
        }
        if (contacts.size() > max) {
            builder.append("... and ").append(contacts.size() - max).append(" more.");
        }
        previewText.setText(builder.toString());
    }

    private void startSendFlow() {
        if (contacts.isEmpty()) {
            showToast("Choose a CSV file first.");
            return;
        }

        String template = messageTemplateEdit.getText().toString().trim();
        if (template.isEmpty()) {
            showToast("Enter a message template first.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, REQUEST_SEND_SMS_PERMISSION);
            return;
        }

        showConfirmationDialog();
    }

    private void showConfirmationDialog() {
        int delaySeconds = getDelaySeconds();
        String firstMessage = buildMessage(contacts.get(0));

        new AlertDialog.Builder(this)
                .setTitle("Confirm SMS sending")
                .setMessage("This will send " + contacts.size() + " SMS messages with a " + delaySeconds + " second delay between each.\n\nFirst message preview:\n\n" + firstMessage)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (dialog, which) -> beginSending())
                .show();
    }

    private int getDelaySeconds() {
        try {
            int seconds = Integer.parseInt(delaySecondsEdit.getText().toString().trim());
            return Math.max(1, seconds);
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    private void beginSending() {
        isSending = true;
        sendIndex = 0;
        sendButton.setEnabled(false);
        stopButton.setEnabled(true);
        appendLog("Starting SMS send to " + contacts.size() + " contacts.");
        sendNext();
    }

    private void sendNext() {
        if (!isSending) {
            appendLog("Sending stopped.");
            sendButton.setEnabled(!contacts.isEmpty());
            stopButton.setEnabled(false);
            return;
        }

        if (sendIndex >= contacts.size()) {
            isSending = false;
            sendButton.setEnabled(!contacts.isEmpty());
            stopButton.setEnabled(false);
            appendLog("Done. Sent request completed for " + contacts.size() + " contacts.");
            return;
        }

        Contact contact = contacts.get(sendIndex);
        String message = buildMessage(contact);

        try {
            SmsManager smsManager = getSmsManagerCompat();
            ArrayList<String> messageParts = smsManager.divideMessage(message);
            smsManager.sendMultipartTextMessage(contact.phone, null, messageParts, null, null);
            appendLog("Queued " + (sendIndex + 1) + "/" + contacts.size() + ": " + contact.name + " — " + contact.phone);
        } catch (SecurityException securityException) {
            appendLog("Permission denied while sending to " + contact.phone + ".");
        } catch (Exception exception) {
            appendLog("Failed for " + contact.phone + ": " + exception.getMessage());
        }

        sendIndex++;
        int delayMillis = getDelaySeconds() * 1000;
        handler.postDelayed(this::sendNext, delayMillis);
    }

    private String buildMessage(Contact contact) {
        return messageTemplateEdit.getText().toString().replace("{name}", contact.name);
    }

    private SmsManager getSmsManagerCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SmsManager manager = getSystemService(SmsManager.class);
            if (manager != null) {
                return manager;
            }
        }
        return SmsManager.getDefault();
    }

    private void stopSending() {
        isSending = false;
        handler.removeCallbacksAndMessages(null);
        sendButton.setEnabled(!contacts.isEmpty());
        stopButton.setEnabled(false);
        appendLog("Stop requested by user.");
    }

    private void appendLog(String message) {
        String current = logText.getText().toString();
        if (current.equals("Ready.")) {
            current = "";
        }
        logText.setText(current + message + "\n");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SEND_SMS_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showConfirmationDialog();
            } else {
                showToast("SMS permission is required to send messages.");
            }
        }
    }

    private static class Contact {
        final String name;
        final String phone;

        Contact(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }

    private static class CsvColumns {
        final int nameIndex;
        final int phoneIndex;
        final boolean hasHeader;

        CsvColumns(int nameIndex, int phoneIndex, boolean hasHeader) {
            this.nameIndex = nameIndex;
            this.phoneIndex = phoneIndex;
            this.hasHeader = hasHeader;
        }
    }
}
