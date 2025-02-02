package com.icon.score;

import score.Context;
import score.annotation.External;

import java.math.BigInteger;

import java.util.Arrays;

public class Verifier {
    static class ByteUtil {
        public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
        public static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

        public static int firstNonZeroByte(byte[] data) {
            for (int i = 0; i < data.length; ++i) {
                if (data[i] != 0) {
                    return i;
                }
            }
            return -1;
        }

        public static byte[] stripLeadingZeroes(byte[] data) {

            if (data == null)
                return null;

            final int firstNonZero = firstNonZeroByte(data);
            switch (firstNonZero) {
                case -1:
                    return ZERO_BYTE_ARRAY;

                case 0:
                    return data;

                default:
                    byte[] result = new byte[data.length - firstNonZero];
                    System.arraycopy(data, firstNonZero, result, 0, data.length - firstNonZero);

                    return result;
            }
        }

        static byte[] encodeInto64Bytes(byte[] w1, byte[] w2) {
            byte[] res = new byte[64];

            w1 = stripLeadingZeroes(w1);
            w2 = stripLeadingZeroes(w2);

            System.arraycopy(w1, 0, res, 32 - w1.length, w1.length);
            System.arraycopy(w2, 0, res, 64 - w2.length, w2.length);

            return res;
        }

        static byte[] encodeInto32Bytes(byte[] w) {
            byte[] res = new byte[32];
            w = stripLeadingZeroes(w);
            System.arraycopy(w, 0, res, 32 - w.length, w.length);
            return res;
        }


        static byte[] concat(byte[] w1, byte[] w2) {
            byte[] res = new byte[w1.length+w2.length];
            System.arraycopy(w1, 0, res, 0, w1.length);
            System.arraycopy(w2, 0, res, w1.length, w2.length);
            return res;
        }
    }

    static class Pairing {

        static class Scalar {
            public BigInteger v;
            Scalar(BigInteger v) {
                this.v = v;
            }
            public static Scalar decode(byte[] r) {
                Context.require(r.length == 32, "Scalar: a scalar should be 32 bytes");
                return new Scalar(new BigInteger(r));
            }
            public byte[] encode() {
                return ByteUtil.encodeInto32Bytes(v.toByteArray());
            }
        }

        static class G1Point {
            public BigInteger x;
            public BigInteger y;
            G1Point(BigInteger x, BigInteger y) {
                this.x = x;
                this.y = y;
            }
            public static G1Point decode(byte[] r) {
                Context.require(r.length == 64, "G1Point: a G1Point should be 64 bytes");
                return new G1Point(
                        new BigInteger(Arrays.copyOfRange(r, 0, 32)),
                        new BigInteger(Arrays.copyOfRange(r, 32, 64)));
            }
            public byte[] encode() {
                return ByteUtil.encodeInto64Bytes(x.toByteArray(), y.toByteArray());
            }
        }

        static class G2Point {
            public BigInteger xi;
            public BigInteger xr;
            public BigInteger yi;
            public BigInteger yr;
            G2Point(BigInteger xi, BigInteger xr, BigInteger yi, BigInteger yr) {
                this.xi = xi;
                this.xr = xr;
                this.yi = yi;
                this.yr = yr;
            }
            public byte[] encode() {
                byte[] x = ByteUtil.encodeInto64Bytes(xi.toByteArray(), xr.toByteArray());
                byte[] y = ByteUtil.encodeInto64Bytes(yi.toByteArray(), yr.toByteArray());
                return ByteUtil.concat(x, y);
            }
        }

        public G1Point P1() {
            return new G1Point(
                    new BigInteger("1"),
                    new BigInteger("2")
            );
        }

        public static G1Point negate(G1Point p) {
            // The prime q in the base field F_q for G1
            BigInteger q = new BigInteger("21888242871839275222246405745257275088696311157297823662689037894645226208583");
            if (p.x.equals(BigInteger.ZERO) && p.y.equals(BigInteger.ZERO)) {
                return new G1Point(
                        new BigInteger("0"),
                        new BigInteger("0")
                );
            }
            return new G1Point(
                    p.x,
                    q.subtract(p.y.mod(q))
            );
        }

        public G2Point P2() {

            // Original code point
            return new G2Point(
                    new BigInteger("11559732032986387107991004021392285783925812861821192530917403151452391805634"),
                    new BigInteger("10857046999023057135944570762232829481370756359578518086990519993285655852781"),
                    new BigInteger("4082367875863433681332203403145435568316851327593401208105741076214120093531"),
                    new BigInteger("8495653923123431417604973247489272438418190587263600148770280649306958101930")
                );
        }

        public static G1Point addition(G1Point p1, G1Point p2) {
            byte[] res = Context.bn256("add", ByteUtil.concat(p1.encode(), p2.encode()));
            G1Point p = G1Point.decode(res);
            return p;
        }

        public static G1Point scalar_mul(G1Point p, Scalar s) {
            byte[] res;
            res = Context.bn256("mul", ByteUtil.concat(p.encode(), s.encode()));
            return G1Point.decode(res);
        }

        public static boolean pairing(G1Point[] p1, G2Point[] p2) {
            Context.require(p1.length == p2.length, "Pairing: G1 and G2 points must have same length");
            Context.require(p1.length > 0, "Paring: Must have some points");

            Context.println("Trying Pairing");

            byte[] arg = ByteUtil.concat(p1[0].encode(), p2[0].encode());
            for (int i=1; i<p1.length; i++) {
                byte[] tmp = ByteUtil.concat(p1[i].encode(), p2[i].encode());
                arg = ByteUtil.concat(arg, tmp);
            }

            byte[] res = Context.bn256("pairing", arg);

            return !Scalar.decode(res).v.equals(BigInteger.ZERO);
        }

