package io.contentos.android.sdk;

import java.io.File;
import java.util.List;

import io.contentos.android.sdk.keystore.KeyStore;
import io.contentos.android.sdk.keystore.KeystoreAPI;
import io.contentos.android.sdk.rpc.ApiServiceGrpc;
import io.contentos.android.sdk.rpc.RpcClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public final class Wallet extends RpcClient implements KeystoreAPI {

    private ManagedChannel channel;
    private KeyStore keyStore;

    /**
     * Wallet constructor.
     * @param serverHost    server host
     * @param serverPort    server port
     */
    public Wallet(String serverHost, int serverPort) {
        super(ApiServiceGrpc.newBlockingStub(
                ManagedChannelBuilder.forAddress(serverHost, serverPort)
                .usePlaintext()
                .userAgent("")
                .build()));
        channel = (ManagedChannel) service.getChannel();
    }

    /**
     * Close the wallet.
     */
    public void close() {
        try {
            channel.shutdownNow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Load accounts and their keys from specific keystore file.
     * @param file      keystore file
     * @param password  password for keystore encryption/decryption
     */
    public synchronized void openKeyStore(File file, String password) {
        keyStore = KeyStore.openOrCreate(file, password);
    }

    /**
     * Create an RPC client in behalf of specific account, i.e. using account's private key for transaction signatures.
     * @param name name of account
     * @return RPC client.
     */
    public RpcClient account(String name) {
        return new RpcClient(service, getKey(name));
    }

    //
    // KeyStoreAPI implementation
    //

    public synchronized String getKey(String account) {
        if (keyStore != null) {
            return keyStore.getKey(account);
        }
        return null;
    }

    public synchronized void addKey(String account, String wifPrivateKey) {
        if (keyStore == null) {
            throw new RuntimeException("no open keystore");
        }
        keyStore.addKey(account, wifPrivateKey);
    }

    public synchronized void removeKey(String account) {
        if (keyStore == null) {
            throw new RuntimeException("no open keystore");
        }
        keyStore.removeKey(account);
    }

    public synchronized List<String> getAccounts() {
        if (keyStore == null) {
            throw new RuntimeException("no open keystore");
        }
        return keyStore.getAccounts();
    }
}