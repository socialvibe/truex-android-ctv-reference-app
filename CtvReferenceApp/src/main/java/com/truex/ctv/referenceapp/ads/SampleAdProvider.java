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
            //"https://qa-get.truex.com/dc2b568f1da7c8698e3c0e14f99414247db0b4fa/vast/config?ip=108.213.126.253",
            //"https://qa-get.truex.com/745a152e9fcad6248eaa4ef6ae818b4462f9fcb6/vast/config?ip=108.213.126.254",
            "https://s3.us-east-1.amazonaws.com/stash.truex.com/sample-tags/deleteme.json?ip=108.213.126.253",
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