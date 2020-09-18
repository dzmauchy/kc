package org.ku.kc.concurrent;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("normal")
class ParallelForkTest {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Test
  void parallelFork() {
    int parallelism = ForkJoinPool.commonPool().getParallelism() + 2;
    var counter = new AtomicInteger();
    logger.info("Starting");
    IntStream.range(0, parallelism).parallel()
      .peek(i -> LockSupport.parkNanos(100_000_000L))
      .forEach(i -> {
        logger.info("{}", i);
        IntStream.range(0, 10).parallel()
          .peek(j -> LockSupport.parkNanos(10_000_000L))
          .forEach(j -> counter.incrementAndGet());
      });
    assertEquals(parallelism * 10, counter.get());
  }
}
