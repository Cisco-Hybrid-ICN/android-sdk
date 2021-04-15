package com.cisco.hicn.hproxylibrary.supportlibrary;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;


import com.samsung.android.semtun.SemTunManagerInterface;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.cisco.hicn.hproxylibrary.service.ForwarderWrapper;
import com.cisco.hicn.hproxylibrary.service.ProxyBackend;

public class HProxyLibrary {
    private static ForwarderWrapper mForwarderWrapper = null;
    private static String mBackendAndroidServiceName = null;
    private static String backendAndroidServiceName = null;
    private static HProxyLibrary sInstance = null;
    private static Activity mActivity = null;
    private static Class<?> mBackendAndroidServiceClass;
    private ProxyBackend mProxyBackend;

    // This will sotre the actual pointer to the proxy (in JNI code)
    private long mProxyPtr = 0;

    /** Messenger for communicating with the service. */
    Messenger mMessenger = null;

    /** We need a context to bind to a Service */
    Service mService;

    /** Flag indicating whether we have called bind on the service. */
    boolean bound;

    static {
        System.loadLibrary("hproxy-wrapper");
    }

    public static HProxyLibrary getInstance() {
        if (sInstance == null) {
            sInstance = new HProxyLibrary();
        }
        return sInstance;
    }

    public static void stopInstance() {
        if (sInstance == null)
            return;
        sInstance.stop();
        sInstance = null;
    }

    public HProxyLibrary() {
        initConfig();
    }

    public static void setBackendAndroidServiceName(String backendAndroidServiceName) {
        mBackendAndroidServiceName = backendAndroidServiceName;
    }

    public static void setForwarderWrapper(ForwarderWrapper forwarderWrapper) {
        mForwarderWrapper = forwarderWrapper;
    }

    public static void setForwarderService(Class<?> backendAndroidServiceClass) {
        mBackendAndroidServiceClass = backendAndroidServiceClass;
    }

    public void setService(Service service) {
        mService = service;
    }

    public static void setActivity(Activity activity) {
        mActivity = activity;
    }

    public static Activity getActivity() {
        return mActivity;
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            mMessenger = new Messenger(service);
            bound = true;

            //send a message to start check if the fwd is alive

            while (!mForwarderWrapper.isRunningForwarder()) {
                // wait for the forwarder ro run.
                Log.d(getTag(), "Hicn forwarder is not started yet. Waiting before activating the proxy.");
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) { }
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) { }

            Log.i(getTag(), "hICN service is now available");
            onHicnServiceAvailable(true);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // NOTE: You will never receive a call to onServiceDisconnected()
            // for a Service running in the same process that you’re binding
            // from.

            mMessenger = null;
            bound = false;

            Log.i(getTag(), "hICN service is no more available");
            onHicnServiceAvailable(false);
        }
    };

    public void setProxyInstance(ProxyBackend proxyThread) {
        mProxyBackend = proxyThread;
    }

    public native void initConfig();

    public int createTunDevice(String vpn_address, int prefix_length,
                               String route_address,
                               int route_prefix_length, String dns) {
        Properties params = new Properties();
        params.put("ADDRESS", vpn_address);
        params.put("PREFIX_LENGTH", Integer.toString(prefix_length));
        params.put("ROUTE_ADDRESS",route_address);
        params.put("ROUTE_PREFIX_LENGTH", Integer.toString(route_prefix_length));
        params.put("DNS", dns);
        int ret = mProxyBackend.configureTun(params);
        return ret;
    }

    public int closeTunDevice() {
        return mProxyBackend.closeTun();
    }

    public native boolean isRunning();

    public native int getTunFd(String device_name);

    public native void start(boolean disableSD, String server, int port); //String remote_address, int remote_port);

    public native void stop();

    public native void destroy();

    public static native boolean isHProxyEnabled();

    //public static native String getProxifiedAppName();
    //public static native String getProxifiedPackageName();
    public static native String getHicnServiceName();

    public static native PuntingSpec[] getPuntingSpecs();

    // BEGIN WITH_START_STOP

    public int attachHicnService() {

        Log.i(getTag(), "Attaching hICN service");
        mService.bindService(new Intent(mService, mBackendAndroidServiceClass), mConnection,
                Context.BIND_AUTO_CREATE);
        return 0;
    }

    public int detachHicnService() {
        Log.i(getTag(), "Detaching hICN service");

        if (bound) {
            mService.unbindService(mConnection);

            /*
             * We need to process event here as we are in the same process and
             * onServiceDisconnected will not be called
             */
            mMessenger = null;
            bound = false;

            Log.i(getTag(), "hICN service is no more available");
            onHicnServiceAvailable(false);
        }
        return 0;
    }

    public native void onHicnServiceAvailable(boolean flag);

    // END WITH_START_STOP

    private final String getTag() {
        return HProxyLibrary.class.getSimpleName();
    }

    public static boolean isStunServiceAvailable() {
        return SemTunManagerInterface.isServiceAvailable();
    }


}







