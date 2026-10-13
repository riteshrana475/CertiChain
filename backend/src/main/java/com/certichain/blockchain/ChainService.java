package com.certichain.blockchain;

import com.certichain.certificate.CertificateEntity;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Numeric;

@Service
public class ChainService {

    public static final String ANCHORED = "ANCHORED";
    public static final String NOT_ANCHORED = "NOT_ANCHORED";

    private static final Logger log = LoggerFactory.getLogger(ChainService.class);
    private static final long SEPOLIA_CHAIN_ID = 11155111L;
    private static final long LOCAL_CHAIN_ID = 31337L;
    private static final String ANVIL_DEVELOPMENT_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private final ChainDeploymentRepository deployments;
    private final ChainSettingsRepository settings;
    private final String defaultPrivateKey;
    private String rpcUrl;
    private long chainId;
    private String privateKey;

    private Web3j web3;
    private RawTransactionManager transactions;
    private Credentials credentials;
    private String contractAddress;

    public ChainService(
            ChainDeploymentRepository deployments,
            ChainSettingsRepository settings,
            @Value("${certichain.chain.rpc-url}") String rpcUrl,
            @Value("${certichain.chain.chain-id}") long chainId,
            @Value("${certichain.chain.private-key:}") String privateKey) {
        this.deployments = deployments;
        this.settings = settings;
        this.defaultPrivateKey = privateKey;
        this.rpcUrl = rpcUrl;
        this.chainId = chainId;
        this.privateKey = privateKey;
    }

    @PostConstruct
    void loadSavedSettings() {
        settings.findById(1).ifPresent(saved -> applySaved(saved.getRpcUrl(), saved.getChainId(), saved.getPrivateKey()));
    }

    public ChainView view() {
        boolean keyConfigured = chainId != SEPOLIA_CHAIN_ID || storedSepoliaKey();
        return new ChainView(rpcUrl, chainId, networkName(), keyConfigured);
    }

