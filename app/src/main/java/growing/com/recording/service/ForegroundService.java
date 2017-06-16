package growing.com.recording.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import de.greenrobot.event.EventBus;
import de.greenrobot.event.Subscribe;
import growing.com.recording.MainActivity;
import growing.com.recording.R;
import growing.com.recording.data.data.BusMessages;
import growing.com.recording.data.data.HttpServer;
import growing.com.recording.data.data.NotifyImageGenerator;
import growing.com.recording.data.data.PcScoketServer;

import static growing.com.recording.BaseApplication.getAppData;
import static growing.com.recording.BaseApplication.getAppPreference;
import static growing.com.recording.BaseApplication.getMainActivityViewModel;
import static growing.com.recording.data.data.BusMessages.MESSAGE_ACTION_HTTP_RESTART;
import static growing.com.recording.data.data.BusMessages.MESSAGE_ACTION_PIN_UPDATE;
import static growing.com.recording.data.data.BusMessages.MESSAGE_ACTION_STREAMING_START;
import static growing.com.recording.data.data.BusMessages.MESSAGE_ACTION_STREAMING_STOP;
import static growing.com.recording.data.data.BusMessages.MESSAGE_ACTION_STREAMING_TRY_START;
import static growing.com.recording.data.data.BusMessages.MESSAGE_STATUS_HTTP_OK;


public final class ForegroundService extends Service {
    private static ForegroundService sServiceInstance;

    private static final int NOTIFICATION_START_STREAMING = 10;
    private static final int NOTIFICATION_STOP_STREAMING = 11;

    private static final String EXTRA_SERVICE_MESSAGE = "info.dvkr.screenstream.extras.EXTRA_SERVICE_MESSAGE";
    private static final String SERVICE_MESSAGE_PREPARE_STREAMING = "SERVICE_MESSAGE_PREPARE_STREAMING";

    private final String ACTION_NOTIFY_START_STREAM = "info.dvkr.screenstream.action.ACTION_NOTIFY_START_STREAM";
    private final String ACTION_NOTIFY_STOP_STREAM = "info.dvkr.screenstream.action.ACTION_NOTIFY_STOP_STREAM";
    private final String ACTION_NOTIFY_CLOSE_APP = "info.dvkr.screenstream.action.ACTION_NOTIFY_CLOSE_APP";

    private boolean isServicePrepared;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private MediaProjection.Callback mProjectionCallback;

    public static PcScoketServer getPcScoketServer() {
        return sServiceInstance.mPcScoketServer != null ? sServiceInstance.mPcScoketServer : null;
    }

    private HttpServer mHttpServer;
    private PcScoketServer mPcScoketServer;
    private NotifyImageGenerator mNotifyImageGenerator;
    private HandlerThread mHandlerThread;
    private ForegroundServiceHandler mForegroundServiceTaskHandler;
    private BroadcastReceiver mLocalNotificationReceiver;
    private BroadcastReceiver mBroadcastReceiver;

    public static Intent getStartIntent(final Context context) {
        return new Intent(context, ForegroundService.class)
                .setAction("forgroundService")
                .putExtra(EXTRA_SERVICE_MESSAGE, SERVICE_MESSAGE_PREPARE_STREAMING);
    }

    public static void stopService() {
        sServiceInstance.stopSelf();
    }

    public static void setMediaProjection(final MediaProjection mediaProjection) {
        sServiceInstance.mMediaProjection = mediaProjection;
    }

    @Nullable
    public static MediaProjectionManager getProjectionManager() {
        return sServiceInstance == null ? null : sServiceInstance.mMediaProjectionManager;
    }

    @Nullable
    public static MediaProjection getMediaProjection() {
        return sServiceInstance == null ? null : sServiceInstance.mMediaProjection;
    }


