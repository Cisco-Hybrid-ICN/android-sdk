/*
 * Copyright (c) 2019-2020 Cisco and/or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cisco.hicn.hproxylibrary.service;

import android.app.Service;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;

import com.samsung.android.semtun.SemTunException;
import com.samsung.android.semtun.SemTunManagerInterface;

import java.util.HashSet;
import java.util.Properties;

import com.cisco.hicn.hproxylibrary.R;
import com.cisco.hicn.hproxylibrary.supportlibrary.HProxyLibrary;
import com.cisco.hicn.hproxylibrary.supportlibrary.PuntingSpec;

//import com.samsung.android.hicn.HicnException; // A11v1
//import com.samsung.android.hicn.HicnManager; // A11v1


public class ProxyBackendNative extends ProxyBackend {
    private static final int DEFAULT_MTU = 1500;

    /**
     * Name of the hicn tun interface
     */
    private static final String TUN_INTERFACE = "TestTun2";

    //private HicnManager mHicnMgr = null; // A11v1
    private final SemTunManagerInterface mSemTun; // A11v3
    private ParcelFileDescriptor mTunPFd = null; // A11v3

    public ProxyBackendNative(Service parentService) {
        super(parentService);

        //mHicnMgr = HicnManager.getInstance(parentService); // A11v1

        /*
         * public static SemTunManagerInterface getInstance(Context ctx)
         *
         * Context – Application context
         *
         * This API fetches the SemTunManagerInterface instance
         */
        mSemTun = SemTunManagerInterface.getInstance(parentService); // A11v3
    }

/*
    // A10
    @Override
    public int configureTun(Properties parameters) {
        if (mTunFd != -1) {
            Log.w(getTag(), "File descriptor already assigned.");
            return mTunFd;
        }

        String address = null;
        try {
            address = parameters.getProperty("ADDRESS") + "/" + parameters.getProperty("PREFIX_LENGTH");
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad parameter");
        }

        mParameters = parameters;

        synchronized (mParentService) {
            mTunFd = startHicnTun(TUN_INTERFACE, mProxiedPackages, address);
            mHandler.sendEmptyMessage(R.string.hproxy_connected);
        }

        Log.i(getTag(), "New interface: " + TUN_INTERFACE + " (" + address + ")");

        return mTunFd;
    }
*/

    private String protocolIntToStr(int protocol) {
        switch(protocol) {
            case PuntingSpec.IPPROTO_TCP:
                return "tcp";
            case PuntingSpec.IPPROTO_UDP:
                return "udp";
            case PuntingSpec.IPPROTO_ICMP:
                return "icmp";
            default:
                throw new IllegalArgumentException("Bad protocol in punting rule");
        }
    }

    // A11v1
    @Override
    public int configureTun(Properties parameters) {
        if (mTunPFd != null) {
            Log.w(getTag(), "File descriptor already assigned.");
            return -1;
        }

        String address;
        try {
            address = parameters.getProperty("ADDRESS") + "/" + parameters.getProperty("PREFIX_LENGTH");
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad parameter");
        }

        mParameters = parameters;
        //int ret = -1;
        synchronized (mParentService) {
            try {
                //mTunPFd = mHicnMgr.startHicn(TUN_INTERFACE, mProxiedPackages, address); // A11v1

                /*
                 * public ParcelFileDescriptor createTun(String interfaceName,
                 *      String ipAddress, int mtu) throws SemTunException
                 * String interfaceName (Tun Interface name, if passed null then
                 *      default value is: HicnTun )
                 * String ipAddress  (package names of arget application)
                 * String ipAddress  (The Ip address of the TUN interface)
                 *
                 * This API will be responsible for the TUN interface creation,
                 * assigning IP address to TUN interface. This API will return
                 * the ParcelFileDescriptor, this ParcelFileDescriptor can be
                 * used to obtain the native FD.
                 */
                mTunPFd = mSemTun.createTun(TUN_INTERFACE, address, DEFAULT_MTU); // A11v3
                //} catch (HicnException e) { // A11v1
            } catch (SemTunException e) { // A11v3
                Log.i(getTag(), "Failed to create TUN interface : " + TUN_INTERFACE + " (" + address + ")");
            }

//            // setup punting XXX require additional API
//            String [] protocols = { "icmp", "udp", "udp" };
//            //int [] protocols = { IPPROTO_ICMP, IPPROTO_UDP, IPPROTO_UDP };
//            int [] ports = { 0, WEBEX_PORT1, WEBEX_PORT2 };

            //for (String pkg : mProxiedPackages) {
            SharedPreferences settings = mParentService.getSharedPreferences(PuntingSpec.PREFS_NAME, 0);

            /* Due to a limitation in the implementation of the SemTunManager
             * API, we can only punt the same set of ports for all applications.
             * We thus perform a first preprocessing of the punting
             * specifications to build a super-set of all protocols.
             */

            final HashSet<Pair<Integer, String>> puntSet = new HashSet<>();
            for (PuntingSpec p : HProxyLibrary.getPuntingSpecs()) {
                boolean punt = settings.getBoolean(p.androidPackage, p.puntByDefault);
                if (!punt)
                    continue;
                for (int i = 0; i < p.protocols.length; i++) {
                    String protocol = protocolIntToStr(p.protocols[i]);
                    final Pair<Integer, String> pair = new Pair<>(p.ports[i], protocol);
                    puntSet.add(pair);
                }
            }

            int [] puntPorts = new int[puntSet.size()];
            String [] puntProtocols = new String[puntSet.size()];
            int pos = 0;
            for (Pair<Integer,String> pair : puntSet) {
                puntPorts[pos] = pair.first;
                puntProtocols[pos] = pair.second;
                pos += 1;
            }

            for (PuntingSpec p : HProxyLibrary.getPuntingSpecs()) {
                boolean punt = settings.getBoolean(p.androidPackage, p.puntByDefault);
                if (!punt)
                    continue;
                try {
                    /*
                     * public int[] enableTunForUID(boolean enable, String
                     *      interfaceName, String packageName, int[] port, String[]
                     *      protocol) throws SemTunException
                     * boolean enable (true – add, false – delete )
                     * String interfaceName  : interface name on which rules should
                     *      be created
                     * String packageName: The Whilelist application
                     * Int[] port and String[] protocol : The count of both the
                     *      arrays(port[] and protocol[]) should be same. The
                     *      port[i] value can be 0 in array but protocol[i] value
                     *      should not be null otherwise rule will not be applied
                     *
                     * This API either add/delete the rule, it will add/delete the
                     * application to whitelist, and port – protocol combination for
                     * redirection of traffic to TUN interface. The return value of
                     * integer array contains the success value of each
                     * port-protocol combination. 0 is success.
                     */

                    //int [] rc =
                    mSemTun.enableTunForUID(true, TUN_INTERFACE, p.androidPackage, puntPorts, puntProtocols);

                } catch (SemTunException e) { // A11v3
                    Log.i(getTag(), "Failed to setup punting");
                }
            }

            mHandler.sendEmptyMessage(R.string.hproxy_connected);
        }

        Log.i(getTag(), "New interface: " + TUN_INTERFACE + " (" + address + ")");
        if (mTunPFd != null) {
            mTunFd = mTunPFd.getFd();
        } else {
            mTunFd = -1;
        }
        Log.i(getTag(), "New interface: from ConfigureTun ProxyBackendNative " + mTunFd);
        return mTunFd;
    }

    @Override
    public int closeTun() {
        if (mTunFd != -1) {
            stopHicn();
        }

        return 0;
    }

