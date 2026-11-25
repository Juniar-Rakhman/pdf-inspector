package com.interlunium.pdfinspector;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * Creates a genuinely signed PDF for integration testing purposes.
 *
 * <p>The signer certificate is self-signed and generated at runtime, so the
 * resulting PDF is a real PAdES/CAdES-style signature (adbe.pkcs7.detached)
 * that the whole inspection pipeline can be exercised against.</p>
 */
public final class SignedPdfFixture {

    private static final String SIGNER_NAME = "PDF Inspector Test Signer";

    private SignedPdfFixture() {
    }

    /**
     * @return the bytes of a PDF containing one digital signature
     * @throws Exception on any failure
     */
    public static byte[] create() throws Exception {
        ensureProvider();
        KeyPair keyPair = generateKeyPair();
        X509Certificate certificate = selfSignedCertificate(keyPair);
        byte[] unsigned = createUnsignedPdf();
        return signIncrementally(unsigned, keyPair, certificate);
    }

    /**
     * @return the name of the test signer
     */
    public static String signerName() {
        return SIGNER_NAME;
    }

    private static void ensureProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair) throws Exception {
        X500Name subject = new X500Name("CN=" + SIGNER_NAME + ", O=Interlunium, C=DE");
        long now = System.currentTimeMillis();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                new Date(now - 86_400_000L),
                new Date(now + 365L * 86_400_000L),
                subject,
                keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyCertSign | KeyUsage.cRLSign));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    private static byte[] createUnsignedPdf() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 18);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("PDF Signature Inspector test document");
                contentStream.endText();
            }
            document.save(out);
        }
        return out.toByteArray();
    }

    private static byte[] signIncrementally(byte[] unsigned, KeyPair keyPair, X509Certificate certificate)
            throws Exception {
        ByteArrayOutputStream signedOut = new ByteArrayOutputStream();
        try (PDDocument document = Loader.loadPDF(unsigned)) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(SIGNER_NAME);
            signature.setLocation("Berlin, Germany");
            signature.setReason("Integration test");
            signature.setSignDate(Calendar.getInstance());

            SignatureInterface signatureInterface = content -> {
                try {
                    return createCmsSignature(content, keyPair, certificate);
                } catch (Exception e) {
                    throw new java.io.IOException("Unable to create CMS signature", e);
                }
            };
            document.addSignature(signature, signatureInterface);
            document.saveIncremental(signedOut);
        }
        return signedOut.toByteArray();
    }

    private static byte[] createCmsSignature(InputStream content, KeyPair keyPair, X509Certificate certificate)
            throws Exception {
        byte[] contentBytes;
        try (InputStream copy = content) {
            contentBytes = copy.readAllBytes();
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] messageDigest = digest.digest(contentBytes);

        ASN1EncodableVector attrVector = new ASN1EncodableVector();
        attrVector.add(new Attribute(PKCSObjectIdentifiersConst.contentType,
                new DERSet(CMSObjectIdentifiers.data)));
        attrVector.add(new Attribute(PKCSObjectIdentifiersConst.messageDigest,
                new DERSet(new DEROctetString(messageDigest))));
        attrVector.add(new Attribute(PKCSObjectIdentifiersConst.signingTime,
                new DERSet(new Time(new Date()))));

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = new org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(certificate);

        SignerInfoGenerator signerInfoGenerator = new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build())
                .setSignedAttributeGenerator(new org.bouncycastle.cms.DefaultSignedAttributeTableGenerator(
                        new AttributeTable(attrVector)))
                .build(contentSigner, holder);

        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(signerInfoGenerator);
        generator.addCertificates(new JcaCertStore(Collections.singletonList(certificate)));

        CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(contentBytes), false);
        return signedData.getEncoded();
    }

    /**
     * Small constants holder to avoid a long static initializer in the method body.
     */
    private static final class PKCSObjectIdentifiersConst {
        private static final ASN1ObjectIdentifier contentType = new ASN1ObjectIdentifier("1.2.840.113549.1.9.3");
        private static final ASN1ObjectIdentifier messageDigest = new ASN1ObjectIdentifier("1.2.840.113549.1.9.4");
        private static final ASN1ObjectIdentifier signingTime = new ASN1ObjectIdentifier("1.2.840.113549.1.9.5");
    }
}
