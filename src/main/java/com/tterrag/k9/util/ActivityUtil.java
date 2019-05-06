package com.tterrag.k9.util;

import java.util.Optional;
import java.util.function.Function;

import discord4j.core.object.presence.Activity;
import discord4j.core.object.util.Snowflake;

public class ActivityUtil {
    
    public static String getImageUrl(Snowflake appid, String imagekey) {
        return "https://cdn.discordapp.com/app-assets/" + appid.asString() + "/" + imagekey + ".png";
    }
    
    private static Optional<String> getImageUrl(Activity activity, Function<Activity, Optional<String>> getter) {
        return activity.getApplicationId().flatMap(id -> getter.apply(activity).map(key -> getImageUrl(id, key)));
    }
    
    public static Optional<String> getLargeImageUrl(Activity activity) {
        return getImageUrl(activity, Activity::getLargeImageId);
    }
    
    public static Optional<String> getSmallImageUrl(Activity activity) {
        return getImageUrl(activity, Activity::getSmallImageId);
    }
}
