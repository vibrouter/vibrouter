package io.github.vibrouter;

import android.content.Context;
import android.os.Vibrator;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.VIBRATOR_SERVICE;

/**
 * Created by yuishihara on 2017/02/04.
 */

public class VibrationController {
    public static final int PATTERN_FORWARD = 0;
    public static final int PATTERN_LEFT = 1;
    public static final int PATTERN_RIGHT = 2;
    public static final int PATTERN_ARRIVE = 7;
    public static final int NONE = -1;

    private static final String TAG = VibrationController.class.getSimpleName();
    private static final String FORWARD_PATTERN_FILE = "forward.csv";
    private static final String LEFT_PATTERN_FILE = "left.csv";
    private static final String RIGHT_PATTERN_FILE = "right.csv";
    private static final String ARRIVE_PATTERN_FILE = "goal.csv";

    private Context mContext;

    private long[] mForwardPattern;
    private long[] mLeftPattern;
    private long[] mRightPattern;
    private long[] mArrivePattern;

    private int mState = NONE;

    private Vibrator mVibrator;

    public VibrationController(Context context) {
        mContext = context;
        mForwardPattern = readPatternFromFile(FORWARD_PATTERN_FILE);
        mLeftPattern = readPatternFromFile(LEFT_PATTERN_FILE);
        mRightPattern = readPatternFromFile(RIGHT_PATTERN_FILE);
        mArrivePattern = readPatternFromFile(ARRIVE_PATTERN_FILE);

        mVibrator = (Vibrator) mContext.getSystemService(VIBRATOR_SERVICE);
    }

    public void startVibrate(int pattern) {
        if (pattern == mState) {
            return;
        }
        switch (pattern) {
            case PATTERN_FORWARD:
                mVibrator.vibrate(mForwardPattern, 0);
                break;
            case PATTERN_LEFT:
                mVibrator.vibrate(mLeftPattern, 0);
                break;
            case PATTERN_RIGHT:
                mVibrator.vibrate(mRightPattern, 0);
                break;
            case PATTERN_ARRIVE:
                mVibrator.vibrate(mArrivePattern, -1);
                break;
            default:
                break;
        }
        mState = pattern;
    }

    public void stopVibrate() {
        mState = NONE;
        mVibrator.cancel();
    }

    private long[] readPatternFromFile(String fileName) {
        InputStream inputStream = null;
        BufferedReader reader = null;
        try {
            inputStream = mContext.getAssets().open(fileName);
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            List<String> timeMillis = new ArrayList<>();
            Log.e(TAG, "Reading file!");
            while ((line = reader.readLine()) != null) {
                for (String millis : line.split(",")) {
                    timeMillis.add(millis);
                }
            }
            long[] pattern = new long[timeMillis.size()];
            for (int i = 0; i < timeMillis.size(); ++i) {
                pattern[i] = Integer.parseInt(timeMillis.get(i));
            }
            Log.e(TAG, "pattern of " + fileName + " read!");
            return pattern;
        } catch (IOException failedReading) {
            // Do nothing
            Log.e(TAG, "Failed reading file");
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException failedClosing) {
                // Do nothing
            }
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException failedClosingStream) {
                // Do nothing
            }
        }
        return new long[0];
    }
}
