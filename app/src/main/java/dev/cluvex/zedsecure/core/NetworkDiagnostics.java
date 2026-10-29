package dev.cluvex.zedsecure.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.Build;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only, privacy-conscious network diagnostics for ZX VPN.
 *
 * Does not probe remote hosts, change network settings, or include config links,
 * credentials, SSIDs, or public IP addresses in the report.
 */
public final class NetworkDiagnostics {
    private NetworkDiagnostics() {}

    public static String createReport(Context context) {
        StringBuilder out = new StringBuilder(512);
        out.append("ZX VPN network diagnostics\n");
        out.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n');

        ConnectivityManager manager =
                (ConnectivityManager) context.getApplicationContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return out.append("Connectivity service: unavailable\n").toString();
        }

        Network active = manager.getActiveNetwork();
        if (active == null) {
            return out.append("Network: disconnected\n").toString();
        }

        NetworkCapabilities caps = manager.getNetworkCapabilities(active);
        out.append("Network: connected\n");
        if (caps != null) {
            List<String> transports = new ArrayList<>();
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("Wi-Fi");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("cellular");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("ethernet");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transports.add("bluetooth");
            if (transports.isEmpty()) transports.add("other");
            out.append("Transport: ").append(join(transports)).append('\n');
            out.append("Validated internet: ")
                    .append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                    .append('\n');
            out.append("Metered network: ")
                    .append(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                    .append('\n');
        }

        LinkProperties link = manager.getLinkProperties(active);
        if (link != null) {
            List<String> dns = new ArrayList<>();
            for (InetAddress address : link.getDnsServers()) {
                // Report only the count; do not expose local DNS addresses in shared logs.
                dns.add(address.getHostAddress());
            }
            out.append("DNS servers detected: ").append(dns.size()).append('\n');
            int ipv4 = 0, ipv6 = 0;
            for (RouteInfo route : link.getRoutes()) {
                InetAddress destination = route.getDestination().getAddress();
                if (destination == null) continue;
                if (destination.getAddress().length == 4) ipv4++;
                else if (destination.getAddress().length == 16) ipv6++;
            }
            out.append("IPv4 routes: ").append(ipv4).append('\n');
            out.append("IPv6 routes: ").append(ipv6).append('\n');
            out.append("Proxy configured: ").append(link.getHttpProxy() != null).append('\n');
        }
        out.append("Scope: local metadata only; no external reachability or leak test performed.\n");
        return out.toString();
    }

    private static String join(List<String> values) {
        if (values.isEmpty()) return "unknown";
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }
}
