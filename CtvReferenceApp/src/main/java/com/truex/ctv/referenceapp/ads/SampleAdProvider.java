package com.truex.ctv.referenceapp.ads;

import java.util.ArrayList;
import java.util.List;

public class SampleAdProvider {

    // Sample preroll ad pod with mixed ad types
    public static List<Ad> createPrerollAdBreak() {
        List<Ad> ads = new ArrayList<>();

        // Position 1: true[X] ad (must be first)
        ads.add(new Ad(
            "trueX",
            "https://media.truex.com/m/video/truexloadingplaceholder-30s.mp4",
            "https://get.truex.com/88ac681ba8d0458e413dc22374194ab9f60b6664/vast/config?dimension_5=PI-2449-ctv-ad&ip=108.213.126.254",
            30,
            1,
            "truex-preroll"
        ));

        // Position 2: IDVx ad (can be anywhere)
        ads.add(new Ad(
            "IDVx",
            "https://qa-media.truex.com/m/video/truexloadingplaceholder-30s.mp4",
            "https://qa-get.truex.com/eb9f752aeab71d71dd129da48ed98206e53a96dd/vast/config?ip=108.213.126.254",
            30,
            2,
            "idvx-preroll"
        ));

        // Position 3: Regular ad
        ads.add(new Ad(
            "GDFP",
            "http://media.truex.com/file_assets/2019-01-30/7fe9da33-6b9e-446d-816d-e1aec51a3173.mp4",
            null,
            30,
            3,
            "airline-preroll"
        ));

        // Position 4: Another regular ad
        ads.add(new Ad(
            "GDFP",
            "http://media.truex.com/file_assets/2019-01-30/742eb926-6ec0-48b4-b1e6-093cee334dd1.mp4",
            null,
            30,
            4,
            "pets-preroll"
        ));

        return ads;
    }
}