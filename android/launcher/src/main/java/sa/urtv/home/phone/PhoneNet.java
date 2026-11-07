package sa.urtv.home.phone;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** The TV's Wi-Fi address, from the network interfaces (Guard.wifiAddress picks: wlan*, up, a private IPv4 address). */
final class PhoneNet {
    private PhoneNet() { }

    static byte[] wifi() {
        List<Guard.Candidate> all = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                boolean up = ni.isUp();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) all.add(new Guard.Candidate(ni.getName(), a.getAddress(), up));
                }
            }
        } catch (java.net.SocketException e) {
            return null;
        }
        return Guard.wifiAddress(all);
    }
}
