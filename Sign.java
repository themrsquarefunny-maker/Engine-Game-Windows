package com.mini.engine;

import android.content.Context;
import com.android.apksig.ApkSigner;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;

public class Sign {

    static byte[] len(int n) {
        if (n < 128) return new byte[]{(byte) n};
        if (n < 256) return new byte[]{(byte) 0x81, (byte) n};
        return new byte[]{(byte) 0x82, (byte) (n >> 8), (byte) n};
    }

    static byte[] tlv(int tag, byte[]... parts) {
        ByteArrayOutputStream c = new ByteArrayOutputStream();
        for (byte[] p : parts) c.write(p, 0, p.length);
        byte[] body = c.toByteArray();
        byte[] l = len(body.length);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        o.write(l, 0, l.length);
        o.write(body, 0, body.length);
        return o.toByteArray();
    }

    static byte[] oid(String s) {
        String[] p = s.split("\\.");
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(Integer.parseInt(p[0]) * 40 + Integer.parseInt(p[1]));
        for (int i = 2; i < p.length; i++) {
            long v = Long.parseLong(p[i]);
            byte[] tmp = new byte[10];
            int n = 0;
            do {
                tmp[n++] = (byte) (v & 0x7f);
                v >>= 7;
            } while (v > 0);
            for (int j = n - 1; j >= 0; j--) o.write(tmp[j] | (j > 0 ? 0x80 : 0));
        }
        return tlv(0x06, o.toByteArray());
    }

    static byte[] time(long ms) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(ms);
        int y = c.get(Calendar.YEAR);
        if (y < 2050) {
            String s = String.format(Locale.US, "%02d%02d%02d%02d%02d%02dZ", y % 100,
                    c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
            return tlv(0x17, s.getBytes(StandardCharsets.US_ASCII));
        }
        String s = String.format(Locale.US, "%04d%02d%02d%02d%02d%02dZ", y,
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
        return tlv(0x18, s.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] cert(KeyPair kp) throws Exception {
        byte[] algId = tlv(0x30, oid("1.2.840.113549.1.1.11"), tlv(0x05));
        byte[] name = tlv(0x30, tlv(0x31, tlv(0x30, oid("2.5.4.3"),
                tlv(0x0C, "Game Engine".getBytes(StandardCharsets.UTF_8)))));
        long from = System.currentTimeMillis() - 60000;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(from);
        cal.add(Calendar.YEAR, 30);
        byte[] version = tlv(0xA0, tlv(0x02, new byte[]{2}));
        byte[] serial = tlv(0x02, new BigInteger(63, new SecureRandom()).add(BigInteger.ONE).toByteArray());
        byte[] validity = tlv(0x30, time(from), time(cal.getTimeInMillis()));
        byte[] tbs = tlv(0x30, version, serial, algId, name, validity, name, kp.getPublic().getEncoded());
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bits, 1, sig.length);
        return tlv(0x30, tbs, algId, tlv(0x03, bits));
    }

    static byte[] read(File f) throws Exception {
        try (FileInputStream i = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = i.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    static void write(File f, byte[] d) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(d);
        }
    }

    // Firma el APK con una clave propia del motor (se crea la primera vez y se reutiliza)
    public static void sign(Context c, File in, File out) throws Exception {
        File kf = new File(c.getFilesDir(), "key.pk8");
        File cf = new File(c.getFilesDir(), "cert.der");
        if (!kf.exists() || !cf.exists()) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            write(cf, cert(kp));
            write(kf, kp.getPrivate().getEncoded());
        }
        PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(read(kf)));
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(read(cf)));
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder(
                "CERT", pk, Collections.singletonList(cert)).build();
        if (out.exists()) out.delete();
        new ApkSigner.Builder(Collections.singletonList(sc))
                .setInputApk(in).setOutputApk(out)
                .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true)
                .build().sign();
    }
}
