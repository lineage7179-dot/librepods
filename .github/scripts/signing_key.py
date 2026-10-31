"""Creates the APK signing keystore for the custom LibrePods builds.

With the CUSTOM_SIGNING_PASSPHRASE secret (env PASSPHRASE) the EC P-256 key and its
self-signed certificate are derived from the passphrase, so every build gets the same
signing identity and new builds install over old ones. Nothing is stored or published:
the key only exists on the runner for the build. Without the secret a random throwaway
key is used.

Writes android/custom.keystore and android/local.properties, and reports the mode and
the certificate's SHA-256 fingerprint (public information) for the build summary.
"""

import datetime
import hashlib
import os
import secrets

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

ALIAS = "librepods-custom"
# Order of the P-256 group.
P256_ORDER = int("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)

passphrase = os.environ.get("PASSPHRASE", "")
if passphrase:
    seed = hashlib.scrypt(
        passphrase.encode("utf-8"),
        salt=b"librepods-custom-signing-v1",
        n=2**17, r=8, p=1, maxmem=512 * 1024 * 1024, dklen=48,
    )
    private_key = ec.derive_private_key(int.from_bytes(seed, "big") % (P256_ORDER - 1) + 1, ec.SECP256R1())
    mode = "derived"
    common_name = "LibrePods custom build"
else:
    private_key = ec.generate_private_key(ec.SECP256R1())
    mode = "throwaway"
    common_name = "LibrePods custom build (throwaway)"

name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, common_name)])
certificate = (
    x509.CertificateBuilder()
    .subject_name(name)
    .issuer_name(name)
    .public_key(private_key.public_key())
    .serial_number(1)
    .not_valid_before(datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc))
    .not_valid_after(datetime.datetime(2076, 1, 1, tzinfo=datetime.timezone.utc))
    # Deterministic (RFC 6979) so the certificate bytes are identical on every build.
    .sign(private_key, hashes.SHA256(), ecdsa_deterministic=True)
)

store_password = secrets.token_hex(16)
keystore = pkcs12.serialize_key_and_certificates(
    ALIAS.encode(), private_key, certificate, None,
    serialization.BestAvailableEncryption(store_password.encode()),
)
with open("android/custom.keystore", "wb") as f:
    f.write(keystore)
with open("android/local.properties", "w") as f:
    f.write(
        "RELEASE_STORE_FILE=../custom.keystore\n"
        f"RELEASE_STORE_PASSWORD={store_password}\n"
        f"RELEASE_KEY_ALIAS={ALIAS}\n"
        f"RELEASE_KEY_PASSWORD={store_password}\n"
    )

fingerprint = hashlib.sha256(certificate.public_bytes(serialization.Encoding.DER)).hexdigest()
print(f"Signing mode: {mode}, certificate SHA-256: {fingerprint}")
if "GITHUB_OUTPUT" in os.environ:
    with open(os.environ["GITHUB_OUTPUT"], "a") as f:
        f.write(f"mode={mode}\nfingerprint={fingerprint}\n")
