package com.anda.launcher.feature.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.anda.launcher.core.model.FuzzyMatcher
import com.anda.launcher.core.model.SearchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ContactSearchProvider(private val context: Context) : SearchProvider {
    override val id = "contacts"
    override val slow = true

    override fun isEnabled(settings: SearchSettings) = settings.contacts

    override suspend fun search(request: SearchRequest): List<SearchResult.Contact> {
        val query = request.query
        if (query.length < 2 || !hasPermission(context)) return emptyList()
        return withContext(Dispatchers.IO) { query(query, withPhones = request.settings.contactActions) }
    }

    private fun query(query: String, withPhones: Boolean): List<SearchResult.Contact> {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        val results = ArrayList<Pair<Long, SearchResult.Contact>>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext() && results.size < MAX_CONTACTS) {
                val name = c.getString(2) ?: continue
                val lookup = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                results += c.getLong(0) to SearchResult.Contact(
                    name = name,
                    lookupUri = lookup,
                    photoUri = c.getString(3)?.let(Uri::parse),
                    score = FuzzyMatcher.score(query, FuzzyMatcher.normalize(name), false).coerceAtLeast(300) - 100,
                )
            }
        }
        if (!withPhones || results.isEmpty()) return results.map { it.second }
        val phones = phoneNumbers(results.map { it.first })
        return results.map { (id, contact) -> contact.copy(phone = phones[id]) }
    }

    /** One query for every listed contact's numbers, rather than one per contact. */
    private fun phoneNumbers(contactIds: List<Long>): Map<Long, String> {
        val rows = ArrayList<PhoneRow>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.IS_SUPER_PRIMARY, Phone.IS_PRIMARY),
            "${Phone.CONTACT_ID} IN (${contactIds.joinToString(",") { "?" }})",
            contactIds.map(Long::toString).toTypedArray(),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                rows += PhoneRow(c.getLong(0), number, superPrimary = c.getInt(2) != 0, primary = c.getInt(3) != 0)
            }
        }
        return primaryNumbers(rows)
    }

    internal data class PhoneRow(val contactId: Long, val number: String, val superPrimary: Boolean, val primary: Boolean)

    companion object {
        private const val MAX_CONTACTS = 10

        /** The number the user marked as default, else the primary one, else the first listed. */
        internal fun primaryNumbers(rows: List<PhoneRow>): Map<Long, String> =
            rows.groupBy { it.contactId }.mapValues { (_, numbers) ->
                (numbers.firstOrNull { it.superPrimary } ?: numbers.firstOrNull { it.primary } ?: numbers.first()).number
            }

        fun hasPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
}
