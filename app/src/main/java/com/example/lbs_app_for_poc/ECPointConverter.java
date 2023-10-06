package com.example.lbs_app_for_poc;
import java.math.BigInteger;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
// import org.bouncycastle.math.ec.ECPoint.Fp;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECFieldElement.Fp;

public class ECPointConverter {

    public static org.bouncycastle.math.ec.ECPoint getBouncyCastleECPointFromJavaSecurityPublic(java.security.spec.ECPoint javaEcPoint, java.security.interfaces.ECPublicKey ecPublicKey) {
        try {
            org.bouncycastle.math.ec.ECCurve Curve = getECCurveFromPublicKey(ecPublicKey);
            // byte[] pointBytes = convertJavaSecurityECPoitToByteArray(javaEcPoint);
            // return convertByteArrayToBouncyCastle(pointBytes, Curve);
            return null;
        }
        catch (Exception e){
            throw e;
        }
    }

    public static org.bouncycastle.math.ec.ECPoint getBouncyCastleECPointFromJavaSecurityPrivate(java.security.spec.ECPoint javaEcPoint, java.security.interfaces.ECPrivateKey ecPrivateKey) {
        try {
            org.bouncycastle.math.ec.ECCurve Curve = getECCurveFromPrivateKey(ecPrivateKey);
            byte[] pointBytes = convertJavaSecurityECPoitToByteArray(javaEcPoint);
            return convertByteArrayToBouncyCastle(pointBytes, Curve);
        }
        catch (Exception e){
            throw e;
        }
    }

    public static org.bouncycastle.math.ec.ECPoint convertByteArrayToBouncyCastle(byte[] byteArray, org.bouncycastle.math.ec.ECCurve curve) {
        int length = byteArray.length / 2;
        byte[] xBytes = new byte[length];
        byte[] yBytes = new byte[length];
        // Split the byte array into X and Y coordinates
        System.arraycopy(byteArray, 0, xBytes, 0, length);
        System.arraycopy(byteArray, length, yBytes, 0, length);
        // Create BigIntegers from the byte arrays
        BigInteger x = new BigInteger(1, xBytes);
        BigInteger y = new BigInteger(1, yBytes);
        // Create ECFieldElements from the BigIntegers
        org.bouncycastle.math.ec.ECFieldElement xFieldElement = curve.fromBigInteger(x); // new Fp(curve.getField().getCharacteristic(), x);
        org.bouncycastle.math.ec.ECFieldElement yFieldElement = curve.fromBigInteger(y); // new Fp(curve.getField().getCharacteristic(), y);
        return curve.createPoint(xFieldElement.toBigInteger(), yFieldElement.toBigInteger());
    }

    public static org.bouncycastle.math.ec.ECCurve getECCurveFromPrivateKey(java.security.interfaces.ECPrivateKey privateKey) {
        return null;
        // return EC5Util.convertCurve(privateKey.getParams().getCurve());
        /*java.security.spec.ECParameterSpec ecSpec = privateKey.getParams();
        String curveName = ecSpec.getCurve().toString();
        org.bouncycastle.jce.spec.ECNamedCurveParameterSpec namedCurveSpec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec(curveName);
        return namedCurveSpec.getCurve();*/
    }

    public static ECCurve extractCurve(ECParameterSpec ecSpec) {
        EllipticCurve ellipticCurve = ecSpec.getCurve();
        return convertToBouncyCastleCurve(ellipticCurve);
    }

    public static ECCurve convertToBouncyCastleCurve(EllipticCurve ellipticCurve) {
        BigInteger p = ellipticCurve.getA();
        BigInteger a = ellipticCurve.getA();
        BigInteger b = ellipticCurve.getB();
        ECCurve.Fp curve = new ECCurve.Fp(p, a, b);
        return curve;
    }

    public static org.bouncycastle.math.ec.ECCurve getECCurveFromPublicKey(java.security.interfaces.ECPublicKey publicKey) {
        ECParameterSpec ecSpec = publicKey.getParams();
        ECCurve curve = extractCurve(ecSpec);
        return curve;
        //        ECParameterSpec ecSpec = publicKey.getParams();
//        ECCurve curve = extractCurve(ecSpec);
//        return curve;

        /*java.security.spec.ECParameterSpec ecSpec = publicKey.getParams();
        org.bouncycastle.jce.spec.ECNamedCurveParameterSpec namedCurveSpec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec(ecSpec.getCurve().toString());
        return namedCurveSpec.getCurve();*/

        // return EC5Util.convertCurve(publicKey.getParams().getCurve());

        /*java.security.spec.ECParameterSpec ecSpec = publicKey.getParams();
        java.security.spec.ECPoint ecPoint = publicKey.getW();
        org.bouncycastle.jce.spec.ECNamedCurveSpec namedCurveSpec = new org.bouncycastle.jce.spec.ECNamedCurveSpec(ecSpec.getCurve().toString(), ecSpec.getCurve(), ecSpec.getGenerator(), ecSpec.getOrder(), ecSpec.getCofactor());
        return ECNamedCurveTable.getParameterSpec(namedCurveSpec.getName()).getCurve();*/
        /*java.security.spec.ECPoint point = org.bouncycastle.jce.ECPointUtil.decodePoint(
                (EllipticCurve)(publicKey.getParams().getCurve()),
                convertJavaSecurityECPoitToByteArray(publicKey.getW())
        );*/
        /*java.security.spec.ECParameterSpec ecSpec = publicKey.getParams();
        String curveName = ecSpec.getCurve().toString();
        org.bouncycastle.jce.spec.ECNamedCurveParameterSpec namedCurveSpec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec(curveName);
        return namedCurveSpec.getCurve();*/
    }

    public static byte[] convertJavaSecurityECPoitToByteArray(java.security.spec.ECPoint javaSecurityPoint) {
        BigInteger x = javaSecurityPoint.getAffineX();
        BigInteger y = javaSecurityPoint.getAffineY();
        byte[] xBytes = x.toByteArray();
        byte[] yBytes = y.toByteArray();
        // Ensure the byte arrays have the same length
        int length = Math.max(xBytes.length, yBytes.length);
        byte[] result = new byte[length * 2];
        // Copy the X and Y bytes into the result array
        System.arraycopy(xBytes, 0, result, length - xBytes.length, xBytes.length);
        System.arraycopy(yBytes, 0, result, length + length - yBytes.length, yBytes.length);
        return result;
    }

    /*
    public ECPoint convertJavaSecurityToBouncyCastle(ECPoint javaSecurityPoint, org.bouncycastle.math.ec.ECParameterSpec ecSpec) {
        org.bouncycastle.math.ec.ECFieldElement x = new org.bouncycastle.math.ec.Fp(ecSpec.getCurve().getField(),
                javaSecurityPoint.getAffineX()).toBigInteger();
        org.bouncycastle.math.ec.ECFieldElement y = new org.bouncycastle.math.ec.Fp(ecSpec.getCurve().getField(),
                javaSecurityPoint.getAffineY()).toBigInteger();
        return ecSpec.getCurve().createPoint(x.toBigInteger(), y.toBigInteger());
    }
    */

}

