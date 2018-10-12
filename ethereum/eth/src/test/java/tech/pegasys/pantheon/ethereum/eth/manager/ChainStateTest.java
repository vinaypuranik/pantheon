package net.consensys.pantheon.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;
import net.consensys.pantheon.util.uint.UInt256;

import org.junit.Test;

public class ChainStateTest {

  private static final UInt256 INITIAL_TOTAL_DIFFICULTY = UInt256.of(256);
  private final ChainState chainState = new ChainState();

  @Test
  public void statusReceivedUpdatesBestBlock() {
    final BlockHeader bestBlockHeader = new BlockHeaderTestFixture().number(12).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateBestBlockAndHeightFromHashAndHeight() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(bestBlockHeader.getHash(), blockNumber);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateHeightFromHashAndHeight() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(gen.hash(), blockNumber);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void doesNotUpdateFromOldHashAndHeight() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(gen.hash(), blockNumber);
    chainState.update(gen.hash(), blockNumber - 1);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateBestBlockAndHeightFromHeader() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(bestBlockHeader);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateHeightFromHeader() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    final long newHeaderNumber = blockNumber + 1;
    chainState.update(new BlockHeaderTestFixture().number(newHeaderNumber).buildHeader());
    assertThat(chainState.getEstimatedHeight()).isEqualTo(newHeaderNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void doesNotUpdateFromOldHeader() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(bestBlockHeader);
    chainState.update(new BlockHeaderTestFixture().number(blockNumber - 5).buildHeader());
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateBestBlockAndHeightFromBestBlockHeaderAndTd() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(bestBlockHeader, INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateBestBlockAndHeightFromBetterBlockHeaderAndTd() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    final long betterBlockNumber = blockNumber + 2;
    final UInt256 betterTd = INITIAL_TOTAL_DIFFICULTY.plus(100L);
    final BlockHeader betterBlock =
        new BlockHeaderTestFixture().number(betterBlockNumber).buildHeader();
    chainState.update(betterBlock, betterTd);

    assertThat(chainState.getEstimatedHeight()).isEqualTo(betterBlockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(betterBlockNumber);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(betterBlock.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(betterTd);
  }

  @Test
  public void updateHeightFromBlockHeaderAndTd() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    final long otherBlockNumber = blockNumber + 2;
    final UInt256 otherTd = INITIAL_TOTAL_DIFFICULTY.minus(100L);
    final BlockHeader otherBlock =
        new BlockHeaderTestFixture().number(otherBlockNumber).buildHeader();
    chainState.update(otherBlock, otherTd);

    assertThat(chainState.getEstimatedHeight()).isEqualTo(otherBlockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void doNotUpdateFromOldBlockHeaderAndTd() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(bestBlockHeader, INITIAL_TOTAL_DIFFICULTY);

    final long otherBlockNumber = blockNumber - 2;
    final UInt256 otherTd = INITIAL_TOTAL_DIFFICULTY.minus(100L);
    final BlockHeader otherBlock =
        new BlockHeaderTestFixture().number(otherBlockNumber).buildHeader();
    chainState.update(otherBlock, otherTd);

    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void shouldOnlyHaveHeightEstimateWhenHeightHasBeenSet() {
    chainState.statusReceived(Hash.EMPTY_LIST_HASH, UInt256.ONE);
    assertThat(chainState.hasEstimatedHeight()).isFalse();

    chainState.update(new BlockHeaderTestFixture().number(12).buildHeader());

    assertThat(chainState.hasEstimatedHeight()).isTrue();
  }
}