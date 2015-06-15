package com.cleo.lexicom.certmgr;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.KeyPair;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

import com.cleo.lexicom.certmgr.external.CertificateHandlerException;
import com.sshtools.j2ssh.transport.publickey.SshPublicKey;
import com.sshtools.j2ssh.transport.publickey.SshPublicKeyFile;
import com.sshtools.j2ssh.transport.publickey.dsa.SshDssPublicKey;
import com.sshtools.j2ssh.transport.publickey.rsa.SshRsaPublicKey;

public class ImportedCertificate {

    private X509Certificate x509 = null;

    public X509Certificate getX509 () {
        return x509;
    }

    public ImportedCertificate(String alias, String sshkeyfn) throws Exception {
        alias = alias.toUpperCase();
        File sshkeyf  = new File(sshkeyfn);
        SshPublicKeyFile sshPublicKeyFile = SshPublicKeyFile.parse(sshkeyf);
        x509 = generate(alias, sshPublicKeyFile, sshkeyf.lastModified());
    }

    public ImportedCertificate(String alias, byte[] sshkey) throws Exception {
        alias = alias.toUpperCase();
        SshPublicKeyFile sshPublicKeyFile = SshPublicKeyFile.parse(sshkey);
        x509 = generate(alias, sshPublicKeyFile, new Date().getTime());
    }

    private static X509Certificate generate(String alias, SshPublicKeyFile sshPublicKeyFile, long creationTime) throws Exception {
        // Get the generic format and public key from the SshPublicKeyFile.
        //SshPublicKeyFormat sshFormat = sshPublicKeyFile.getFormat();
        SshPublicKey sshPublicKey = sshPublicKeyFile.toPublicKey();

        // Retain the key data to save within the CA cert
        //report("Imported publicKeyStr:" + F.hex(sshPublicKeyFile.getBytes()));

        // For either format, the comment should include an email address
        //String comment = sshPublicKeyFile.getComment();
        //jTextFieldEmail.setText(comment);

        // For SECSH format, an additional username is in the subject header
        //if (sshFormat instanceof SECSHPublicKeyFormat) {
            //SECSHPublicKeyFormat secshFormat = (SECSHPublicKeyFormat)sshFormat;
            // String alias = secshFormat.getHeaderValue("Subject");
            //jTextFieldUser.setText(alias);
        //}

        // Set the key ID
        int keyID = sshPublicKey.hashCode();
        int bitStrength = sshPublicKey.getBitLength();

        // Generate a new J2Ssh?saPrivateKey using the public key parameters
        // Then create a generic keypair using the ?saPrivate and ?saPublic key portions
        KeyPair keyPair;
        if ( sshPublicKey instanceof SshRsaPublicKey ) {
            J2SshRsaPrivateKey rsaPrivateKey = new J2SshRsaPrivateKey((SshRsaPublicKey)sshPublicKey);
            keyPair = new KeyPair(rsaPrivateKey.pubKey, rsaPrivateKey.prvKey);
        } else if ( sshPublicKey instanceof SshDssPublicKey ) {
            J2SshDssPrivateKey dsaPrivateKey = new J2SshDssPrivateKey((SshDssPublicKey)sshPublicKey);
            keyPair = new KeyPair(dsaPrivateKey.pubKey, dsaPrivateKey.prvKey);
        } else {
            throw new CertificateHandlerException( "Error: Unsupported public key format '" +
                                                   sshPublicKey.getAlgorithmName() + "' class:" + sshPublicKey.getClass().getName());
        }

        // Now make a cert from the keyPair
        MyCertificate cert = new MyCertificate();
        cert.SUBJECT_ALIAS = alias;
        // To create the signature key
        cert.PASSWORD = alias.toCharArray();
        cert.SUBJECT_NAME = alias;
        cert.validFor = 96; // 96 month default

        // Assign the keypair data
        cert.keyPair = keyPair;
        cert.STRENGTH = bitStrength;
        cert.signatureAlgorithm = "SHA-1";
        cert.creationTime = creationTime;
        cert.expirationTime = 0; // for SSH, set to something for PGP?
        cert.keyID = keyID;
        cert.KEY_DATA = new String(sshPublicKeyFile.getBytes());

        // make it into a certificate
        CSRGenerator csr = new CSRGenerator();
        csr.generateCert(cert);
        ByteArrayInputStream inStream = new ByteArrayInputStream(csr.getSelfSignedCertificate());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate x509 = (X509Certificate)cf.generateCertificate(inStream);
        inStream.close();
        return x509;
    }

}
