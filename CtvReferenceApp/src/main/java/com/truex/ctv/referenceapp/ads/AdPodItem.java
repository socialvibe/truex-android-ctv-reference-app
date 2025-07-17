package com.truex.ctv.referenceapp.ads;

public class AdPodItem {
    private String adSystem;
    private String adUrl;
    private String vastConfigUrl;
    private int duration;
    private int position;
    private String adId;
    private AdType adType;

    public AdPodItem(String adSystem, String adUrl, String vastConfigUrl, int duration, int position, String adId) {
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

    public String getAdSystem() {
        return adSystem;
    }

    public String getAdUrl() {
        return adUrl;
    }

    public String getVastConfigUrl() {
        return vastConfigUrl;
    }

    public int getDuration() {
        return duration;
    }

    public int getPosition() {
        return position;
    }

    public String getAdId() {
        return adId;
    }

    public AdType getAdType() {
        return adType;
    }

    public boolean isInfillionAd() {
        return adType == AdType.TRUEX || adType == AdType.IDVX;
    }

    public boolean isRegularAd() {
        return adType == AdType.REGULAR;
    }
}