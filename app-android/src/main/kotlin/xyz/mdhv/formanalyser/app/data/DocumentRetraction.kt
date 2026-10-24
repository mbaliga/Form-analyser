package xyz.mdhv.formanalyser.app.data

import xyz.mdhv.formanalyser.app.vault.Vault

/**
 * Retract, restore and (narrowly) erase vault documents.
 *
 * The rule of this codebase is that history is never silently deleted. A document is not history in
 * the sense that matters to the rule — it feeds no trend, readiness figure or personal best — but it
 * is a medical record the athlete brought in, and the single tap that used to delete the row and its
 * ciphertext could not be undone. So the tap now only [retract]s: the row leaves every list and
 * every export, while the encrypted file stays untouched in the vault and [restore] brings it back.
 *
 * Hard removal exists because a medical file is the one class where the athlete's right to truly
 * remove it outweighs recoverability. It is two steps from the single tap: retract first, then
 * [erasePermanently] from Settings -> Data behind its own confirmation. It is refused for a live
 * document here, and the DAO's `deletedAt IS NOT NULL` guard refuses it again at SQL level.
 */
class DocumentRetraction(private val dao: BodyDao, private val vault: Vault) {

    suspend fun retract(id: String, now: Long = System.currentTimeMillis()) =
        dao.retractDocument(id, now)

    suspend fun restore(id: String) = dao.restoreDocument(id)

    /**
     * Remove the ciphertext and then the row of an already-retracted document. Returns false, and
     * leaves the row, if the document is live, unknown, or its ciphertext could not be removed — the
     * row is never dropped while the file it points at might still exist.
     */
    suspend fun erasePermanently(id: String): Boolean {
        val doc = dao.documentById(id) ?: return false
        if (doc.deletedAt == null) return false
        if (!vault.delete(doc.encPath)) return false
        return dao.eraseRetractedDocument(id) == 1
    }
}
