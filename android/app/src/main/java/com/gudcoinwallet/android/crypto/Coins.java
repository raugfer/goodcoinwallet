package com.gudcoinwallet.android.crypto;

import com.raugfer.crypto.coins;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Coins {

    private static final Coin waves = new Waves();
    private static final Coin gudcoin = new GudCoin();

    private static final Map<String, Coin> registry = new HashMap<>();

    static {
        registry.put(waves.getCode(), waves);
        registry.put(gudcoin.getCode(), gudcoin);
    }

    public static Coin findCoin(String code) {
        return registry.get(code);
    }

    public static Iterator<Coin> list() {
        return registry.values().iterator();
    }

    private static abstract class AbstractCoin implements Coin {

        @Override
        public final AddressMode getMode() {
            String mode = coins.attr("address.mode", getLabel());
            switch (mode) {
                case "utxo": return AddressMode.UTXO;
                case "account": return AddressMode.ACCOUNT;
                default: throw new IllegalStateException("Unknown mode");
            }
        }

        @Override
        public final int getDecimals() {
            return coins.attr("decimals", getLabel());
        }

        @Override
        public final int getBlockTime() {
            return coins.attr("block.time", getLabel());
        }

        @Override
        public final int getMinConf() {
            return coins.attr("confirmations", getLabel());
        }

        @Override
        public Coin getFeeCoin() {
            return this;
        }
    }

    private static class Waves extends AbstractCoin {
        @Override
        public String getName() {
            return "Waves";
        }

        @Override
        public String getLabel() {
            return "waves";
        }

        @Override
        public String getCode() {
            return "WAVES";
        }

        @Override
        public String getSymbol() {
            return null;
        }

        @Override
        public Service getService(boolean testnet) {
            if (testnet) {
                return new WavesnodesAPI("https://pool.testnet.wavesnodes.com/", true, 100000);
            } else {
                return new WavesnodesAPI("https://nodes.wavesnodes.com/", false, 100000);
            }
        }

        @Override
        public String getTransactionUrl(String hash, boolean testnet) {
            if (testnet) {
                return "https://testnet.wavesexplorer.com/tx/" + hash;
            } else {
                return "https://wavesexplorer.com/tx/" + hash;
            }
        }
    }

    public static abstract class WavesToken extends Waves {
        @Override
        public Coin getFeeCoin() {
            return findCoin("WAVES");
        }

        public long getFee() { return 100000; }

        @Override
        public Service getService(boolean testnet) {
            String assetId = coins.attr("asset.id", getLabel(), testnet);
            long fee = getFee();
            if (testnet) {
                return new WavesnodesAPI("https://pool.testnet.wavesnodes.com/", assetId, true, fee);
            } else {
                return new WavesnodesAPI("https://nodes.wavesnodes.com/", assetId, false, fee);
            }
        }
    }

    private static class GudCoin extends WavesToken {
        @Override
        public String getName() {
            return "Gud Coin";
        }

        @Override
        public String getLabel() {
            return "gudcoin";
        }

        @Override
        public String getCode() {
            return "UMTC";
        }

        @Override
        public String getSymbol() {
            return null;
        }

        @Override
        public Coin getFeeCoin() {
            return findCoin("UMTC");
        }

        @Override
        public long getFee() {
            return 1;
        }
    }

}