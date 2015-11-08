package net.atomarea.flowx.utils;

import android.content.Intent;
import android.net.Uri;

import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeoHelper {
    private static Pattern GEO_URI = Pattern.compile("geo:([\\-0-9.]+),([\\-0-9.]+)(?:,([\\-0-9.]+))?(?:\\?(.*))?", Pattern.CASE_INSENSITIVE);

    public static boolean isGeoUri(String body) {
        return body != null && GEO_URI.matcher(body).matches();
    }

    public static ArrayList<Intent> createGeoIntentsFromMessage(Message message) {
        final ArrayList<Intent> intents = new ArrayList<>();
        Matcher matcher = GEO_URI.matcher(message.getBody());
        if (!matcher.matches()) {
            return intents;
        }
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(matcher.group(1));
            if (latitude > 90.0 || latitude < -90.0) {
                return intents;
            }
            longitude = Double.parseDouble(matcher.group(2));
            if (longitude > 180.0 || longitude < -180.0) {
                return intents;
            }
        } catch (NumberFormatException nfe) {
            return intents;
        }
        final Conversation conversation = message.getConversation();
        String label;
        if (conversation.getMode() == Conversation.MODE_SINGLE && message.getStatus() == Message.STATUS_RECEIVED) {
            try {
                label = "(" + URLEncoder.encode(message.getConversation().getName(), "UTF-8") + ")";
            } catch (UnsupportedEncodingException e) {
                label = "";
            }
        } else {
            label = "";
        }

        Intent locationPluginIntent = new Intent("net.atomarea.flowx.location.show");
        locationPluginIntent.putExtra("latitude", latitude);
        locationPluginIntent.putExtra("longitude", longitude);
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (message.getStatus() == Message.STATUS_RECEIVED) {
                locationPluginIntent.putExtra("name", conversation.getName());
                locationPluginIntent.putExtra("jid", message.getCounterpart().toString());
            } else {
                locationPluginIntent.putExtra("jid", conversation.getAccount().getJid().toString());
            }
        }
        intents.add(locationPluginIntent);

        Intent geoIntent = new Intent(Intent.ACTION_VIEW);
        geoIntent.setData(Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude) + "?q=" + String.valueOf(latitude) + "," + String.valueOf(longitude) + label));
        intents.add(geoIntent);

        Intent httpIntent = new Intent(Intent.ACTION_VIEW);
        httpIntent.setData(Uri.parse("https://maps.google.com/maps?q=loc:" + String.valueOf(latitude) + "," + String.valueOf(longitude) + label));
        intents.add(httpIntent);
        return intents;
    }
}
