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

    private FcmPredictor fcmPredictor;
    private DfcmPredictor dfcmPredictor;

    static int[] LEADING_BYTES_TO_HEADER_FIRST = new int[]{0, 1<<4, 2<<4, 3<<4, 3<<4, 4<<4, 5<<4, 6<<4, 7<<4};
    static int[] LEADING_BYTES_TO_HEADER = new int[]{0,1,2,3,3,4,5,6,7};
    static int[] HEADER_BYTES_TO_LEADING = new int[]{0,1,2,3,5,6,7,8};

    private static final int DEFAULT_logOfTableSize = 16;

    public FpcCompressor(int logOfTableSize) {
        fcmPredictor = new FcmPredictor(logOfTableSize);
        dfcmPredictor = new DfcmPredictor(logOfTableSize);
    }

    public FpcCompressor() {
        fcmPredictor = new FcmPredictor(DEFAULT_logOfTableSize);
        dfcmPredictor = new DfcmPredictor(DEFAULT_logOfTableSize);
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
        // If uneven amount of doubles is requested, remove last double - it's not real
    }

    private void process(ByteBuffer buff, double[] dest, int i, int numZeroBytes, long prediction) {
        long diff = getLong(buff, numZeroBytes);

        long actual = prediction ^ diff;

        fcmPredictor.update(actual);
        dfcmPredictor.update(actual);

        dest[i] = Double.longBitsToDouble(actual);
    }

    private void decode(ByteBuffer buff, double[] dest, int i) {
        byte header = buff.get();
        long prediction;

        if ((header & 0x80) != 0) {
            prediction = dfcmPredictor.getPrediction();
        } else {
            prediction = fcmPredictor.getPrediction();
        }

        int numZeroBytes = HEADER_BYTES_TO_LEADING[(header & 0x70) >> 4];
        long diff = getLong(buff, numZeroBytes);

        long actual = prediction ^ diff;

        fcmPredictor.update(actual);
        dfcmPredictor.update(actual);

        dest[i] = Double.longBitsToDouble(actual);

        if ((header & 0x08) != 0) {
            prediction = dfcmPredictor.getPrediction();
        } else {
            prediction = fcmPredictor.getPrediction();
        }

        numZeroBytes = HEADER_BYTES_TO_LEADING[(header & 0x07)];

        diff = getLong(buff, numZeroBytes);

        if (numZeroBytes == 7 && diff == 0) {
            return;
        }

        actual = prediction ^ diff;

        fcmPredictor.update(actual);
        dfcmPredictor.update(actual);

        dest[i + 1] = Double.longBitsToDouble(actual);
    }