    @Override
    public void onCreate() {
        sServiceInstance = this;

        getAppData().initIndexHtmlPage(this);

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mProjectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                serviceStopStreaming();
            }
        };
        mHttpServer = new HttpServer();
        mPcScoketServer = new PcScoketServer();
        getAppData().getImageQueue().clear(); //清空缓存队列
        mNotifyImageGenerator = new NotifyImageGenerator(getApplicationContext());
        mNotifyImageGenerator.addDefaultScreen(); //添加数据到缓存队列

        // Starting thread Handler 开启handler线程
        mHandlerThread = new HandlerThread(
                ForegroundService.class.getSimpleName(),
                Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mHandlerThread.start();
        mForegroundServiceTaskHandler = new ForegroundServiceHandler(mHandlerThread.getLooper());

        //Local notifications
        final IntentFilter localNotificationIntentFilter = new IntentFilter();
        localNotificationIntentFilter.addAction(ACTION_NOTIFY_START_STREAM);
        localNotificationIntentFilter.addAction(ACTION_NOTIFY_STOP_STREAM);
        localNotificationIntentFilter.addAction(ACTION_NOTIFY_CLOSE_APP);

        mLocalNotificationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                switch (action) {
                    case ACTION_NOTIFY_START_STREAM:
                        if (!getAppData().isActivityRunning()) {
                            startActivity(MainActivity.getStartIntent(getApplicationContext())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }

                        final BusMessages stickyEvent = EventBus.getDefault().getStickyEvent(BusMessages.class);
                        if (stickyEvent == null || MESSAGE_STATUS_HTTP_OK.equals(stickyEvent.getMessage())) {
                            EventBus.getDefault().postSticky(new BusMessages(MESSAGE_ACTION_STREAMING_TRY_START));
                        }

                        break;
                    case ACTION_NOTIFY_STOP_STREAM:
                        serviceStopStreaming();
                        break;
                    case ACTION_NOTIFY_CLOSE_APP:
                        stopService();
                        System.exit(0);
                        break;
                }
            }
        };

        registerReceiver(mLocalNotificationReceiver, localNotificationIntentFilter);

        // Registering receiver for screen off messages and WiFi changes
        final IntentFilter screenOnOffAndWiFiFilter = new IntentFilter();
        screenOnOffAndWiFiFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenOnOffAndWiFiFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    if (getAppPreference().isStopOnSleep() && getAppData().isStreamRunning())
                        serviceStopStreaming();
                }

                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                    final boolean isWiFiConnected = getAppData().isWiFiConnected();
                    if (getMainActivityViewModel().isWiFiConnected() != isWiFiConnected) {
                        getMainActivityViewModel().setServerAddress(getAppData().getServerAddress());
                        getMainActivityViewModel().setWiFiConnected(isWiFiConnected);

                        if (getMainActivityViewModel().isWiFiConnected()) {
                            EventBus.getDefault().post(new BusMessages(MESSAGE_ACTION_HTTP_RESTART));
                        }

                        if ((!getMainActivityViewModel().isWiFiConnected()) && getAppData().isStreamRunning())
                            serviceStopStreaming();
                    }
                }
            }
        };

        registerReceiver(mBroadcastReceiver, screenOnOffAndWiFiFilter);

        EventBus.getDefault().register(this);