        public boolean pairingProd2(G1Point a1, G2Point a2, G1Point b1, G2Point b2) {
            G1Point[] p1 = new G1Point[]{a1, b1};
            G2Point[] p2 = new G2Point[]{a2, b2};
            return pairing(p1, p2);
        }

        public boolean pairingProd3(G1Point a1, G2Point a2, G1Point b1, G2Point b2, G1Point c1, G2Point c2) {
            G1Point[] p1 = new G1Point[]{a1, b1, c1};
            G2Point[] p2 = new G2Point[]{a2, b2, c2};
            return pairing(p1, p2);
        }

        public static boolean pairingProd4(G1Point a1, G2Point a2, G1Point b1, G2Point b2, G1Point c1, G2Point c2, G1Point d1, G2Point d2) {
            G1Point[] p1 = new G1Point[]{a1, b1, c1, d1};
            G2Point[] p2 = new G2Point[]{a2, b2, c2, d2};
            return pairing(p1, p2);
        }

    }

    static class VerifyingKey {
        Pairing.G1Point alfa1;
        Pairing.G2Point beta2;
        Pairing.G2Point gamma2;
        Pairing.G2Point delta2;

        Pairing.G1Point[] IC;
    }

    static class Proof {
        Pairing.G1Point A;
        Pairing.G2Point B;
        Pairing.G1Point C;

    }

    public VerifyingKey verifyingKey () {
        VerifyingKey vk = new VerifyingKey();
        vk.alfa1 = new Pairing.G1Point(
                new BigInteger("6244961780046620888039345106890105326735326490660670538171427260567041582118"),
                new BigInteger("9345530074574832515777964177156498988936486542424817298013748219694852051085")
        );

        vk.beta2 = new Pairing.G2Point(
                    new BigInteger("2818280727920019509567344333433040640814847647252965574434688845111015589444"),
                    new BigInteger("2491450868879707184707638923318620824043077264425678122529022119991361101584"),
                    new BigInteger("5029766152948309994503689842780415913659475358303615599223648363828913323263"),
                    new BigInteger("2351008111262281888427337816453804537041498010110846693783231450896493019270")
                );

        vk.gamma2 = new Pairing.G2Point(
                    new BigInteger("11559732032986387107991004021392285783925812861821192530917403151452391805634"),
                    new BigInteger("10857046999023057135944570762232829481370756359578518086990519993285655852781"),
                    new BigInteger("4082367875863433681332203403145435568316851327593401208105741076214120093531"),
                    new BigInteger("8495653923123431417604973247489272438418190587263600148770280649306958101930")
                );

        vk.delta2 = new Pairing.G2Point(
                    new BigInteger("20357583894981042724041147112728663642192389767532790379586807642603211339296"),
                    new BigInteger("16724459956898194420001774771107011251121183644400113420009731464596132523544"),
                    new BigInteger("16237331308709056395953700405451681465556343609310773522173256536156022093405"),
                    new BigInteger("3391378266105643470740467819376339353336154378345751297020149225371500106870")
                );

        vk.IC = new Pairing.G1Point[] {
            
            new Pairing.G1Point( 
                new BigInteger("3318215850506904344917620945683151527416225208972147541837352858560867617664"),
                new BigInteger("11747151111802793062439135468089529579175934281607584242945730191491913853935")
            ),
            
            new Pairing.G1Point( 
                new BigInteger("17917913212281490319834384310170467506722893907782768625202003038096247318861"),
                new BigInteger("11930672168320499446324697817074991233097854569509354085385760632603596119358")
            ),
            
        };

        return vk;
    }

    public int verify(BigInteger[] input, Proof proof) {
        BigInteger snark_scalar_field = new BigInteger("21888242871839275222246405745257275088548364400416034343698204186575808495617");
        VerifyingKey vk = verifyingKey();
        Context.require(input.length + 1 == vk.IC.length, "verifier-bad-input");
        // Compute the linear combination vk_x
        Pairing.G1Point vk_x = new Pairing.G1Point(BigInteger.ZERO,BigInteger.ZERO);
        for (int i=0; i<input.length; i++) {
            Context.require(input[i].compareTo(snark_scalar_field) < 0, "verifier-gte-snark-scalar-field");
            vk_x = Pairing.addition(vk_x, Pairing.scalar_mul(vk.IC[i+1], new Pairing.Scalar(input[0])));
        }
        vk_x = Pairing.addition(vk_x, vk.IC[0]);
        if (!Pairing.pairingProd4(Pairing.negate(proof.A), proof.B, vk.alfa1, vk.beta2, vk_x, vk.gamma2, proof.C, vk.delta2)) {
            return 1;
        }
        return 0;
    }

    @External(readonly = true)
    public boolean verifyProof(BigInteger[] a, BigInteger[][] b, BigInteger[] c, BigInteger[] input) {
        Proof proof = new Proof();
        proof.A = new Pairing.G1Point(a[0], a[1]);
        proof.B = new Pairing.G2Point(b[0][0], b[0][1], b[1][0], b[1][1]);
        proof.C = new Pairing.G1Point(c[0], c[1]);

        // TODO: Verify if copying is necessary
        return verify(input, proof) == 0;
    }
}
