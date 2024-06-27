package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import java.util.ArrayDeque;
import java.util.List;

//Notification
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.RingtoneManager;
import android.net.Uri;
//Notification



public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }
    private String deviceAddress;
    private SerialService service;
    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    //Extra
    private final List<String> splitData = new ArrayList<>();
    private final StringBuilder receivedData = new StringBuilder();
    //Notification
    static final String NOTIFICATION_CHANNEL_ID = "data_notification_channel";
    //Extra

    //Notification
    private EditText deloadingThresholdInput;
    private Button setThresholdButton;
    private double deloadingThreshold = 20.0; // Default value
    private TextView currentThresholdDisplay;
    //Notification

    //Scroll
    private TextView pastDataText;
    private TextView liveDataText;
    //Scroll

    /*
     * Lifecycle
     */


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        assert getArguments() != null;
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        //Scroll
        pastDataText = view.findViewById(R.id.past_data_text);                           // TextView performance decreases with number of spans
        liveDataText = view.findViewById(R.id.live_data_text);
        pastDataText.setTextColor(getResources().getColor(R.color.colorRecieveText));  // set as default color to reduce number of spans
        //Scroll
        pastDataText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        //Notification
        deloadingThresholdInput = view.findViewById(R.id.deloading_threshold_input);
        setThresholdButton = view.findViewById(R.id.set_threshold_button);
        setThresholdButton.setOnClickListener(v -> setDeloadingThreshold());
        currentThresholdDisplay = view.findViewById(R.id.current_threshold_display);
        //Notification
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }


    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.hex).setChecked(hexEnabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.backgroundNotification).setChecked(service != null && service.areNotificationsEnabled());
        } else {
            menu.findItem(R.id.backgroundNotification).setChecked(true);
            menu.findItem(R.id.backgroundNotification).setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            pastDataText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else if (id == R.id.backgroundNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!service.areNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                } else {
                    showNotificationSettings();
                }
            }
            return true;
        }

        // ... (existing code)
        else if (id == R.id.action_new_page) {
            Intent intent = new Intent(getActivity(), SaveDataActivity.class);
            //intent.putExtra("receivedData", getReceivedData().toString());

            //Extra
            intent.putStringArrayListExtra("splitData", new ArrayList<>(splitData));
            //Extra
            startActivity(intent);
            return true;
        }
        else if (id == R.id.action_new_page2) {
            Intent intent = new Intent(getActivity(), GraphActivity.class);
            //intent.putExtra("receivedData", getReceivedData().toString());

            //Extra
            intent.putStringArrayListExtra("splitData", new ArrayList<>(splitData));
            //Extra
            startActivity(intent);
            return true;
        }
        // ... (existing code)
        else{
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            pastDataText.append(spn);
            service.write(data);

        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            if (hexEnabled) {
                spn.append(TextUtil.toHexString(data)).append('\n');
            } else {
                String msg = new String(data);
                if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                    // don't show CR as ^M if directly before LF
                    msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                    // special handling if CR and LF come in separate fragments
                    if (pendingNewline && msg.charAt(0) == '\n') {
                        if(spn.length() >= 2) {
                            spn.delete(spn.length() - 2, spn.length());
                        } else {
                            Editable edt = pastDataText.getEditableText();
                            if (edt != null && edt.length() >= 2)
                                edt.delete(edt.length() - 2, edt.length());
                        }
                    }
                    pendingNewline = msg.charAt(msg.length() - 1) == '\r';
                }
                spn.append(TextUtil.toCaretString(msg, newline.length() != 0));
            }
        }
        pastDataText.append(spn);
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        pastDataText.append(spn);
    }

    /*
     * starting with Android 14, notifications are not shown in notification bar by default when App is in background
     */

    private void showNotificationSettings() {
        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("android.provider.extra.APP_PACKAGE", getActivity().getPackageName());
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(Arrays.equals(permissions, new String[]{Manifest.permission.POST_NOTIFICATIONS}) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !service.areNotificationsEnabled())
            showNotificationSettings();
    }


    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;

        //Extra
        splitData.clear(); // Clear the splitData list on new connection
        //Extra
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
//        ArrayDeque<byte[]> datas = new ArrayDeque<>();
//        datas.add(data);
//        receive(datas);

        //Extra
        String receivedString = new String(data);
        String[] parts = receivedString.split(" "); // Split the string using the comma as the delimiter
        splitData.addAll(Arrays.asList(parts)); // Add the split parts to the splitData list
        updateUI();
        //Extra

        //Extra
        // receivedData.append(new String(data));
        //Extra

    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        //receive(datas);
        ///////////////extra////////////////
