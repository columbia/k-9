package com.fsck.k9.crypto.e3

import com.fsck.k9.mail.internet.MimeMessage

class E3KeyEmailParser {

    fun parseKeyEmail(message: MimeMessage): E3KeyEmail {
        return E3KeyEmail(
                publicKeys = getE3PublicKeys(message),
                headersToSign = buildConcatenatedE3Headers(message),
                headersSignature = getE3SignatureHeader(message)
        )
    }

    private fun getE3SignatureHeader(message: MimeMessage): ByteArray? {
        return when (message.headerNames.contains(E3Constants.MIME_E3_SIGNATURE)) {
            true -> {
                message.getHeader(E3Constants.MIME_E3_SIGNATURE)[0].toByteArray(Charsets.UTF_8)
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
        return when (message.headerNames.contains(E3Constants.MIME_E3_KEYS)) {
            true -> {
                val keySet = mutableSetOf<ByteArray>()

                for (header in message.getHeader(E3Constants.MIME_E3_KEYS)) {
                    keySet.add(header.toByteArray(Charsets.UTF_8))
                }

                keySet
            }
            false -> {
                setOf()
            }
        }
    }
}

data class E3KeyEmail(
    val publicKeys: Set<ByteArray>,
    val headersToSign: String,
    val headersSignature: ByteArray?) {

}