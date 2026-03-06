package me.nasukhov.intrakill.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListStateTest {
    @Test
    fun listStateDefaultValuesAreSensible() {
        val state = ListState()
        assertEquals(0, state.offset)
        assertEquals(12, state.entriesPerPage)
        assertTrue(state.filteredByTags.isEmpty())
        assertNull(state.searchResult)
        assertFalse(state.isSearching)
    }

    @Test
    fun listStateThrowsWhenEntriesPerPageIsZero() {
        assertFails { ListState(entriesPerPage = 0) }
    }

    @Test
    fun listStateThrowsWhenEntriesPerPageIsNegative() {
        assertFails { ListState(entriesPerPage = -1) }
    }

    @Test
    fun listStateThrowsWhenOffsetIsNegative() {
        assertFails { ListState(offset = -1) }
    }

    @Test
    fun listStateAcceptsZeroOffset() {
        val state = ListState(offset = 0)
        assertEquals(0, state.offset)
    }

    @Test
    fun listStateAcceptsPositiveOffset() {
        val state = ListState(offset = 24)
        assertEquals(24, state.offset)
    }

    @Test
    fun listStateAcceptsPositiveEntriesPerPage() {
        val state = ListState(entriesPerPage = 5)
        assertEquals(5, state.entriesPerPage)
    }

    @Test
    fun listStateWithFilteredByTagsStoresTags() {
        val tags = setOf("nature", "travel")
        val state = ListState(filteredByTags = tags)
        assertEquals(tags, state.filteredByTags)
    }
}

class NewEntryStateTest {
    @Test
    fun newEntryStateDefaultValuesAreEmpty() {
        val state = NewEntryState()
        assertEquals("", state.name)
        assertTrue(state.selectedTags.isEmpty())
        assertTrue(state.knownTags.isEmpty())
        assertTrue(state.attachments.isEmpty())
        assertTrue(state.violations.isEmpty())
        assertFalse(state.isSaving)
    }

    @Test
    fun newEntryStateCopyWithNameUpdatesNameOnly() {
        val state = NewEntryState(name = "original")
        val updated = state.copy(name = "updated")
        assertEquals("updated", updated.name)
        assertTrue(updated.violations.isEmpty())
    }
}

class LoginStateTest {
    @Test
    fun loginStateDefaultValuesAreEmpty() {
        val state = LoginState()
        assertEquals("", state.password)
        assertFalse(state.isLoggingIn)
        assertTrue(state.violations.isEmpty())
    }

    @Test
    fun loginStateCopyWithPasswordPreservesOtherFields() {
        val state = LoginState(password = "newpass")
        assertEquals("newpass", state.password)
        assertFalse(state.isLoggingIn)
    }
}

class EntryStateTest {
    @Test
    fun entryStateInitializesWithCorrectEntryId() {
        val id = "some-entry-id"
        val state = EntryState(entryId = id)
        assertEquals(id, state.entryId)
        assertNull(state.entry)
        assertFalse(state.isEditing)
        assertFalse(state.isLoading)
        assertFalse(state.isSaving)
    }

    @Test
    fun entryStateWithIsLoadingStoresLoadingFlag() {
        val state = EntryState(entryId = "id", isLoading = true)
        assertTrue(state.isLoading)
    }
}
