package growing.com.recording.data.data;

import com.google.firebase.crash.FirebaseCrash;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static growing.com.recording.BaseApplication.getAppData;

/**
 * File: PcScoketServer.java
 * Author: ejiang
 * 1.扫描二维码获取服务端ip 端口
 * 2.把本地ip地址上传服务端Scoket
 * <p>
 * Version: V100R001C01
 * Create: 2017-06-15 10:50
 */

public class PcScoketServer {

    private pcScoketThread mPcScoketThread;
    private Socket mSocket;

    public PcScoketServer() {
        mPcScoketThread = new pcScoketThread();
    }

    private final Object mLock = new Object();

    private class pcScoketThread extends Thread {
        public pcScoketThread() {
            super(pcScoketThread.class.getName());
        }

        public void run() {
            while (!isInterrupted()) { //是否断开链接
                synchronized (mLock) {
                    try {
                        if (connect()) break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private boolean connect() throws IOException {
            mSocket = new Socket();
            mSocket.setKeepAlive(true);
            mSocket.setSoTimeout(60 * 1000);//inputStream read 超时时间
            mSocket.setTcpNoDelay(true);
            mSocket.connect(new InetSocketAddress(getAppData().getStrSocketUrl(), getAppData().getStrSocketProt()));
            if (mSocket.isConnected()) {
//                OutputStream stream = mSocket.getOutputStream();
//                InetAddress address = getAppData().getIpAddress();
//                int port = getAppPreference().getSeverPort();
//                String socketStr = address + ":" + port;
//                byte[] bytes = socketStr.getBytes();
//                stream.write(bytes);
//                stream.flush();
//                if (getMainActivityViewModel().isStreaming()) {
////                    EventBus.getDefault().post(new BusMessages(BusMessages.MESSAGE_ACTION_STREAMING_STOP));
//                    EventBus.getDefault().post(new BusMessages(BusMessages.MESSAGE_ACTION_STREAMING_TRY_START));
//                } else {
////                    EventBus.getDefault().post(new BusMessages(BusMessages.MESSAGE_ACTION_STREAMING_TRY_START));
//                }
                return true;
            } else {
                return false;
            }
        }
    }

    public void start() {
        try {
            if (mPcScoketThread.isAlive()) return;
            if (getAppData().getIpAddress() == null) return;
            if (getAppData().getStrSocketUrl() == null) return;
            if (getAppData().getStrSocketProt() == 0) return;
            mPcScoketThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void stop() {
        if (!mPcScoketThread.isAlive()) return;
        mPcScoketThread.interrupt();
        synchronized (mLock) {
            try {
                if (mSocket != null) {
                    mSocket.close();
                }
            } catch (IOException e) {
                FirebaseCrash.report(e);
            }
            mSocket = null;
            mPcScoketThread = new pcScoketThread();
        }
    }
}