//    private void decode(ByteBuffer buff, double[] dest, int i) {
//        byte header = buff.get();
//        long prediction;
//
//        switch(header & 0x88) {
//            case 0x80:
//                // First is DFCM
//                prediction = dfcmPredictor.getPrediction();
//                process(buff, dest, i, HEADER_BYTES_TO_LEADING[(header & 0x70) >> 4], prediction);
//                prediction = fcmPredictor.getPrediction();
//                process(buff, dest, i+1, HEADER_BYTES_TO_LEADING[(header & 0x07)], prediction);
//                break;
//            case 0x08:
//                // Second is DFCM
//                prediction = fcmPredictor.getPrediction();
//                process(buff, dest, i, HEADER_BYTES_TO_LEADING[(header & 0x70) >> 4], prediction);
//                prediction = dfcmPredictor.getPrediction();
//                process(buff, dest, i+1, HEADER_BYTES_TO_LEADING[(header & 0x07)], prediction);
//                break;
//            case 0x88:
//                // Both are DFCM
//                prediction = dfcmPredictor.getPrediction();
//                process(buff, dest, i, HEADER_BYTES_TO_LEADING[(header & 0x70) >> 4], prediction);
//                prediction = dfcmPredictor.getPrediction();
//                process(buff, dest, i+1, HEADER_BYTES_TO_LEADING[(header & 0x07)], prediction);
//                break;
//            default:
//                // Both are FCM
//                prediction = fcmPredictor.getPrediction();
//                process(buff, dest, i, HEADER_BYTES_TO_LEADING[(header & 0x70) >> 4], prediction);
//                prediction = fcmPredictor.getPrediction();
//                process(buff, dest, i+1, HEADER_BYTES_TO_LEADING[(header & 0x07)], prediction);
//                break;
//        }
//    }

    public long getLong(ByteBuffer buf, int leadingBytes) {
        long value = 0;
        switch(leadingBytes) {
            case 0:
                value |= (long) buf.get() & 0xFF;
                value |= ((long) buf.get() & 0xFF) << 8;
                value |= ((long) buf.get() & 0xFF) << 16;
                value |= ((long) buf.get() & 0xFF) << 24;
                value |= ((long) buf.get() & 0xFF) << 32;
                value |= ((long) buf.get() & 0xFF) << 40;
                value |= ((long) buf.get() & 0xFF) << 48;
                value |= ((long) buf.get() & 0xFF) << 56;
                break;
            case 1:
                value |= (long) buf.get() & 0xFF;
                value |= ((long) buf.get() & 0xFF) << 8;
                value |= ((long) buf.get() & 0xFF) << 16;
                value |= ((long) buf.get() & 0xFF) << 24;
                value |= ((long) buf.get() & 0xFF) << 32;
                value |= ((long) buf.get() & 0xFF) << 40;
                value |= ((long) buf.get() & 0xFF) << 48;
                break;
            case 2:
                value |= (long) buf.get() & 0xFF;
                value |= ((long) buf.get() & 0xFF) << 8;
                value |= ((long) buf.get() & 0xFF) << 16;
                value |= ((long) buf.get() & 0xFF) << 24;
                value |= ((long) buf.get() & 0xFF) << 32;
                value |= ((long) buf.get() & 0xFF) << 40;
                break;
            case 3:
                value |= (long) buf.get() & 0xFF;
                value |= ((long) buf.get() & 0xFF) << 8;
                value |= ((long) buf.get() & 0xFF) << 16;
                value |= ((long) buf.get() & 0xFF) << 24;
                value |= ((long) buf.get() & 0xFF) << 32;
                break;
            case 4:
                value |= (long) buf.get() & 0xFF;
                value |= ((long) buf.get() & 0xFF) << 8;
                value |= ((long) buf.get() & 0xFF) << 16;
                value |= ((long) buf.get() & 0xFF) << 24;
                break;
            case 5:
                value |= (long) buf.get() & 0xFF;
                value |= ((long) buf.get() & 0xFF) << 8;
                value |= ((long) buf.get() & 0xFF) << 16;
                break;
            case 6:
                value |= (long) buf.get() & 0xFF;
                value |= ((long) buf.get() & 0xFF) << 8;
                break;
            case 7:
                value = (long) buf.get() & 0xFF;
                break;
        }
        return value;
    }

    private void encodeAndPad(ByteBuffer buf, double d) {
        long dBits = Double.doubleToRawLongBits(d);
        long diff1d = fcmPredictor.getPrediction() ^ dBits;
        long diff2d = dfcmPredictor.getPrediction() ^ dBits;

        fcmPredictor.update(dBits);
        dfcmPredictor.update(dBits);

        byte code = 0;
        if(diff1d > diff2d) {
            code |= 0x80;
            diff1d = diff2d;
        }

        int zeroBytes = Long.numberOfLeadingZeros(diff1d) >> 3;
        code |= LEADING_BYTES_TO_HEADER_FIRST[zeroBytes];
        code |= 0x06;
        buf.put(code);
        pushByteArray(buf, diff1d, zeroBytes);
        buf.put((byte) 0);
    }

    private void encode(ByteBuffer buf, double d, double e) {
        long dBits = Double.doubleToRawLongBits(d);
        long diff1d = fcmPredictor.getPrediction() ^ dBits;
        long diff2d = dfcmPredictor.getPrediction() ^ dBits;

        fcmPredictor.update(dBits);
        dfcmPredictor.update(dBits);

        long eBits = Double.doubleToRawLongBits(e);
        long diff1e = fcmPredictor.getPrediction() ^ eBits;
        long diff2e = dfcmPredictor.getPrediction() ^ eBits;

        fcmPredictor.update(eBits);
        dfcmPredictor.update(eBits);

        byte code = 0;

        if(diff1d > diff2d) {
            code |= 0x80;
            diff1d = diff2d;
        }

        int zeroBytesD = Long.numberOfLeadingZeros(diff1d) >> 3;
        code |= LEADING_BYTES_TO_HEADER_FIRST[zeroBytesD];

        if(diff1e > diff2e) {
            code |= 0x08;
            diff1e = diff2e;
        }

        int zeroBytesE = Long.numberOfLeadingZeros(diff1e) >> 3;
        code |= LEADING_BYTES_TO_HEADER[zeroBytesE];

        buf.put(code);
        pushByteArray(buf, diff1d, zeroBytesD);
        pushByteArray(buf, diff1e, zeroBytesE);
    }

    public void pushByteArray(ByteBuffer buf, long diff, int leadingBytes) {
        switch(leadingBytes) {
            case 0:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                buf.put((byte) (diff >>> 24));
                buf.put((byte) (diff >>> 32));
                buf.put((byte) (diff >>> 40));
                buf.put((byte) (diff >>> 48));
                buf.put((byte) (diff >>> 56));
                break;
            case 1:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                buf.put((byte) (diff >>> 24));
                buf.put((byte) (diff >>> 32));
                buf.put((byte) (diff >>> 40));
                buf.put((byte) (diff >>> 48));
                break;
            case 2:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                buf.put((byte) (diff >>> 24));
                buf.put((byte) (diff >>> 32));
                buf.put((byte) (diff >>> 40));
                break;
            case 3:
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
                buf.put((byte) (diff >>> 32));
                break;
            case 5:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                buf.put((byte) (diff >>> 16));
                break;
            case 6:
                buf.put((byte) (diff & 0xFF));
                buf.put((byte) (diff >>> 8));
                break;
            case 7:
                buf.put((byte) (diff & 0xFF));
                break;
        }
    }
}
