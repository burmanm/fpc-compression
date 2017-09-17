package com.github.kutschkem.fpc;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;

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
