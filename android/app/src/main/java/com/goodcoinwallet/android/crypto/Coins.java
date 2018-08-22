package com.goodcoinwallet.android.crypto;

import com.raugfer.crypto.coins;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Coins {

    private static final Coin ethereum = new Ethereum();
    private static final Coin goodcoin = new GoodCoin();

    private static final Map<String, Coin> registry = new HashMap<>();

    static {
        registry.put(ethereum.getCode(), ethereum);
        registry.put(goodcoin.getCode(), goodcoin);
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

    private static class Ethereum extends AbstractCoin {
        @Override
        public String getName() {
            return "Ethereum";
        }

        @Override
        public String getLabel() {
            return "ethereum";
        }

        @Override
        public String getCode() {
            return "ETH";
        }

        @Override
        public String getSymbol() {
            return "Ξ";
        }

        @Override
        public Service getService(boolean testnet) {
            if (testnet) {
                return new EtherscanAPI("https://api-ropsten.etherscan.io/api");
            } else {
                return new Service.Multi(new Service[]{
                        new EtherscanAPI("https://api.etherscan.io/api"),
                        new BlockcypherAPI("https://api.blockcypher.com/v1/eth/main"),
                });
            }
        }

        @Override
        public String getTransactionUrl(String hash, boolean testnet) {
            if (testnet) {
                return "https://ropsten.etherscan.io/tx/" + hash;
            } else {
                return "https://etherscan.io/tx/" + hash;
            }
        }
    }

    public static abstract class ERC20Token extends Ethereum {
        @Override
        public Coin getFeeCoin() {
            return findCoin("ETH");
        }

        @Override
        public Service getService(boolean testnet) {
            String contractAddress = coins.attr("contract.address", getLabel(), testnet);
            if (testnet) {
                return new EtherscanAPI("https://api-ropsten.etherscan.io/api", contractAddress);
            } else {
                return new EtherscanAPI("https://api.etherscan.io/api", contractAddress);
            }
        }
    }

    private static class GoodCoin extends ERC20Token {
        @Override
        public String getName() {
            return "Good Coin";
        }

        @Override
        public String getLabel() {
            return "goodcoin";
        }

        @Override
        public String getCode() {
            return "GDC";
        }

        @Override
        public String getSymbol() {
            return null;
        }
    }

}
