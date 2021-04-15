package com.cisco.hicn.hproxylibrary.supportlibrary;

public class PuntingSpec
{

    /* The name of SharedPreferences storing this information */
    public static final String PREFS_NAME = "app_pref";

    public static final int IPPROTO_ICMP = 1;
    public static final int IPPROTO_TCP = 6;
    public static final int IPPROTO_UDP = 17;

    public String appName;
    public String androidPackage;
    public int[] ports;
    public int[] protocols;
    public boolean puntByDefault;
}