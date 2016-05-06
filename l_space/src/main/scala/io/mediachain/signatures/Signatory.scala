package io.mediachain.signatures

import java.security.PrivateKey

case class Signatory(commonName: String, privateKey: PrivateKey)
