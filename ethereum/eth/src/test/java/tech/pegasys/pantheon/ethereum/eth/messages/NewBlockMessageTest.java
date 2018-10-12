package net.consensys.pantheon.ethereum.eth.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.wire.RawMessage;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import io.netty.buffer.Unpooled;
import org.junit.Test;

public class NewBlockMessageTest {
  private static final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();

  @Test
  public void roundTripNewBlockMessage() {
    final UInt256 totalDifficulty = UInt256.of(98765);
    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block blockForInsertion = blockGenerator.block();

    final NewBlockMessage msg = NewBlockMessage.create(blockForInsertion, totalDifficulty);
    assertThat(msg.getCode()).isEqualTo(EthPV62.NEW_BLOCK);
    assertThat(msg.totalDifficulty(protocolSchedule)).isEqualTo(totalDifficulty);
    final Block extractedBlock = msg.block(protocolSchedule);
    assertThat(extractedBlock).isEqualTo(blockForInsertion);
  }

  @Test
  public void rawMessageUpCastsToANewBlockMessage() {
    final UInt256 totalDifficulty = UInt256.of(12345);
    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block blockForInsertion = blockGenerator.block();

    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    blockForInsertion.writeTo(tmp);
    tmp.writeUInt256Scalar(totalDifficulty);
    tmp.endList();

    final BytesValue msgPayload = tmp.encoded();

    final RawMessage rawMsg =
        new RawMessage(EthPV62.NEW_BLOCK, Unpooled.wrappedBuffer(tmp.encoded().extractArray()));

    final NewBlockMessage newBlockMsg = NewBlockMessage.readFrom(rawMsg);

    assertThat(newBlockMsg.getCode()).isEqualTo(EthPV62.NEW_BLOCK);
    assertThat(newBlockMsg.totalDifficulty(protocolSchedule)).isEqualTo(totalDifficulty);
    final Block extractedBlock = newBlockMsg.block(protocolSchedule);
    assertThat(extractedBlock).isEqualTo(blockForInsertion);
  }

  @Test
  public void readFromMessageWithWrongCodeThrows() {
    final ProtocolSchedule<Void> protSchedule = MainnetProtocolSchedule.create();
    final RawMessage rawMsg = new RawMessage(EthPV62.BLOCK_HEADERS, NetworkMemoryPool.allocate(1));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> NewBlockMessage.readFrom(rawMsg));
  }
}