    public ChainView configure(String rpcUrl, long chainId, String submittedKey) {
        if (chainId != LOCAL_CHAIN_ID && chainId != SEPOLIA_CHAIN_ID) {
            throw new IllegalArgumentException("Choose the local chain or Sepolia.");
        }
        String endpoint = rpcUrl == null ? "" : rpcUrl.trim();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("The RPC URL must start with http:// or https://.");
        }
        String nextKey = resolveKey(chainId, submittedKey);
        String storedKey = chainId == SEPOLIA_CHAIN_ID ? nextKey : "";
        ChainSettingsEntity saved = settings.findById(1).orElseGet(ChainSettingsEntity::new);
        saved.setId(1);
        saved.setRpcUrl(endpoint);
        saved.setChainId(chainId);
        saved.setPrivateKey(storedKey);
        settings.save(saved);
        synchronized (this) {
            applySaved(endpoint, chainId, storedKey);
            web3 = null;
            transactions = null;
            credentials = null;
            contractAddress = null;
        }
        return view();
    }

    private void applySaved(String rpcUrl, long chainId, String storedKey) {
        this.rpcUrl = rpcUrl;
        this.chainId = chainId;
        if (storedKey != null && !storedKey.isBlank()) {
            this.privateKey = storedKey;
        } else if (chainId != SEPOLIA_CHAIN_ID) {
            this.privateKey = defaultPrivateKey;
        }
    }

    private boolean storedSepoliaKey() {
        return settings.findById(1)
                .map(saved -> saved.getChainId() == SEPOLIA_CHAIN_ID && saved.getPrivateKey() != null && !saved.getPrivateKey().isBlank())
                .orElse(false);
    }

    private String resolveKey(long chainId, String submittedKey) {
        String submitted = submittedKey == null ? "" : submittedKey.trim();
        if (chainId == SEPOLIA_CHAIN_ID) {
            String key = submitted.isBlank() ? storedKey() : submitted;
            if (key.isBlank()) {
                throw new IllegalArgumentException("Sepolia needs your own wallet key. The local development key cannot be used.");
            }
            if (isAnvilKey(key)) {
                throw new IllegalArgumentException("The local development key cannot be used on Sepolia.");
            }
            return key.startsWith("0x") || key.startsWith("0X") ? key : "0x" + key;
        }
        return defaultPrivateKey;
    }

    private String storedKey() {
        return settings.findById(1).map(ChainSettingsEntity::getPrivateKey).orElse("");
    }

    private static boolean isAnvilKey(String key) {
        String normalized = key.startsWith("0x") || key.startsWith("0X") ? key.substring(2) : key;
        String known = ANVIL_DEVELOPMENT_KEY.substring(2);
        return normalized.equalsIgnoreCase(known);
    }

    public record ChainView(String rpcUrl, long chainId, String network, boolean keyConfigured) {
    }

    public String networkName() {
        return chainId == SEPOLIA_CHAIN_ID ? "sepolia" : "local";
    }

    public boolean sync(CertificateEntity certificate) {
        if (certificate.getDocumentHash() == null || certificate.getDocumentHash().isBlank()) {
            certificate.setChainStatus(NOT_ANCHORED);
            certificate.setChainTxHash("");
            certificate.setChainNetwork("");
            return true;
        }
        try {
            if (!ready()) {
                return false;
            }
            byte[] hash = Numeric.hexStringToByteArray(certificate.getDocumentHash());
            OnChain current = read(certificate.getId(), hash);
            String txHash = certificate.getChainTxHash() == null ? "" : certificate.getChainTxHash();
            if (!current.exists()) {
                txHash = send("registerCertificate", List.of(new Utf8String(certificate.getId()), new Bytes32(hash)));
                current = new OnChain(true, false, true);
            } else if (!current.matches()) {
                log.warn("Certificate {} is registered under a different fingerprint.", certificate.getId());
                boolean changed = !NOT_ANCHORED.equals(certificate.getChainStatus())
                        || (certificate.getChainTxHash() != null && !certificate.getChainTxHash().isBlank());
                certificate.setChainStatus(NOT_ANCHORED);
                certificate.setChainTxHash("");
                certificate.setChainNetwork("");
                return changed;
            } else if (!txHash.startsWith("0x")) {
                String recovered = findRegisterTx(certificate.getId());
                if (!recovered.isBlank()) {
                    txHash = recovered;
                }
            }
            if ("REVOKED".equals(certificate.getStatus()) && !current.revoked()) {
                send("revokeCertificate", List.of(new Utf8String(certificate.getId())));
            }
            boolean changed = !ANCHORED.equals(certificate.getChainStatus())
                    || !networkName().equals(certificate.getChainNetwork())
                    || !txHash.equals(certificate.getChainTxHash());
            certificate.setChainStatus(ANCHORED);
            certificate.setChainNetwork(networkName());
            certificate.setChainTxHash(txHash);
            return changed;
        } catch (Exception exception) {
            log.warn("Certificate {} was not anchored: {}", certificate.getId(), exception.getMessage());
            return false;
        }
    }

    private boolean ready() throws Exception {
        if (privateKey == null || privateKey.isBlank()) {
            log.warn("No chain private key is configured.");
            return false;
        }
        if (web3 == null && !connect()) {
            return false;
        }
        if (contractAddress != null && !hasCode(contractAddress)) {
            contractAddress = null;
        }
        if (contractAddress == null) {
            contractAddress = deploy();
        }
        return contractAddress != null;
    }

    private boolean connect() {
        try {
            OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS)
                    .build();
            Web3j client = Web3j.build(new HttpService(rpcUrl, http, false));
            client.ethChainId().send();
            Credentials wallet = Credentials.create(privateKey);
            web3 = client;
            credentials = wallet;
            transactions = new RawTransactionManager(client, wallet, chainId);
            return true;
        } catch (Exception exception) {
            log.warn("Chain node is not reachable at {}: {}", rpcUrl, exception.getMessage());
            web3 = null;
            return false;
        }
    }

    private String deploy() throws Exception {
        ChainDeploymentEntity saved = deployments.findById(1).orElse(null);
        if (saved != null && saved.getChainId() == chainId && hasCode(saved.getContractAddress())) {
            return saved.getContractAddress();
        }
        String bytecode = new String(
                ChainService.class.getResourceAsStream("/chain/CertificateRegistry.bin").readAllBytes(),
                StandardCharsets.US_ASCII).trim();
        TransactionReceipt receipt = waitFor(transactions.sendTransaction(
                gasPrice(),
                BigInteger.valueOf(2_000_000),
                null,
                Numeric.prependHexPrefix(bytecode),
                BigInteger.ZERO).getTransactionHash());
        String address = receipt.getContractAddress();
        ChainDeploymentEntity deployment = saved == null ? new ChainDeploymentEntity() : saved;
        deployment.setId(1);
        deployment.setContractAddress(address);
        deployment.setChainId(chainId);
        deployments.save(deployment);
        log.info("Deployed CertificateRegistry at {} on {}", address, networkName());
        return address;
    }

    private boolean hasCode(String address) throws IOException {
        String code = web3.ethGetCode(address, DefaultBlockParameterName.LATEST).send().getCode();
        return code != null && !code.equals("0x") && !code.equals("0x0");
    }

    private OnChain read(String certificateId, byte[] hash) throws IOException {
        Function function = new Function(
                "verifyCertificate",
                List.of(new Utf8String(certificateId), new Bytes32(hash)),
                List.of(boolType(), boolType(), boolType()));
        var response = web3.ethCall(
                Transaction.createEthCallTransaction(credentials.getAddress(), contractAddress, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST).send();
        if (response.isReverted()) {
            throw new IllegalStateException("Chain verify reverted");
        }
        var decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        return new OnChain(
                ((Bool) decoded.get(0)).getValue(),
                ((Bool) decoded.get(1)).getValue(),
                ((Bool) decoded.get(2)).getValue());
    }

    private String send(String name, List<org.web3j.abi.datatypes.Type> arguments) throws Exception {
        Function function = new Function(name, arguments, List.of());
        String txHash = transactions.sendTransaction(
                gasPrice(),
                BigInteger.valueOf(400_000),
                contractAddress,
                FunctionEncoder.encode(function),
                BigInteger.ZERO).getTransactionHash();
        waitFor(txHash);
        return txHash;
    }

    public AnchorInspection inspect(String txHash, String network) {
        String safeNetwork = network == null ? "" : network;
        String explorer = "sepolia".equals(safeNetwork) && txHash != null && txHash.startsWith("0x")
                ? "https://sepolia.etherscan.io/tx/" + txHash
                : "";
        if (txHash == null || !txHash.startsWith("0x")) {
            return new AnchorInspection("NOT_SUBMITTED", "", safeNetwork, null, "");
        }
        try {
            if ((web3 == null && !connect()) || web3 == null) {
                return new AnchorInspection("UNAVAILABLE", txHash, safeNetwork, null, explorer);
            }
            var receipt = web3.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElse(null);
            if (receipt == null) {
                return new AnchorInspection("PENDING", txHash, safeNetwork, null, explorer);
            }
            Long block = receipt.getBlockNumber() == null ? null : receipt.getBlockNumber().longValue();
            if ("0x0".equals(receipt.getStatus())) {
                return new AnchorInspection("FAILED", txHash, safeNetwork, block, explorer);
            }
            return new AnchorInspection("CONFIRMED", txHash, safeNetwork, block, explorer);
        } catch (Exception exception) {
            web3 = null;
            log.warn("Anchor status could not be read: {}", exception.getMessage());
            try {
                if (!connect()) {
                    return new AnchorInspection("UNAVAILABLE", txHash, safeNetwork, null, explorer);
                }
                var receipt = web3.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElse(null);
                if (receipt == null) {
                    return new AnchorInspection("PENDING", txHash, safeNetwork, null, explorer);
                }
                Long block = receipt.getBlockNumber() == null ? null : receipt.getBlockNumber().longValue();
                if ("0x0".equals(receipt.getStatus())) {
                    return new AnchorInspection("FAILED", txHash, safeNetwork, block, explorer);
                }
                return new AnchorInspection("CONFIRMED", txHash, safeNetwork, block, explorer);
            } catch (Exception retry) {
                web3 = null;
                return new AnchorInspection("UNAVAILABLE", txHash, safeNetwork, null, explorer);
            }
        }
    }

    public record AnchorInspection(String state, String txHash, String network, Long blockNumber, String explorerUrl) {
    }

    private String findRegisterTx(String certificateId) {
        try {
            Event event = new Event("CertificateRegistered", List.of(utf8Type(), bytes32Type()));
            var filter = new org.web3j.protocol.core.methods.request.EthFilter(
                    DefaultBlockParameterName.EARLIEST,
                    DefaultBlockParameterName.LATEST,
                    contractAddress);
            filter.addSingleTopic(EventEncoder.encode(event));
            for (EthLog.LogResult<?> item : web3.ethGetLogs(filter).send().getLogs()) {
                if (!(item.get() instanceof Log entry)) {
                    continue;
                }
                var decoded = FunctionReturnDecoder.decode(entry.getData(), event.getNonIndexedParameters());
                if (!decoded.isEmpty() && certificateId.equals(((Utf8String) decoded.get(0)).getValue())) {
                    return entry.getTransactionHash();
                }
            }
        } catch (Exception exception) {
            log.warn("Could not read registration logs: {}", exception.getMessage());
        }
        return "";
    }

    private BigInteger gasPrice() throws IOException {
        BigInteger price = web3.ethGasPrice().send().getGasPrice();
        return price.signum() > 0 ? price : BigInteger.valueOf(1_000_000_000L);
    }

    private TransactionReceipt waitFor(String txHash) throws Exception {
        if (txHash == null || txHash.isBlank()) {
            throw new IllegalStateException("The chain node did not accept the transaction");
        }
        for (int attempt = 0; attempt < 40; attempt++) {
            var receipt = web3.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (receipt.isPresent()) {
                if ("0x0".equals(receipt.get().getStatus())) {
                    throw new IllegalStateException("Chain transaction reverted: " + txHash);
                }
                return receipt.get();
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Chain transaction was not mined: " + txHash);
    }

    private static TypeReference<Bool> boolType() {
        return new TypeReference<>() {
        };
    }

    private static TypeReference<Utf8String> utf8Type() {
        return new TypeReference<>() {
        };
    }

    private static TypeReference<Bytes32> bytes32Type() {
        return new TypeReference<>() {
        };
    }

    private record OnChain(boolean matches, boolean revoked, boolean exists) {
    }
}
