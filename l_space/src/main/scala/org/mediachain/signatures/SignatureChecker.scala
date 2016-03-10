package org.mediachain.signatures

import com.jsuereth.pgp.PublicKeyLike

class SignatureChecker(pubkey: PublicKeyLike) {

  // TODO: return Xor[SigningError, Unit] ?
  def verifySignedString(string: String, signature: String): Boolean = {
    pubkey.verifySignatureString(string, signature)
  }
}
