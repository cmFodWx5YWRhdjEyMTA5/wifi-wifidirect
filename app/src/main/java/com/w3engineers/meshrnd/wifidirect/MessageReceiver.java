package com.w3engineers.meshrnd.wifidirect;

/*
 *  ****************************************************************************
 *  * Created by : Md. Azizul Islam on 1/14/2019 at 6:25 PM.
 *  * Email : azizul@w3engineers.com
 *  *
 *  * Purpose:
 *  *
 *  * Last edited by : Md. Azizul Islam on 1/14/2019.
 *  *
 *  * Last Reviewed by : <Reviewer Name> on <mm/dd/yy>
 *  ****************************************************************************
 */

import android.content.Context;

import com.w3engineers.meshrnd.model.UserModel;
import com.w3engineers.meshrnd.util.AppLog;
import com.w3engineers.meshrnd.util.Common;
import com.w3engineers.meshrnd.util.Constants;
import com.w3engineers.meshrnd.util.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class MessageReceiver implements Runnable {
    /**
     * Instance variable
     */

    private Context context;
    private ServerSocket serverSocket;
    private int post;
    private Thread thread;
    private boolean isRunning;
    private WifiDirectDataListener wifiDirectDataListener;

    /**
     * <p>Constructor and thread init</p>
     *
     * @param context : context
     */
    public MessageReceiver(Context context, WifiDirectDataListener listener) {
        this.context = context;
        this.post = Common.MESSAGING_PORT;
        this.thread = new Thread(this, "msg_receiver");
        this.wifiDirectDataListener = listener;
    }

    /**
     * <p>Msg receiver thread start</p>
     */
    public void startReceiver() {
        if (isRunning) return;
        isRunning = true;
        thread.start();
    }

    /**
     * <p>Stop receiver thread </p>
     */
    public void stopReceiver() {
        try {
            if (!isRunning) return;
            isRunning = false;
            thread.interrupt();
        } catch (Exception e) {
        }


    }

    @Override
    public void run() {
        try {
            AppLog.v("Message receiver started");
            serverSocket = new ServerSocket(post);
            serverSocket.setReuseAddress(true);
            if (serverSocket != null && !serverSocket.isBound()) {
                serverSocket.bind(new InetSocketAddress(post));
            }
            Socket socket = null;
            while (isRunning) {
                AppLog.v("Receiver running");
                socket = serverSocket.accept();
                handleData(socket.getInetAddress().getHostAddress(), socket.getInputStream());
            }
            socket.close();
            socket = null;

        } catch (IOException e) {
            AppLog.v("Message receiver IOException");
        }
    }

    private void handleData(String ipAddress, InputStream inputStream) {
        byte[] data = getInputStreamByteArray(inputStream);

        String jsonString = new String(data);
        AppLog.v("Message received form =" + jsonString);

        int messageType = JsonParser.getScanType(jsonString);

        if (wifiDirectDataListener != null &&(messageType == Common.SCAN_REQ || messageType == Common.SCAN_RES)) {

            UserModel userModel = JsonParser.parseUserData(jsonString, ipAddress);
            wifiDirectDataListener.onUserFound(userModel);
        }

        if (messageType == Common.SCAN_REQ) {
            wifiDirectDataListener.sendResponseInfo(ipAddress);
            wifiDirectDataListener.onSendListUsers(ipAddress);
        } else if (messageType == Common.TEXT_MESSAGE) {
            wifiDirectDataListener.onMessageReceived(jsonString);

        }else if(messageType == Common.TYPE_USER_LIST){
            List<UserModel> userModelList = JsonParser.parseJsonArray(jsonString, ipAddress);
            wifiDirectDataListener.onUserFound(userModelList);
            AppLog.v("List users =" + userModelList.size());
        }

    }

    public byte[] getInputStreamByteArray(InputStream input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len;

        try {
            while ((len = input.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return (baos.toByteArray());
    }


}
