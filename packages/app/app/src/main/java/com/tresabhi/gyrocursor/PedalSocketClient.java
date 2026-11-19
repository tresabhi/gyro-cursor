package com.tresabhi.gyrocursor;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Connects to the server's access point over TCP and reads newline-delimited
// messages from it, reconnecting whenever the link drops.
public class PedalSocketClient {

    private static final String TAG = "PedalSocketClient";

    // ESP32 soft-AP default gateway address
    private static final String SERVER_HOST = "192.168.4.1";
    // must match SERVER_PORT in packages/server/config.py
    private static final int SERVER_PORT = 5000;

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final long RETRY_DELAY_MS = 1000;

    public interface Listener {
        void onPedalChanged(int index, boolean pressed);

        void onConnectionChanged(boolean connected);
    }

    private final ConnectivityManager connectivityManager;
    private final Listener listener;
    // keeps the Wi-Fi radio out of power save, which otherwise batches
    // incoming packets and adds 100ms+ of jittery latency
    private final WifiManager.WifiLock wifiLock;

    private final Object lock = new Object();
    private volatile boolean running = false;
    private Network wifiNetwork;
    private Socket socket;
    private Thread thread;

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            Log.d(TAG, "Wi-Fi network available: " + network);

            synchronized (lock) {
                wifiNetwork = network;
                lock.notifyAll();
            }
        }

        @Override
        public void onLost(Network network) {
            Log.d(TAG, "Wi-Fi network lost: " + network);

            synchronized (lock) {
                if (network.equals(wifiNetwork)) {
                    wifiNetwork = null;
                }
            }

            closeSocket();
        }
    };

    public PedalSocketClient(Context context, Listener listener) {
        this.connectivityManager = context.getSystemService(ConnectivityManager.class);
        this.listener = listener;

        WifiManager wifiManager = context.getApplicationContext().getSystemService(WifiManager.class);
        this.wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, TAG);
        this.wifiLock.setReferenceCounted(false);
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        wifiLock.acquire();

        // The access point has no internet, so without an explicit request
        // Android (and Wear OS's Bluetooth proxy) would route traffic
        // elsewhere. Requesting it also keeps Wi-Fi up on watches.
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.requestNetwork(request, networkCallback);

        thread = new Thread(this::run, "pedal-socket-thread");
        thread.start();
    }

    public void stop() {
        running = false;

        if (wifiLock.isHeld()) {
            wifiLock.release();
        }

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (IllegalArgumentException ignored) {
        }

        synchronized (lock) {
            lock.notifyAll();
        }

        closeSocket();

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void closeSocket() {
        Socket current;

        synchronized (lock) {
            current = socket;
            socket = null;
        }

        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void run() {
        while (running) {
            Network network;

            synchronized (lock) {
                while (running && wifiNetwork == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }

                network = wifiNetwork;
            }

            if (!running) {
                return;
            }

            try {
                connectAndRead(network);
            } catch (IOException e) {
                Log.d(TAG, "Socket error: " + e);
            }

            closeSocket();
            listener.onConnectionChanged(false);

            if (!running) {
                return;
            }

            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void connectAndRead(Network network) throws IOException {
        Socket newSocket = network.getSocketFactory().createSocket();

        synchronized (lock) {
            socket = newSocket;
        }

        newSocket.setTcpNoDelay(true);
        newSocket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), CONNECT_TIMEOUT_MS);

        Log.d(TAG, "Connected to " + SERVER_HOST + ":" + SERVER_PORT);
        listener.onConnectionChanged(true);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(newSocket.getInputStream(), StandardCharsets.US_ASCII));
        String line;

        while (running && (line = reader.readLine()) != null) {
            handleLine(line.trim());
        }
    }

    private void handleLine(String line) {
        String[] parts = line.split(" ");

        if (parts.length == 3 && parts[0].equals("pedal")) {
            try {
                int index = Integer.parseInt(parts[1]);
                boolean pressed = parts[2].equals("1");

                listener.onPedalChanged(index, pressed);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Bad pedal message: " + line);
            }

            return;
        }

        Log.w(TAG, "Unknown message: " + line);
    }
}
