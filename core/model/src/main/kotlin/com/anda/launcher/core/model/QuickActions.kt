package com.anda.launcher.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The built-in quick actions read from a query, in their default chip order. */
enum class QuickActionKind {
    Call,
    Message,
    Email,
    OpenUrl,
    SetAlarm,
    SetTimer,
    SaveContact,
    AddEvent,
    ;

    companion object {
        /** [saved] first (the user's order), then any kind it doesn't list, in default order. */
        fun ordered(saved: List<QuickActionKind>): List<QuickActionKind> = (saved + entries).distinct()
    }
}

/**
 * A list of [QuickActionKind]s stored by name. A name this version doesn't know (from a newer
 * one) is skipped instead of failing the whole settings file.
 */
internal object QuickActionKindsSerializer : KSerializer<List<QuickActionKind>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<QuickActionKind>) = delegate.serialize(encoder, value.map { it.name })

    override fun deserialize(decoder: Decoder): List<QuickActionKind> =
        delegate.deserialize(decoder).mapNotNull { name -> QuickActionKind.entries.firstOrNull { it.name == name } }.distinct()
}

/**
 * A user-added chip that hands the query to another app. With [intentAction] null it is an app
 * search: `ACTION_SEARCH` with the query, sent to [packageName]. Otherwise it is a custom intent:
 * [intentAction] with the query in [data] (where `%s` is) and/or in the [extraKey] extra.
 * Either way it only ever starts an activity; Anda sends no broadcasts and starts no services.
 */
@Serializable
data class CustomAction(
    val label: String,
    val packageName: String? = null,
    val intentAction: String? = null,
    /** A URI template; `%s` is replaced by the encoded query. */
    val data: String? = null,
    /** A string extra that receives the query as typed. */
    val extraKey: String? = null,
    val mimeType: String? = null,
) {
    val isAppSearch: Boolean get() = intentAction == null

    /** [data] with the query filled in, or null without a data template. */
    fun dataFor(query: String): String? =
        data?.replace(PLACEHOLDER, java.net.URLEncoder.encode(query, Charsets.UTF_8).replace("+", "%20"))

    val isValid: Boolean
        get() {
            if (label.isBlank() || label.length > MAX_LABEL) return false
            if (packageName != null && !packageName.matches(packagePattern)) return false
            val action = intentAction ?: return packageName != null && data == null && extraKey == null && mimeType == null
            return action.matches(namePattern) &&
                (data == null || isValidDataTemplate(data)) &&
                (extraKey == null || extraKey.matches(namePattern)) &&
                (mimeType == null || mimeType.matches(mimePattern)) &&
                // The query has to go somewhere.
                (data?.contains(PLACEHOLDER) == true || extraKey != null)
        }

    companion object {
        const val PLACEHOLDER = "%s"
        const val MAX_ACTIONS = 10
        const val MAX_LABEL = 40

        private val namePattern = Regex("""[A-Za-z][A-Za-z0-9_.]{0,199}""")
        private val packagePattern = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")
        private val mimePattern = Regex("""[a-z0-9.+-]+/[a-z0-9.+*-]+""")
        private val schemePattern = Regex("""^[a-z][a-z0-9+.-]*:.+""")

        /** A URI with a scheme, no spaces, and a sane length; `javascript:` and `file:` are refused. */
        fun isValidDataTemplate(template: String): Boolean =
            schemePattern.matches(template) && template.none(Char::isWhitespace) && template.length <= 500 &&
                !template.startsWith("javascript:") && !template.startsWith("file:")
    }
}