/*
    // A10
    private int startHicnTun(String interfaceName, String[] packageName, String ipAddress) {
        int fd = -1;
        if (getHicnService()) {
            try {
                Log.d(getTag(), "Opening tun device  " + interfaceName + " with IP address " + ipAddress);
                fd = HProxy.getInstance().getTunFd(interfaceName);
                Method startHicn = sIHicnManagerClass.getMethod("startHicn", String.class, String[].class, String.class);
                startHicn.invoke(sHicnManager, interfaceName, packageName, ipAddress);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        Log.d(getTag(), "The PID of the app is " + android.os.Process.myPid());
        return fd;
    }
*/

/*
    // A10
    private void stopHicn() {
        if (getHicnService()) {
            try {
                Method stop = sIHicnManagerClass.getMethod("stopHicn");
                stop.invoke(sHicnManager);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }
*/

    // A11v1
    private void stopHicn() {
        //if(mHicnMgr == null ) // A11v1
        if (mSemTun == null ) // A11v3
            return;

        try {
            // mTunFd = mHicnMgr.stopHicn(); // A11v1

            /*
             * int stopTun(String interfaceName) throws SemTunException
             *
             * This API flushes all the rules applied till now, returns success
             *  when all rules are deleted for the interface name.
             */
            mSemTun.stopTun(TUN_INTERFACE); // A11v3
            //} catch (HicnException e) { // A11v1
        } catch (SemTunException e) { //A11v3
            Log.i(getTag(), "caught hICN related Exception : " + e.getMessage());
            //e.printStackTrace();
        } catch (Exception e) {
            Log.i(getTag(), "caught Exception : " + e.getMessage());
            //e.printStackTrace();
        }
    }


    private String getTag() {
        return ProxyBackendNative.class.getSimpleName();
    }
}
