#!/usr/bin/env python3
"""Public-key-encrypt CI signing material. No plaintext key/password enters artifacts."""
import base64,io,json,os,pathlib,zipfile
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
here=pathlib.Path(__file__).resolve().parent
public=serialization.load_pem_public_key((here/'signing-recipient.pem').read_bytes())
stream=io.BytesIO()
with zipfile.ZipFile(stream,'w',zipfile.ZIP_DEFLATED) as archive:
 archive.writestr('Minecraft-Jogger-Mobile-signing.p12',pathlib.Path(os.environ['JOGGER_KEYSTORE']).read_bytes())
 archive.writestr('password.txt',os.environ['JOGGER_KEYSTORE_PASSWORD']+'\n')
 archive.writestr('README.txt','PRIVATE signing backup for com.wiktor.minecraftjogger.mobile. Alias: jogger. Keep both this key and password for future update-compatible builds. Never upload this backup to a public repository.\n')
key=AESGCM.generate_key(bit_length=256);nonce=os.urandom(12);aad=b'JoggerMobileSigningRecovery1'
wrapped=public.encrypt(key,padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()),algorithm=hashes.SHA256(),label=None))
ciphertext=AESGCM(key).encrypt(nonce,stream.getvalue(),aad)
encode=lambda b:base64.b64encode(b).decode('ascii')
pathlib.Path('out/signing-recovery.enc').write_text(json.dumps({'format':1,'wrapped_key':encode(wrapped),'nonce':encode(nonce),'ciphertext':encode(ciphertext)}))
print('Signing backup encrypted for the private local recipient; no plaintext signing material published.')
