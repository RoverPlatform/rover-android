package io.rover.ui;

import android.support.annotation.AnimRes;

/**
 * Created by Rover Labs Inc. on 2017-04-21.
 */

public class ExperienceScreenAnimation {

    @AnimRes public static int ENTER_DEFAULT_ANIMATION = 0;
    @AnimRes public static int EXIT_DEFAULT_ANIMATION = 0;
    @AnimRes public static int POP_ENTER_DEFAULT_ANIMATION = 0;
    @AnimRes public static int POP_EXIT_DEFAULT_ANIMATIION = 0;

    @AnimRes private int mEnter, mExit, mPopEnter, mPopExit;

    public ExperienceScreenAnimation() {
        this(ENTER_DEFAULT_ANIMATION, EXIT_DEFAULT_ANIMATION);
    }

    public ExperienceScreenAnimation(@AnimRes int enter, @AnimRes int exit) {
        this(enter, exit, POP_ENTER_DEFAULT_ANIMATION, POP_EXIT_DEFAULT_ANIMATIION);
    }

    public ExperienceScreenAnimation(@AnimRes int enter, @AnimRes int exit, @AnimRes int popEnter, @AnimRes int popExit) {
        mEnter = enter;
        mExit = exit;
        mPopEnter = popEnter;
        mPopExit = popExit;
    }


    public int getEnter() {
        return mEnter;
    }

    public int getExit() {
        return mExit;
    }

    public int getPopEnter() {
        return mPopEnter;
    }

    public int getPopExit() {
        return mPopExit;
    }
}
