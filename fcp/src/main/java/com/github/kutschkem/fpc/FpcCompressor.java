/*******************************************************************************
 * Copyright (c) 2013 Michael Kutschke.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Michael Kutschke - initial API and implementation
 ******************************************************************************/
package com.github.kutschkem.fpc;

import java.nio.ByteBuffer;

public class FpcCompressor {

    private FcmPredictor predictor1;
    private DfcmPredictor predictor2;

    private static final int DEFAULT_logOfTableSize = 16;

    public FpcCompressor(int logOfTableSize) {
        predictor1 = new FcmPredictor(logOfTableSize);
        predictor2 = new DfcmPredictor(logOfTableSize);
    }

    public FpcCompressor() {
        predictor1 = new FcmPredictor(DEFAULT_logOfTableSize);
        predictor2 = new DfcmPredictor(DEFAULT_logOfTableSize);
    }

    public void compress(ByteBuffer buff, double[] doubles) {
        for (int i = 0; i < doubles.length - 1; i += 2) {
            encode(buff, doubles[i], doubles[i + 1]);
        }
        if((doubles.length & 1) != 0) {
            encodeAndPad(buff, doubles[doubles.length - 1]);
        }
    }

    public void decompress(ByteBuffer buff, double[] dest) {
        for (int i = 0; i < dest.length; i += 2) {
            decode(buff, dest, i);
        }
    }

    private void decode(ByteBuffer buff, double[] dest, int i) {
        byte header = buff.get();

        long prediction;

        if ((header & 0x80) != 0) {
            prediction = predictor2.getPrediction();
        } else {
            prediction = predictor1.getPrediction();
        }

        int numZeroBytes = (header & 0x70) >> 4;
        if (numZeroBytes > 3) {
            numZeroBytes++;
        }
        byte[] dst = new byte[8 - numZeroBytes];
        System.out.printf("dst size: %d\n", dst.length);
        buff.get(dst);
        long diff = toLong(dst);
        long actual = prediction ^ diff;

        predictor1.update(actual);
        predictor2.update(actual);

        dest[i] = Double.longBitsToDouble(actual);

        if ((header & 0x08) != 0) {
            prediction = predictor2.getPrediction();
        } else {
            prediction = predictor1.getPrediction();
        }

        numZeroBytes = (header & 0x07);
        if (numZeroBytes > 3) {
            numZeroBytes++;
        }
        dst = new byte[8 - numZeroBytes];
        buff.get(dst);
        diff = toLong(dst);

        if (numZeroBytes == 7 && diff == 0) {
            return;
        }
        actual = prediction ^ diff;

        predictor1.update(actual);
        predictor2.update(actual);

        dest[i + 1] = Double.longBitsToDouble(actual);
    }

    public long toLong(byte[] dst) {
        long result = 0L;
        for (int i = dst.length; i > 0; i--) {
            result = result << 8;
            result |= dst[i - 1] & 0xff;
        }
        return result;
    }

    private void encodeAndPad(ByteBuffer buf, double d) {

        long dBits = Double.doubleToRawLongBits(d);
        long diff1d = predictor1.getPrediction() ^ dBits;
        long diff2d = predictor2.getPrediction() ^ dBits;

        boolean predictor1BetterForD = Long.numberOfLeadingZeros(diff1d) >= Long.numberOfLeadingZeros(diff2d);

        predictor1.update(dBits);
        predictor2.update(dBits);

        byte code = 0;
        if(!predictor1BetterForD) {
            code |= 0x80;
            diff1d = diff2d;
        }
        int zeroBytes = Long.numberOfLeadingZeros(diff1d) >> 3;
        code |= headerZeroBytes(zeroBytes) << 4;
        code |= 0x06;
        buf.put(code);
        pushByteArray(buf, diff1d, Long.BYTES - zeroBytes);
        buf.put((byte) 0);
    }

    private int headerZeroBytes(int leadingZeroBytes) {
        switch(leadingZeroBytes) {
            case 8:
            case 7:
            case 6:
            case 5:
                return --leadingZeroBytes;
            case 4:
                return 3;
            case 3:
            case 2:
            case 1:
            case 0:
                return leadingZeroBytes;
            default:
                return leadingZeroBytes;
        }
    }

    private void encode(ByteBuffer buf, double d, double e) {

        // predictor1=FCM, 2=DFCM

        long dBits = Double.doubleToRawLongBits(d);
        long diff1d = predictor1.getPrediction() ^ dBits;
        long diff2d = predictor2.getPrediction() ^ dBits;

        boolean predictor1BetterForD = Long.numberOfLeadingZeros(diff1d) >= Long.numberOfLeadingZeros(diff2d);

        predictor1.update(dBits);
        predictor2.update(dBits);

        long eBits = Double.doubleToRawLongBits(e);
        long diff1e = predictor1.getPrediction() ^ eBits;
        long diff2e = predictor2.getPrediction() ^ eBits;

        boolean predictor1BetterForE = Long.numberOfLeadingZeros(diff1e) >= Long.numberOfLeadingZeros(diff2e);

        predictor1.update(eBits);
        predictor2.update(eBits);

        byte code = 0;

        if(!predictor1BetterForD) {
            code |= 0x80;
            diff1d = diff2d;
        }

        int zeroBytesD = Long.numberOfLeadingZeros(diff1d) >> 3;
        code |= headerZeroBytes(zeroBytesD) << 4;

        if(!predictor1BetterForE) {
            code |= 0x08;
            diff1e = diff2e;
        }

        int zeroBytesE = Long.numberOfLeadingZeros(diff1e) >> 3;
        code |= headerZeroBytes(zeroBytesE);

        buf.put(code);
        pushByteArray(buf, diff1d, Long.BYTES - zeroBytesD);
        pushByteArray(buf, diff1e, Long.BYTES - zeroBytesE);
    }

    public void pushByteArray(ByteBuffer buf, long diff, int bytes) {
        switch(bytes) {
            case 8:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                buf.put((byte) (diff >>> 24));
                buf.put((byte) (diff >>> 32));
                buf.put((byte) (diff >>> 40));
                buf.put((byte) (diff >>> 48));
                buf.put((byte) (diff >>> 56));
                break;
            case 7:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                buf.put((byte) (diff >>> 24));
                buf.put((byte) (diff >>> 32));
                buf.put((byte) (diff >>> 40));
                buf.put((byte) (diff >>> 48));
                break;
            case 6:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                buf.put((byte) (diff >>> 24));
                buf.put((byte) (diff >>> 32));
                buf.put((byte) (diff >>> 40));
                break;
            case 5:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                buf.put((byte) (diff >>> 24));
                buf.put((byte) (diff >>> 32));
                break;
            case 4:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                buf.put((byte) (diff >>> 24));
                break;
            case 3:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                break;
            case 2:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                break;
            case 1:
                buf.put((byte) (diff & 0xFF));
                break;
        }
    }
}