//        mHttpServer.start();
//        mPcScoketServer.start();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (SERVICE_MESSAGE_PREPARE_STREAMING.equals(intent.getStringExtra(EXTRA_SERVICE_MESSAGE))) {
            if (!isServicePrepared) {
                startForeground(NOTIFICATION_START_STREAMING, getNotificationStart());
            }
            isServicePrepared = true;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        mHttpServer.stop(null);
        mPcScoketServer.stop();
        stopForeground(true);
        unregisterReceiver(mBroadcastReceiver);
        unregisterReceiver(mLocalNotificationReceiver);
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mProjectionCallback);
            mMediaProjection.stop();
        }
        mHandlerThread.quit();
    }

    @Subscribe
    public void onMessageEvent(BusMessages busMessage) {
        switch (busMessage.getMessage()) {
            case MESSAGE_ACTION_STREAMING_START:
                serviceStartStreaming();
                break;
            case MESSAGE_ACTION_STREAMING_STOP:
                serviceStopStreaming();
                break;
            case MESSAGE_ACTION_HTTP_RESTART:
                getAppData().getImageQueue().clear();
                mHttpServer.stop(mNotifyImageGenerator.getClientNotifyImage(MESSAGE_ACTION_HTTP_RESTART));
                mPcScoketServer.stop();
                mNotifyImageGenerator.addDefaultScreen();
                mHttpServer.start();
                mPcScoketServer.start();

                break;
            case MESSAGE_ACTION_PIN_UPDATE:
                getAppData().getImageQueue().clear();
                mHttpServer.stop(mNotifyImageGenerator.getClientNotifyImage(MESSAGE_ACTION_PIN_UPDATE));
                mPcScoketServer.stop();
                mNotifyImageGenerator.addDefaultScreen();
                mHttpServer.start();
                mPcScoketServer.start();
                break;
            default:
                break;
        }
    }

    private void serviceStartStreaming() {
        if (getAppData().isStreamRunning()) return;
        stopForeground(true);
        mForegroundServiceTaskHandler.obtainMessage(ForegroundServiceHandler.HANDLER_START_STREAMING).sendToTarget();
        startForeground(NOTIFICATION_STOP_STREAMING, getNotificationStop());
        if (mMediaProjection != null) mMediaProjection.registerCallback(mProjectionCallback, null);
    }

    private void serviceStopStreaming() {
        if (!getAppData().isStreamRunning()) return;
        stopForeground(true);
        mForegroundServiceTaskHandler.obtainMessage(ForegroundServiceHandler.HANDLER_STOP_STREAMING).sendToTarget();
        startForeground(NOTIFICATION_START_STREAMING, getNotificationStart());
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mProjectionCallback);
            mMediaProjection.stop();
        }
        getAppData().getImageQueue().clear();
        mNotifyImageGenerator.addDefaultScreen();

        if (getAppPreference().isEnablePin() && getAppPreference().isAutoChangePin()) {
            getAppPreference().generateAndSaveNewPin();
            getMainActivityViewModel().setStreamPin(getAppPreference().getCurrentPin());
            EventBus.getDefault().post(new BusMessages(MESSAGE_ACTION_PIN_UPDATE));
        }
    }

    private Notification getNotificationStart() {
        final Intent mainActivityIntent =
                MainActivity.getStartIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        final PendingIntent pendingMainActivityIntent =
                PendingIntent.getActivity(this, 0, mainActivityIntent, 0);

        final NotificationCompat.Builder startNotificationBuilder = new NotificationCompat.Builder(this);
        startNotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        startNotificationBuilder.setSmallIcon(R.drawable.ic_service_notification_24dp);
        startNotificationBuilder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        startNotificationBuilder.setContentTitle(getString(R.string.service_ready_to_stream));
        startNotificationBuilder.setContentText(getString(R.string.service_press_start));
        startNotificationBuilder.setContentIntent(pendingMainActivityIntent);
        startNotificationBuilder.addAction(R.drawable.ic_service_start_24dp,
                getString(R.string.service_start).toUpperCase(),
                PendingIntent.getBroadcast(this, 0, new Intent(ACTION_NOTIFY_START_STREAM), 0));
        startNotificationBuilder.addAction(R.drawable.ic_service_exit_24dp,
                getString(R.string.service_exit).toUpperCase(),
                PendingIntent.getBroadcast(this, 0, new Intent(ACTION_NOTIFY_CLOSE_APP), 0));
        startNotificationBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
        return startNotificationBuilder.build();
    }

    private Notification getNotificationStop() {
        final Intent mainActivityIntent =
                MainActivity.getStartIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        final PendingIntent pendingMainActivityIntent =
                PendingIntent.getActivity(this, 0, mainActivityIntent, 0);

        final NotificationCompat.Builder stopNotificationBuilder = new NotificationCompat.Builder(this);
        stopNotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        stopNotificationBuilder.setSmallIcon(R.drawable.ic_service_notification_24dp);
        stopNotificationBuilder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        stopNotificationBuilder.setContentTitle(getString(R.string.service_stream));
        stopNotificationBuilder.setContentText(getString(R.string.service_go_to) + getAppData().getServerAddress());
        stopNotificationBuilder.setContentIntent(pendingMainActivityIntent);
        stopNotificationBuilder.addAction(R.drawable.ic_service_stop_24dp,
                getString(R.string.service_stop).toUpperCase(),
                PendingIntent.getBroadcast(this, 0, new Intent(ACTION_NOTIFY_STOP_STREAM), 0));
        stopNotificationBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
        return stopNotificationBuilder.build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}