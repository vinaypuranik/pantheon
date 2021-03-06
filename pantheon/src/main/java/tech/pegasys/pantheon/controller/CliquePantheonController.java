/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.controller;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.config.CliqueConfigOptions;
import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueVoteTallyUpdater;
import tech.pegasys.pantheon.consensus.clique.VoteTallyCache;
import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueBlockScheduler;
import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueMinerExecutor;
import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueMiningCoordinator;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHashFunction;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.db.DefaultMutableBlockchain;
import tech.pegasys.pantheon.ethereum.db.KeyValueStoragePrefixedKeyBlockchainStorage;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.sync.DefaultSynchronizer;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolFactory;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import tech.pegasys.pantheon.ethereum.p2p.api.ProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.RocksDbKeyValueStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;

public class CliquePantheonController implements PantheonController<CliqueContext> {

  private static final Logger LOG = getLogger();
  private final GenesisConfig<CliqueContext> genesisConfig;
  private final ProtocolContext<CliqueContext> context;
  private final Synchronizer synchronizer;
  private final ProtocolManager ethProtocolManager;
  private final KeyPair keyPair;
  private final TransactionPool transactionPool;
  private final Runnable closer;

  private final MiningCoordinator miningCoordinator;

  CliquePantheonController(
      final GenesisConfig<CliqueContext> genesisConfig,
      final ProtocolContext<CliqueContext> context,
      final ProtocolManager ethProtocolManager,
      final Synchronizer synchronizer,
      final KeyPair keyPair,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final Runnable closer) {

    this.genesisConfig = genesisConfig;
    this.context = context;
    this.ethProtocolManager = ethProtocolManager;
    this.synchronizer = synchronizer;
    this.keyPair = keyPair;
    this.transactionPool = transactionPool;
    this.closer = closer;
    this.miningCoordinator = miningCoordinator;
  }

  public static PantheonController<CliqueContext> init(
      final Path home,
      final GenesisConfig<CliqueContext> genesisConfig,
      final SynchronizerConfiguration taintedSyncConfig,
      final MiningParameters miningParams,
      final CliqueConfigOptions cliqueConfig,
      final int networkId,
      final KeyPair nodeKeys)
      throws IOException {
    final long blocksPerEpoch = cliqueConfig.getEpochLength();
    final long secondsBetweenBlocks = cliqueConfig.getBlockPeriodSeconds();

    final EpochManager epochManger = new EpochManager(blocksPerEpoch);
    final KeyValueStorage kv =
        RocksDbKeyValueStorage.create(Files.createDirectories(home.resolve(DATABASE_PATH)));
    final ProtocolSchedule<CliqueContext> protocolSchedule = genesisConfig.getProtocolSchedule();

    final BlockHashFunction blockHashFunction =
        ScheduleBasedBlockHashFunction.create(protocolSchedule);
    final KeyValueStoragePrefixedKeyBlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(kv, blockHashFunction);
    final MutableBlockchain blockchain =
        new DefaultMutableBlockchain(genesisConfig.getBlock(), blockchainStorage);

    final KeyValueStorageWorldStateStorage worldStateStorage =
        new KeyValueStorageWorldStateStorage(kv);
    final WorldStateArchive worldStateArchive = new WorldStateArchive(worldStateStorage);
    genesisConfig.writeStateTo(worldStateArchive.getMutable(Hash.EMPTY_TRIE_HASH));

    final ProtocolContext<CliqueContext> protocolContext =
        new ProtocolContext<>(
            blockchain,
            worldStateArchive,
            new CliqueContext(
                new VoteTallyCache(
                    blockchain, new CliqueVoteTallyUpdater(epochManger), epochManger),
                new VoteProposer(),
                epochManger));

    final SynchronizerConfiguration syncConfig = taintedSyncConfig.validated(blockchain);
    final boolean fastSyncEnabled = syncConfig.syncMode().equals(SyncMode.FAST);
    final EthProtocolManager ethProtocolManager =
        new EthProtocolManager(
            protocolContext.getBlockchain(),
            genesisConfig.getChainId(),
            fastSyncEnabled,
            networkId);
    final SyncState syncState =
        new SyncState(
            protocolContext.getBlockchain(), ethProtocolManager.ethContext().getEthPeers());
    final Synchronizer synchronizer =
        new DefaultSynchronizer<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethProtocolManager.ethContext(),
            syncState);

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule, protocolContext, ethProtocolManager.ethContext());

    final ExecutorService minerThreadPool = Executors.newCachedThreadPool();
    final CliqueMinerExecutor miningExecutor =
        new CliqueMinerExecutor(
            protocolContext,
            minerThreadPool,
            protocolSchedule,
            transactionPool.getPendingTransactions(),
            nodeKeys,
            miningParams,
            new CliqueBlockScheduler(
                Clock.systemUTC(),
                protocolContext.getConsensusState().getVoteTallyCache(),
                Util.publicKeyToAddress(nodeKeys.getPublicKey()),
                secondsBetweenBlocks),
            epochManger);
    final CliqueMiningCoordinator miningCoordinator =
        new CliqueMiningCoordinator(blockchain, miningExecutor, syncState);
    miningCoordinator.addMinedBlockObserver(ethProtocolManager);

    // Clique mining is implicitly enabled.
    miningCoordinator.enable();

    return new CliquePantheonController(
        genesisConfig,
        protocolContext,
        ethProtocolManager,
        synchronizer,
        nodeKeys,
        transactionPool,
        miningCoordinator,
        () -> {
          miningCoordinator.disable();
          minerThreadPool.shutdownNow();
          try {
            minerThreadPool.awaitTermination(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            LOG.error("Failed to shutdown miner executor");
          }
          try {
            kv.close();
          } catch (final IOException e) {
            LOG.error("Failed to close key value storage", e);
          }
        });
  }

  @Override
  public ProtocolContext<CliqueContext> getProtocolContext() {
    return context;
  }

  @Override
  public GenesisConfig<CliqueContext> getGenesisConfig() {
    return genesisConfig;
  }

  @Override
  public Synchronizer getSynchronizer() {
    return synchronizer;
  }

  @Override
  public SubProtocolConfiguration subProtocolConfiguration() {
    return new SubProtocolConfiguration().withSubProtocol(EthProtocol.get(), ethProtocolManager);
  }

  @Override
  public KeyPair getLocalNodeKeyPair() {
    return keyPair;
  }

  @Override
  public TransactionPool getTransactionPool() {
    return transactionPool;
  }

  @Override
  public MiningCoordinator getMiningCoordinator() {
    return miningCoordinator;
  }

  @Override
  public void close() {
    closer.run();
  }
}
