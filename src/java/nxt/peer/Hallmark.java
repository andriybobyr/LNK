package nxt.peer;

import nxt.Nxt;
import nxt.crypto.Crypto;
import nxt.util.Convert;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;

public final class Hallmark {

    public static int parseDate(String dateValue) {
        return Integer.parseInt(dateValue.substring(0, 4)) * 10000
                + Integer.parseInt(dateValue.substring(5, 7)) * 100
                + Integer.parseInt(dateValue.substring(8, 10));
    }

    public static String formatDate(int date) {
        int year = date / 10000;
        int month = (date % 10000) / 100;
        int day = date % 100;
        return (year < 10 ? "000" : (year < 100 ? "00" : (year < 1000 ? "0" : ""))) + year + "-" + (month < 10 ? "0" : "") + month + "-" + (day < 10 ? "0" : "") + day;
    }

    public static String generateHallmark(String secretPhrase, String host, int weight, int date) {

        try {

            if (host.length() == 0 || host.length() > 100) {
                throw new IllegalArgumentException("Hostname length should be between 1 and 100");
            }
            if (weight <= 0 || weight > Nxt.MAX_BALANCE) {
                throw new IllegalArgumentException("Weight should be between 1 and " + Nxt.MAX_BALANCE);
            }

            byte[] publicKey = Crypto.getPublicKey(secretPhrase);
            byte[] hostBytes = host.getBytes("UTF-8");

            ByteBuffer buffer = ByteBuffer.allocate(32 + 2 + hostBytes.length + 4 + 4 + 1);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(publicKey);
            buffer.putShort((short)hostBytes.length);
            buffer.put(hostBytes);
            buffer.putInt(weight);
            buffer.putInt(date);

            byte[] data = buffer.array();
            byte[] signature;
            do {
                data[data.length - 1] = (byte) ThreadLocalRandom.current().nextInt();
                signature = Crypto.sign(data, secretPhrase);
            } while (!Crypto.verify(signature, data, publicKey));

            return Convert.toHexString(data) + Convert.toHexString(signature);

        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static Hallmark parseHallmark(String hallmarkString) {

        try {

            byte[] hallmarkBytes = Convert.parseHexString(hallmarkString);

            ByteBuffer buffer = ByteBuffer.wrap(hallmarkBytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            byte[] publicKey = new byte[32];
            buffer.get(publicKey);
            int hostLength = buffer.getShort();
            if (hostLength > 300) {
                throw new IllegalArgumentException("Invalid host length");
            }
            byte[] hostBytes = new byte[hostLength];
            buffer.get(hostBytes);
            String host = new String(hostBytes, "UTF-8");
            int weight = buffer.getInt();
            int date = buffer.getInt();
            buffer.get();
            byte[] signature = new byte[64];
            buffer.get(signature);

            byte[] data = new byte[hallmarkBytes.length - 64];
            System.arraycopy(hallmarkBytes, 0, data, 0, data.length);

            boolean isValid = host.length() < 100 && weight > 0 && weight <= Nxt.MAX_BALANCE && Crypto.verify(signature, data, publicKey);

            return new Hallmark(publicKey, signature, host, weight, date, isValid);

        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private final String host;
    private final int weight;
    private final int date;
    private final byte[] publicKey;
    private final byte[] signature;
    private final boolean isValid;

    private Hallmark(byte[] publicKey, byte[] signature, String host, int weight, int date, boolean isValid) {
        this.host = host;
        this.publicKey = publicKey;
        this.signature = signature;
        this.weight = weight;
        this.date = date;
        this.isValid = isValid;
    }

    public String getHost() {
        return host;
    }

    public int getWeight() {
        return weight;
    }

    public int getDate() {
        return date;
    }

    public byte[] getSignature() {
        return signature;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public boolean isValid() {
        return isValid;
    }

}
