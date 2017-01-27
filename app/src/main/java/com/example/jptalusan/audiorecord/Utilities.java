package com.example.jptalusan.audiorecord;

import android.util.Log;

import java.text.DateFormat;
import java.util.Date;

/**
 * Created by JPTalusan on 03/01/2017.
 */

public class Utilities {
    //https://www.dsprelated.com/showthread/comp.dsp/29246-1.php

    private static double mRmsSmoothed = 0;

    private static final float MAX_16_BIT = 32768;
    private static final float FUDGE = 0.6f;

    public static String generateFileName() {
        return String.valueOf(System.currentTimeMillis());
    }

    public static String setupDate () {
        String sFileNameTemp;
        DateFormat dfMyTime = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        DateFormat dfMyDate= DateFormat.getDateInstance(DateFormat.SHORT);
        Date dMyDate = new Date(System.currentTimeMillis());
        sFileNameTemp = " " + dfMyDate.format(dMyDate) + " , " + dfMyTime.format(dMyDate);
        return sFileNameTemp;
    }
    //https://www.kvraudio.com/forum/viewtopic.php?t=291758

    public static int calculatePowerDb(short[] bData, int off, int samples)
    {
        double sum = 0;
        double sqsum = 0;
        for (int i = 0; i < samples; i++)
        {
            final long v = bData[off + i];
            sum += v;
            sqsum += v * v;
        }
        double power = (sqsum - sum * sum / samples) / samples;

        power /= MAX_16_BIT * MAX_16_BIT;

        double result = Math.log10(power) * 10f + FUDGE;
        return (int)result;
    }

    //Promising: http://stackoverflow.com/questions/4152201/calculate-decibels
    //bitTo16 = converts byte to short
    //TODO: Check where i got this code?
//    public static int calculatePowerDb(byte[] bData, int off, int samples)
//    {
//        double sum = 0;
//        double sqsum = 0;
//        for (int i = 0; i < samples; i++)
//        {
//            final long v = bData[off + i];
//            sum += v;
//            sqsum += v * v;
//        }
//        double power = (sqsum - sum * sum / samples) / samples;
//
//        power /= MAX_16_BIT * MAX_16_BIT;
//
//        double result = Math.log10(power) * 10f + FUDGE;
//        return (int)result;
//    }

    //http://dsp.stackexchange.com/questions/2951/loudness-of-pcm-stream

    //Smoothing is the reason why the graph does not immediately change from max to min, like the other ones.
//    public static double getPower2(short[] buffer) {
//        double p2 = buffer[0];
//        double decibel;
//        if (p2==0)
//            decibel=Double.NEGATIVE_INFINITY;
//        else
//            decibel = 20.0*Math.log10(p2/65535);
//        return decibel;
//    }

    //TODO: Try using buffer, but get the same computation as above, to see if the equation is the problem or the incoming data
    public static double getPower(byte[] buffer){
        Log.d("UtilRMS:", mRmsSmoothed + "");
//        recorder.read(sData, 0, BufferElements2Rec);
        /*
         * Noise level meter begins here
         */
        // Compute the RMS value. (Note that this does not remove DC).
        double rms = 0;
        for (int i = 0; i < buffer.length; i++) {
            rms += buffer[i] * buffer[i];
        }
        rms = Math.sqrt(rms / buffer.length);
        double mAlpha = 0.9;
        double mGain = 0.0044;
        /*Compute a smoothed version for less flickering of the
        // display.*/
        //TODO: Try removing the smoothing here and then just get the same data and record and check
        mRmsSmoothed = mRmsSmoothed * mAlpha + (1 - mAlpha) * rms;
//		Log.w("rain316", "RMS Smoothed: " + Double.toString(mRmsSmoothed));
        double rmsdB = -1;
        if (mGain * mRmsSmoothed > 0.0f)
            rmsdB = 20.0 * Math.log10(mGain * mRmsSmoothed);
        else rmsdB = -30.0;
        //Log.w("rain316", "RMS dB: " + Double.toString(rmsdB));
        return rmsdB;
    }
}