//        for (byte[] data : datas) {
//            receivedData.append(new String(data));
//        }
        /////////////////////////////////////////

        //Extra
        for (byte[] data : datas) {
            String receivedString = new String(data);
            String[] parts = receivedString.split(" "); // Split the string using the comma as the delimiter
            splitData.addAll(Arrays.asList(parts));           // Add the split parts to the splitData list

        }
        updateUI();
        //Extra
    }



    //Extra
//    private void updateUI() {
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < splitData.size(); i++) {
//            sb.append(i).append(": ").append(splitData.get(i)).append("\n");
//        }
//        receiveText.setText(sb.toString());
//    }

    //Notification
    private void setDeloadingThreshold() {
        String input = deloadingThresholdInput.getText().toString();
        if (!input.isEmpty()) {
            try {
                deloadingThreshold = Double.parseDouble(input);
                currentThresholdDisplay.setText(String.format("Current Deloading Threshold: %.1f%%", deloadingThreshold));
                Toast.makeText(getContext(), "Threshold set to " + deloadingThreshold + "%", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid input. Please enter a valid number.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "Please enter a threshold value.", Toast.LENGTH_SHORT).show();
        }
    }
    //Notification

    //Notification
    private void updateUI() {
        //StringBuilder sb = new StringBuilder();

        //Scroll
        StringBuilder pastDataBuilder = new StringBuilder(pastDataText.getText());
        StringBuilder liveDataBuilder = new StringBuilder();
        //scroll

        for (int i = 0; i < splitData.size(); i++) {
            //sb.append(i).append(": ").append(splitData.get(i)).append("\n");

            //scroll
            String dataItem1 = i + ": " + splitData.get(i) + "\n";
            //String dataItem2 = i + ": " + splitData.get(i-2) + "\n";

            if (i == splitData.size() - 1) {
                // This is the most recent data, so it goes in the live data TextView
                liveDataBuilder.append(i - 2).append(" weight1: ").append(splitData.get(i - 2)).append("\n");
                liveDataBuilder.append(i).append(" weight2: ").append(splitData.get(i));
            } else {
                // This is past data, so it goes in the scrolling TextView
                pastDataBuilder.append(dataItem1);
            }
            //scroll

            if ((i + 3) % 4 == 0 && i + 2 < splitData.size()) {
                double deloading;
                try {
                    double value1 = Double.parseDouble(splitData.get(i));
                    double value2 = Double.parseDouble(splitData.get(i + 2));
                    deloading = ((value1 - value2) / value1) * 100;
                    if (deloading < deloadingThreshold) {
                        showDataNotification(deloading);
                    }
                } catch (NumberFormatException e) {
                    // Handle the case when the values in splitData are not numeric
                    // You can log an error or show a Toast message
                    e.printStackTrace();
                }
            }
        }
        //pastDataText.setText(sb.toString());

        //Scroll
        pastDataText.setText(pastDataBuilder.toString());
        liveDataText.setText(liveDataBuilder.toString());
        // Scroll to the bottom of the past data
        final ScrollView scrollView = (ScrollView) pastDataText.getParent();
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        //Scroll
    }

        //Notification
        private void showDataNotification(double deloading) {
            createNotificationChannel();

            Intent intent = new Intent(getContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);

            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.waring)
                    .setContentTitle("Warning Notification")
                    .setContentText("You are in Danger & Deloading percentage: " + deloading + "%")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSound(defaultSoundUri)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(0, builder.build());
            }
        }

        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CharSequence name = getString(R.string.channel_name);
                String description = getString(R.string.channel_description);
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
                channel.setDescription(description);
                NotificationManager notificationManager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.createNotificationChannel(channel);
            }
        }
    //Extra

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();

    }

   // Extra
    //private StringBuilder receivedData = new StringBuilder();
    public StringBuilder getReceivedData() {
        return receivedData;
    }
}



