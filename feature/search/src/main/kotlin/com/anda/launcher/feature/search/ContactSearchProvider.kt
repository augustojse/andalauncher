package com.anda.launcher.feature.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
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
        return withContext(Dispatchers.IO) { query(query) }
    }

    private fun query(query: String): List<SearchResult.Contact> {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        val results = ArrayList<SearchResult.Contact>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext() && results.size < MAX_CONTACTS) {
                val name = c.getString(2) ?: continue
                val lookup = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                results += SearchResult.Contact(
                    name = name,
                    lookupUri = lookup,
                    photoUri = c.getString(3)?.let(Uri::parse),
                    score = FuzzyMatcher.score(query, FuzzyMatcher.normalize(name), false).coerceAtLeast(300) - 100,
                )
            }
        }
        return results
    }

    companion object {
        private const val MAX_CONTACTS = 5

        fun hasPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
}
