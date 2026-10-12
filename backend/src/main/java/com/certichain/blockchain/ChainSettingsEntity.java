package com.certichain.blockchain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chain_settings")
public class ChainSettingsEntity {

    @Id
    private Integer id;

    @Column(name = "rpc_url", nullable = false)
    private String rpcUrl;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "private_key", nullable = false)
    private String privateKey = "";

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getRpcUrl() {
        return rpcUrl;
    }

    public void setRpcUrl(String rpcUrl) {
        this.rpcUrl = rpcUrl;
    }

    public long getChainId() {
        return chainId;
    }

    public void setChainId(long chainId) {
        this.chainId = chainId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }
}
