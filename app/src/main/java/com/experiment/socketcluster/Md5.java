package com.experiment.socketcluster;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Md5 {

    public interface Md5SecureObject {
        String toStringForHash();

        String getMd5Hash();
        void setMd5Hash(String md5);
    }

    /**
     * Hashes string using MD5
     *
     * @param inputData as String
     * @return
     */
    public static String encrypt(String inputData) {
        MessageDigest md = null;

        try {
            md = MessageDigest.getInstance("MD5");
            md.update(inputData.getBytes());

            byte messageDigest[] = md.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (int i=0; i<messageDigest.length; i++)
                hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean isChecksumValid(Md5SecureObject object) {
        String dbHash = object.getMd5Hash();
        String newHash = Md5.encrypt(object.toStringForHash());

        return dbHash != null && newHash != null && dbHash.equals(newHash);
    }
}
