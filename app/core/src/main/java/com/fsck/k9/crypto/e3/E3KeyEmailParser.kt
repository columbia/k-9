package com.fsck.k9.crypto.e3

import com.fsck.k9.crypto.KeyFormattingUtils
import com.fsck.k9.mail.internet.MimeMessage

class E3KeyEmailParser {

    /**
     * At the moment, the returned [E3KeyEmail] only contains the public keys in the MIME
     * headers and not the attached public key file.
     */
    fun parseKeyEmail(message: MimeMessage): E3KeyEmail {
        return E3KeyEmail(
                publicKeys = getE3PublicKeys(message),
                deletedPublicKeyIds = getDeletedE3PublicKeyIds(message),
                headersToSign = buildConcatenatedE3Headers(message),
                headersSignature = getE3SignatureHeader(message)
        )
    }

    private fun getE3SignatureHeader(message: MimeMessage): ByteArray? {
        return when (message.headerNames.contains(E3Constants.MIME_E3_SIGNATURE)) {
            true -> {
                val base64EncodedSignature = message.getHeader(E3Constants.MIME_E3_SIGNATURE)[0].toByteArray(Charsets.UTF_8)

                KeyFormattingUtils.unfoldBase64KeyData(base64EncodedSignature)
            }
            false -> {
                null
            }
        }
    }

    private fun buildConcatenatedE3Headers(message: MimeMessage): String {
        val sortedE3HeaderNames = message.headerNames.filter { name ->
            val upperCaseName = name.toUpperCase()
            upperCaseName.startsWith(E3Constants.MIME_E3_PREFIX)
                    .and(upperCaseName != E3Constants.MIME_E3_SIGNATURE)
        }.toSortedSet()
        val concatHeadersBuilder = StringBuilder()

        for (header in sortedE3HeaderNames) {
            val values = message.getHeader(header)

            for (value in values) {
                concatHeadersBuilder.append(value)
            }
        }

        return concatHeadersBuilder.toString()
    }

    private fun getE3PublicKeys(message: MimeMessage): Set<ByteArray> {
        return getAllDecodedHeadersUnfolded(message, E3Constants.MIME_E3_KEYS)
    }

    private fun getDeletedE3PublicKeyIds(message: MimeMessage): Set<Long> {
        return getAllHeaders(message, E3Constants.MIME_E3_DELETE).mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun getAllDecodedHeadersUnfolded(message: MimeMessage, headerName: String): Set<ByteArray> {
        return when (message.headerNames.contains(headerName)) {
            true -> {
                val keySet = mutableSetOf<ByteArray>()

                for (header in message.getHeader(headerName)) {
                    val unfoldedDecodedKeydata = KeyFormattingUtils.unfoldBase64KeyData(header.toByteArray(Charsets.UTF_8))
                    keySet.add(unfoldedDecodedKeydata)
                }

                keySet
            }
            false -> {
                emptySet()
            }
        }
    }

    private fun getAllHeaders(message: MimeMessage, headerName: String): Set<String> {
        return when (message.headerNames.contains(headerName)) {
            true -> {
                val headersSet = mutableSetOf<String>()

                for (header in message.getHeader(headerName)) {
                    headersSet.add(header)
                }

                headersSet
            }
            false -> {
                emptySet()
            }
        }
    }
}

data class E3KeyEmail(
        val publicKeys: Set<ByteArray>,
        val deletedPublicKeyIds: Set<Long>,
        val headersToSign: String,
        val headersSignature: ByteArray?) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as E3KeyEmail

        if (publicKeys != other.publicKeys) return false
        if (deletedPublicKeyIds != other.deletedPublicKeyIds) return false
        if (headersToSign != other.headersToSign) return false
        if (headersSignature != null) {
            if (other.headersSignature == null) return false
            if (!headersSignature.contentEquals(other.headersSignature)) return false
        } else if (other.headersSignature != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKeys.hashCode()
        result = 31 * result + deletedPublicKeyIds.hashCode()
        result = 31 * result + headersToSign.hashCode()
        result = 31 * result + (headersSignature?.contentHashCode() ?: 0)
        return result
    }
}