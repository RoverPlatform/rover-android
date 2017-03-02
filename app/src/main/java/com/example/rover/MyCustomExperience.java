package com.example.rover;

import io.rover.ExperienceActivity;
import io.rover.model.Experience;

/**
 * Created by Rover Labs Inc. on 2017-03-01.
 */

public class MyCustomExperience extends ExperienceActivity {


    @Override
    public void setExperience(Experience experience) {
        /*
            Here you have the chance to modify the experience before it gets displayed
            Always call super once you are done with your edits!
         */
        super.setExperience(experience);
    }
}
