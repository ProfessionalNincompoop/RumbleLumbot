package slaynash.lum.bot;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class UrlShortener {
    private UrlShortener() {}

    public static String GetShortenedUrl(String baseUrl) {
        String result = "";

        // Check if already exists
        try {
            ResultSet rs = DBConnectionManagerShortUrls.sendRequest("SELECT uid FROM shorturls WHERE url = ?", baseUrl);
            if (rs.next()) {
                result = rs.getString("uid");
                DBConnectionManagerShortUrls.closeRequest(rs);
                return "https://s.slaynash.fr/" + result;
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch shorten url from database");
            e.printStackTrace();
            return "";
        }

        // Generate new unique one
        try {

            boolean exists = true;
            do {
                result = randomAlphaNumeric(8);
                ResultSet rs = DBConnectionManagerShortUrls.sendRequest("SELECT uid FROM shorturls WHERE uid = ?", result);
                exists = rs.next();
                DBConnectionManagerShortUrls.closeRequest(rs);
            }
            while (exists);

            DBConnectionManagerShortUrls.sendUpdate("INSERT INTO shorturls (uid, url) VALUES (?,?)", result, baseUrl);

            return "https://s.slaynash.fr/" + result;

        } catch (SQLException e) {
            System.err.println("Failed to save shorten url in database");
            e.printStackTrace();
            return "";
        }
    }

    private static final String ALPHA_NUMERIC_STRING = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static String randomAlphaNumeric(int count) {
        StringBuilder builder = new StringBuilder(count);
        while (count-- != 0) {
            int character = (int)(Math.random() * ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
}