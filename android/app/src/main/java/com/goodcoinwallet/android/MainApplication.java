package com.goodcoinwallet.android;

import android.app.Application;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;

import com.goodcoinwallet.android.crypto.Coin;
import com.raugfer.crypto.mnemonic;
import com.raugfer.crypto.pair;
import com.goodcoinwallet.android.crypto.Sync;
import com.goodcoinwallet.android.db.AppDatabase;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MainApplication extends Application {

    private static MainApplication app; { app = this; }
    public static MainApplication app() {
        return app;
    }

    private Locker locker;
    private ExecutorService exec;
    private AppDatabase mainnetdb;
    private AppDatabase testnetdb;
    private Sync mainnetSync;
    private Sync testnetSync;
    private Map<String, Integer> themes = new HashMap<>();
    private Map<String, Integer> drawable = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();

        locker = Locker.create("default", getBaseContext());

        mainnetdb = createDatabase("mainnetdb");
        testnetdb = createDatabase("testnetdb");

        themes.put("ETH", R.style.Ethereum);
        themes.put("GDC", R.style.Good_Coin);

        drawable.put("ETH", R.drawable.ethereum);
        drawable.put("GDC", R.drawable.goodcoin);

        exec = createExec();
        mainnetSync = new Sync(exec, mainnetdb.appDao(), false);
        testnetSync = new Sync(exec, testnetdb.appDao(), true);
    }

    private AppDatabase createDatabase(String name) {
        return Room.databaseBuilder(getApplicationContext(), AppDatabase.class, name).allowMainThreadQueries().build();
    }

    private ExecutorService createExec() {
        return new ThreadPoolExecutor(50, 100, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    public ExecutorService getExec() {
        return exec;
    }

    public Sync getMainnetSync() {
        return mainnetSync;
    }

    public Sync getTestnetSync() {
        return testnetSync;
    }

    public Sync getSync() {
        boolean testnet = getPreferences().getBoolean("testnet_mode", false);
        return testnet ? testnetSync : mainnetSync;
    }

    public int findTheme(String code) {
        return themes.get(code);
    }

    public int findDrawable(String code) {
        return drawable.get(code);
    }

    public boolean shuttingDown() {
        return exec.isShutdown();
    }

    public boolean networkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo == null) return false;
        return netInfo.isConnected();
    }

    public void signup(String[] wordlist, String words, String password, Continuation<Boolean> cont, Locker.UserAuthenticationHandler handler) {
        pair<BigInteger, Integer> t;
        try {
            t = mnemonic.unmnemonic(words, wordlist);
        } catch (IllegalArgumentException e) {
            cont.cont(false);
            return;
        }
        BigInteger entropy = t.l;
        int entropySize = t.r;
        Object[] result = new Object[2];
        mainnetSync.derive(words, password, null, result, () -> {
            Object secrets = result[0];
            BigInteger identity = (BigInteger) result[1];
            Session session = new Session(entropy, entropySize, identity);
            String plain = session.toString();
            locker.encrypt(plain, handler, (String encrypted) -> {
                if (encrypted == null) {
                    cont.cont(false);
                    return;
                }
                mainnetSync.bootstrap(secrets, () -> testnetSync.bootstrap(secrets, () -> {
                    SharedPreferences preferences = getPreferences();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("encrypted_session", encrypted);
                    editor.apply();
                    cont.cont(true);
                }));
            });
        });
    }

    public void login(Continuation<Boolean> cont, Locker.UserAuthenticationHandler handler) {
        SharedPreferences preferences = getPreferences();
        String encrypted = preferences.getString("encrypted_session", "");
        locker.decrypt(encrypted, handler, (String plain) -> {
            if (plain == null) {
                logout();
                cont.cont(false);
                return;
            }
            Session session = Session.parse(plain);
            if (session == null) {
                logout();
                cont.cont(false);
                return;
            }
            cont.cont(true);
        });
    }

    public void authenticate(String[] wordlist, String password, Coin coin, Continuation<Object> cont, Locker.UserAuthenticationHandler handler) {
        SharedPreferences preferences = getPreferences();
        String encrypted = preferences.getString("encrypted_session", "");
        locker.decrypt(encrypted, handler, (String plain) -> {
            if (plain == null) {
                cont.cont(null);
                return;
            }
            Session session = Session.parse(plain);
            if (session == null) {
                cont.cont(null);
                return;
            }
            String words = mnemonic.mnemonic(session.getEntropy(), session.getEntropySize(), wordlist);
            List<Coin> coins = new ArrayList<>();
            coins.add(coin);
            Object[] result = new Object[2];
            mainnetSync.derive(words, password, coins, result, () -> {
                Object secrets = result[0];
                BigInteger identity = (BigInteger) result[1];
                if (!identity.equals(session.getIdentity())) {
                    cont.cont(null);
                    return;
                }
                cont.cont(secrets);
            });
        });
    }

    public void logout() {
        boolean done = false;
        do {
            exec.shutdownNow();
            try {
                done = exec.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                continue;
            }
        } while (!done);
        mainnetdb.clearAllTables();
        testnetdb.clearAllTables();
        SharedPreferences preferences = getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("encrypted_session");
        editor.apply();
        exec = createExec();
        mainnetSync = new Sync(exec, mainnetdb.appDao(), false);
        testnetSync = new Sync(exec, testnetdb.appDao(), true);
    }

    private static class Session {
        static Session parse(String string) {
            String[] parts = string.split(":");
            if (parts.length != 3) return null;
            BigInteger entropy;
            try {
                entropy = new BigInteger(parts[0], Character.MAX_RADIX);
            } catch (NumberFormatException e) {
                return null;
            }
            int entropySize;
            try {
                entropySize = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            BigInteger identity;
            try {
                identity = new BigInteger(parts[2], Character.MAX_RADIX);
            } catch (NumberFormatException e) {
                return null;
            }
            return new Session(entropy, entropySize, identity);
        }
        private BigInteger entropy;
        private int entropySize;
        private BigInteger identity;
        Session(BigInteger entropy, int entropySize, BigInteger identity) {
            this.entropy = entropy;
            this.entropySize = entropySize;
            this.identity = identity;
        }
        BigInteger getEntropy() {
            return entropy;
        }
        int getEntropySize() {
            return entropySize;
        }
        BigInteger getIdentity() {
            return identity;
        }
        public String toString() {
            return entropy.toString(Character.MAX_RADIX) + ":" + entropySize + ":" + identity.toString(Character.MAX_RADIX);
        }
    }

}
