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
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractEthTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class PersistBlockTask<C> extends AbstractEthTask<Block> {

  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final Block block;
  private final HeaderValidationMode validateHeaders;

  private PersistBlockTask(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final Block block,
      final HeaderValidationMode headerValidationMode) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.block = block;
    this.validateHeaders = headerValidationMode;
  }

  public static <C> PersistBlockTask<C> create(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final Block block,
      final HeaderValidationMode headerValidationMode) {
    return new PersistBlockTask<>(protocolSchedule, protocolContext, block, headerValidationMode);
  }

  public static <C> Supplier<CompletableFuture<List<Block>>> forSequentialBlocks(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final List<Block> blocks,
      final HeaderValidationMode headerValidationMode) {
    checkArgument(blocks.size() > 0);
    return () -> {
      final List<Block> successfulImports = new ArrayList<>();
      CompletableFuture<Block> future = null;
      for (final Block block : blocks) {
        if (future == null) {
          future =
              importBlockAndAddToList(
                  protocolSchedule,
                  protocolContext,
                  block,
                  successfulImports,
                  headerValidationMode);
          continue;
        }
        future =
            future.thenCompose(
                b ->
                    importBlockAndAddToList(
                        protocolSchedule,
                        protocolContext,
                        block,
                        successfulImports,
                        headerValidationMode));
      }
      return future.thenApply((r) -> successfulImports);
    };
  }

  private static <C> CompletableFuture<Block> importBlockAndAddToList(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final Block block,
      final List<Block> list,
      final HeaderValidationMode headerValidationMode) {
    return PersistBlockTask.create(protocolSchedule, protocolContext, block, headerValidationMode)
        .run()
        .whenComplete(
            (r, t) -> {
              if (r != null) {
                list.add(r);
              }
            });
  }

  public static <C> Supplier<CompletableFuture<List<Block>>> forUnorderedBlocks(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final List<Block> blocks,
      final HeaderValidationMode headerValidationMode) {
    checkArgument(blocks.size() > 0);
    return () -> {
      final CompletableFuture<List<Block>> finalResult = new CompletableFuture<>();
      final List<Block> successfulImports = new ArrayList<>();
      CompletableFuture<Block> future = null;
      for (final Block block : blocks) {
        if (future == null) {
          future =
              PersistBlockTask.create(
                      protocolSchedule, protocolContext, block, headerValidationMode)
                  .run();
          continue;
        }
        future =
            future
                .handle((r, t) -> r)
                .thenCompose(
                    (r) -> {
                      if (r != null) {
                        successfulImports.add(r);
                      }
                      return PersistBlockTask.create(
                              protocolSchedule, protocolContext, block, headerValidationMode)
                          .run();
                    });
      }
      future.whenComplete(
          (r, t) -> {
            if (r != null) {
              successfulImports.add(r);
            }
            if (successfulImports.size() > 0) {
              finalResult.complete(successfulImports);
            } else {
              finalResult.completeExceptionally(t);
            }
          });

      return finalResult;
    };
  }

  @Override
  protected void executeTask() {
    try {
      final ProtocolSpec<C> protocolSpec =
          protocolSchedule.getByBlockNumber(block.getHeader().getNumber());
      final BlockImporter<C> blockImporter = protocolSpec.getBlockImporter();
      final boolean blockImported =
          blockImporter.importBlock(protocolContext, block, validateHeaders);
      if (!blockImported) {
        result
            .get()
            .completeExceptionally(
                new InvalidBlockException(
                    "Failed to import block", block.getHeader().getNumber(), block.getHash()));
        return;
      }
      result.get().complete(block);
    } catch (final Exception e) {
      result.get().completeExceptionally(e);
    }
  }
}
