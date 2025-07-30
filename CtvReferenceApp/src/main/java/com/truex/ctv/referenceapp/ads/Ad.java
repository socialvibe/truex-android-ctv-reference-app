package com.truex.ctv.referenceapp.ads;

public class Ad {
    public String adSystem;
    public String adUrl;
    public String vastConfigUrl;
    public int duration;
    public int position;
    public String adId;
    public AdType adType;

    public Ad(String adSystem, String adUrl, String vastConfigUrl, int duration, int position, String adId) {
        this.adSystem = adSystem;
        this.adUrl = adUrl;
        this.vastConfigUrl = vastConfigUrl;
        this.duration = duration;
        this.position = position;
        this.adId = adId;
        this.adType = determineAdType(adSystem);
    }

    private AdType determineAdType(String adSystem) {
        if ("trueX".equals(adSystem)) {
            return AdType.TRUEX;
        } else if ("IDVx".equals(adSystem)) {
            return AdType.IDVX;
        } else {
            return AdType.REGULAR;
        }
    }
    public boolean isInfillionAd() {
        return adType == AdType.TRUEX || adType == AdType.IDVX;
    }

    public boolean isRegularAd() {
        return adType == AdType.REGULAR;
    }
}