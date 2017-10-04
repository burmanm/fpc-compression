package com.github.kutschkem.fpc;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

public class FcpCompressorTest {

    @Test
    public void compressMore() {
        double[] doubles = new double[] { 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0000000005460000000000000000001,
                0.0, 1.1, 0.0, 1.0, 2.0, 3.0, 4.0, 5.0,
                6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 12.0, 0.5, 1.0, 1.5, 2.0 };
        FpcCompressor fpc = new FpcCompressor(10);
        ByteBuffer buffer = ByteBuffer.allocate(256);
        fpc.compress(buffer, doubles);

        buffer.flip();

        double[] dest = new double[doubles.length];
        fpc = new FpcCompressor(10);
        fpc.decompress(buffer, dest);

        for(int i = 0; i < doubles.length; i++) {
            assertEquals(Double.doubleToRawLongBits(doubles[i]), Double.doubleToRawLongBits(dest[i]));
        }
    }

//    @Test
    public void smallDeCompressPerfTest() {
        double[] doubles = new double[8192];
        double[] doublesU = new double[8192];
        for(int i = 0; i < doubles.length; i++) {
            doubles[i] = ThreadLocalRandom.current().nextDouble();
        }

        ByteBuffer bb = ByteBuffer.allocate(8192*Double.BYTES*2);

        FpcCompressor fpc = new FpcCompressor(10);
        fpc.compress(bb, doubles);

        bb.flip();

        long startTime = System.nanoTime();
        for(int i = 0; i < 100000; i++) {
            fpc = new FpcCompressor(10);
            fpc.decompress(bb, doublesU);
            bb.flip();
        }
        long endTime = System.nanoTime();
        double seconds = (endTime - startTime) / Math.pow(10, 9);
        double throughput = (8192*100000 / seconds) / Math.pow(10, 6);
        // Original:
//        Time it took to decompress 1000 times: 3.476551 s, throughput: 23.563581 M/s
//        Time it took to decompress 1000 times: 1.473559 s, throughput: 55.593308 M/s
//        Time it took to decompress 100000 times: 13.428478 s, throughput: 61.004679 M/s
        System.out.printf("Time it took to decompress 100000 times: %f s, throughput: %f M/s\n", seconds, throughput);
    }

    @Test
    public void testRoundtripWithTwoValues() {
        double[] doubles = new double[] { 1.0, 0.0 };
        FpcCompressor fpc = new FpcCompressor();

        ByteBuffer buffer = ByteBuffer.allocate(64);
        fpc.compress(buffer, doubles);

        buffer.flip();

        FpcCompressor decompressor = new FpcCompressor();

        double[] dest = new double[2];
        decompressor.decompress(buffer, dest);

        assertThat(dest, is(doubles));
    }

    @Test
    public void testRoundtripWithThreeValues() {
        double[] doubles = new double[] { 1.0, 0.0, 0.5 };
        FpcCompressor fpc = new FpcCompressor();

        ByteBuffer buffer = ByteBuffer.allocate(64);
        fpc.compress(buffer, doubles);

        buffer.flip();

        FpcCompressor decompressor = new FpcCompressor();

        double[] dest = new double[3];
        decompressor.decompress(buffer, dest);

        assertThat(dest, is(doubles));
    }

}
