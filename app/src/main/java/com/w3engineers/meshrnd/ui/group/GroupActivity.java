package com.w3engineers.meshrnd.ui.group;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.w3.meshlib.client.WiFiDirectClient;
import com.w3.meshlib.common.GroupDevice;
import com.w3.meshlib.common.GroupServiceDevice;
import com.w3.meshlib.common.WiFiDirectBroadcastReceiver;
import com.w3.meshlib.common.WiFiP2PError;
import com.w3.meshlib.common.WiFiP2PInstance;
import com.w3.meshlib.common.listeners.ServiceConnectedListener;
import com.w3.meshlib.common.listeners.ServiceDiscoveredListener;
import com.w3.meshlib.common.listeners.ServiceRegisteredListener;
import com.w3.meshlib.service.WiFiDirectService;
import com.w3engineers.meshrnd.R;

import java.util.ArrayList;
import java.util.List;

public class GroupActivity extends AppCompatActivity implements GroupCreationDialog.GroupCreationAcceptButtonListener {

    private static final String TAG = GroupActivity.class.getSimpleName();

    private WiFiDirectBroadcastReceiver wiFiDirectBroadcastReceiver;
    private WiFiDirectService wroupService;
    private WiFiDirectClient wroupClient;

    private GroupCreationDialog groupCreationDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        wiFiDirectBroadcastReceiver = WiFiP2PInstance.getInstance(this).getBroadcastReceiver();

        Button btnCreateGroup = (Button) findViewById(R.id.btnCreateGroup);
        Button btnJoinGroup = (Button) findViewById(R.id.btnJoinGroup);

        btnCreateGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                groupCreationDialog = new GroupCreationDialog();
                groupCreationDialog.addGroupCreationAcceptListener(GroupActivity.this);
                groupCreationDialog.show(getSupportFragmentManager(), GroupCreationDialog.class.getSimpleName());
            }
        });

        btnJoinGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchAvailableGroups();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        registerReceiver(wiFiDirectBroadcastReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(wiFiDirectBroadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (wroupService != null) {
            wroupService.disconnect();
        }

        if (wroupClient != null) {
            wroupClient.disconnect();
        }
    }

    @Override
    public void onAcceptButtonListener(final String groupName) {
        if (!groupName.isEmpty()) {
            wroupService = WiFiDirectService.getInstance(getApplicationContext());
            wroupService.registerService(groupName, new ServiceRegisteredListener() {

                @Override
                public void onSuccessServiceRegistered() {
                    Log.i(TAG, "Group created. Launching GroupChatActivity...");
                    startGroupChatActivity(groupName, true);
                    groupCreationDialog.dismiss();
                }

                @Override
                public void onErrorServiceRegistered(WiFiP2PError wiFiP2PError) {
                    Toast.makeText(getApplicationContext(), "Error creating group", Toast.LENGTH_SHORT).show();
                }

            });
        } else {
            Toast.makeText(getApplicationContext(), "Please, insert a group name", Toast.LENGTH_SHORT).show();
        }
    }

    private void searchAvailableGroups() {
        final ProgressDialog progressDialog = new ProgressDialog(GroupActivity.this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage(getString(R.string.prgrss_searching_groups));
        progressDialog.show();

        wroupClient = WiFiDirectClient.getInstance(getApplicationContext());
        wroupClient.discoverServices(5000L, new ServiceDiscoveredListener() {

            @Override
            public void onNewServiceDeviceDiscovered(GroupServiceDevice serviceDevice) {
                Log.i(TAG, "New group found:");
                Log.i(TAG, "\tName: " + serviceDevice.getTxtRecordMap().get(WiFiDirectService.SERVICE_GROUP_NAME));
            }

            @Override
            public void onFinishServiceDeviceDiscovered(List<GroupServiceDevice> serviceDevices) {
                Log.i(TAG, "Found '" + serviceDevices.size() + "' groups");
                progressDialog.dismiss();

                if (serviceDevices.isEmpty()) {
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_not_found_groups),Toast.LENGTH_LONG).show();
                } else {
                    showPickGroupDialog(serviceDevices);
                }
            }

            @Override
            public void onError(WiFiP2PError wiFiP2PError) {
                Toast.makeText(getApplicationContext(), "Error searching groups: " + wiFiP2PError, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showPickGroupDialog(final List<GroupServiceDevice> devices) {
        List<String> deviceNames = new ArrayList<>();
        for (GroupServiceDevice device : devices) {
            deviceNames.add(device.getTxtRecordMap().get(WiFiDirectService.SERVICE_GROUP_NAME));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select a group");
        builder.setItems(deviceNames.toArray(new String[deviceNames.size()]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final GroupServiceDevice serviceSelected = devices.get(which);
                final ProgressDialog progressDialog = new ProgressDialog(GroupActivity.this);
                progressDialog.setMessage(getString(R.string.prgrss_connecting_to_group));
                progressDialog.setIndeterminate(true);
                progressDialog.show();

                wroupClient.connectToService(serviceSelected, new ServiceConnectedListener() {
                    @Override
                    public void onServiceConnected(GroupDevice serviceDevice) {
                        progressDialog.dismiss();
                        startGroupChatActivity(serviceSelected.getTxtRecordMap().get(WiFiDirectService.SERVICE_GROUP_NAME), false);
                    }
                });
            }
        });

        AlertDialog pickGroupDialog = builder.create();
        pickGroupDialog.show();
    }


    private void startGroupChatActivity(String groupName, boolean isGroupOwner) {
        Intent intent = new Intent(getApplicationContext(), GroupChatActivity.class);
        intent.putExtra(GroupChatActivity.EXTRA_GROUP_NAME, groupName);
        intent.putExtra(GroupChatActivity.EXTRA_IS_GROUP_OWNER, isGroupOwner);
        startActivity(intent);
    }
}
