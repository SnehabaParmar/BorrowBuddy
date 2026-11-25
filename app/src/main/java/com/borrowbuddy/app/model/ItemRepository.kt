package com.borrowbuddy.app.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

/**
 * Central repository managing BorrowBuddy Item Listings in Cloud Firestore.
 *
 * DATA ARCHITECTURE & RULES:
 * 1. Single collection: `items/{itemId}` where Firestore document ID strictly equals `CampusItem.id`.
 * 2. Listing photos are stored locally on-device via [com.borrowbuddy.app.util.ItemPhotoHelper].
 *    Firestore `imageUri` remains empty string `""` to eliminate Firebase Storage / Blaze plan billing requirements.
 * 3. UID validation: users may only create, update or delete items matching their authenticated UID.
 * 4. Resilient serialization / deserialization with fallback defaults for all 20 CampusItem fields.
 */
object ItemRepository {

    const val COLLECTION_ITEMS = "items"

    // Safe lazy accessors to prevent crashes during JVM unit testing where Firebase is uninitialized
    private val firestore: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            null
        }

    private val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }

    /**
     * Serializes a [CampusItem] to a complete Map suitable for Cloud Firestore storage.
     * Guaranteed to persist all listing metadata fields without omissions.
     * Note: Only valid remote HTTPS Cloudinary URLs are written; local device paths and content:// URIs are sanitized.
     * Enforces a maximum of 3 images per listing.
     */
    fun campusItemToMap(item: CampusItem): Map<String, Any> {
        // Enforce maximum 3 remote HTTPS image URLs
        val validRemoteUris = item.imageUris
            .filter { it.startsWith("https://", ignoreCase = true) }
            .take(3)

        // Primary URL for compatibility with single-image consumers
        val primaryRemoteUri = when {
            validRemoteUris.isNotEmpty() -> validRemoteUris.first()
            item.imageUri.startsWith("https://", ignoreCase = true) -> item.imageUri
            else -> ""
        }

        // Public IDs corresponding to valid remote URLs (max 3)
        val validPublicIds = item.imagePublicIds.take(validRemoteUris.size)

        return hashMapOf(
            "title" to item.title,
            "description" to item.description,
            "category" to item.category,
            "priceLabel" to item.priceLabel,
            "isFree" to item.isFree,
            "pickupSpot" to item.pickupSpot,
            "ownerName" to item.ownerName,
            "ownerUid" to item.ownerUid,
            "rating" to item.rating,
            "imageUri" to primaryRemoteUri,
            "imageUris" to validRemoteUris,
            "imagePublicIds" to validPublicIds,
            "department" to item.department,
            "condition" to item.condition,
            "maxDuration" to item.maxDuration,
            "securityDeposit" to item.securityDeposit,
            "completedLends" to item.completedLends,
            "ownerCourse" to item.ownerCourse,
            "isAvailable" to item.isAvailable,
            "createdAt" to item.createdAt,
            "updatedAt" to item.updatedAt
        )
    }

    /**
     * Deserializes a raw Map and document ID into a valid [CampusItem].
     * Provides strict defaults for any missing or null fields. Returns null if title is blank.
     * Fully compatible with legacy documents containing only single `imageUri`.
     */
    fun mapToCampusItem(id: String, data: Map<String, Any>?): CampusItem? {
        if (data == null) return null

        val title = (data["title"] as? String)?.trim().orEmpty()
        if (title.isBlank()) return null

        val description = (data["description"] as? String).orEmpty()
        val category = (data["category"] as? String)?.ifBlank { "TECH" } ?: "TECH"
        val priceLabel = (data["priceLabel"] as? String)?.ifBlank { "FREE" } ?: "FREE"
        val isFree = (data["isFree"] as? Boolean) ?: (priceLabel.equals("FREE", ignoreCase = true))
        val pickupSpot = (data["pickupSpot"] as? String)?.ifBlank { "Campus Library" } ?: "Campus Library"
        val ownerName = (data["ownerName"] as? String)?.ifBlank { "Student" } ?: "Student"
        val ownerUid = (data["ownerUid"] as? String).orEmpty()
        val rating = (data["rating"] as? String)?.ifBlank { "★ 5.0" } ?: "★ 5.0"

        // Deserialization & validation of image fields (HTTPS only, max 3)
        val rawImageUri = (data["imageUri"] as? String).orEmpty().trim()
        val sanitizedLegacyUri = if (rawImageUri.startsWith("https://", ignoreCase = true)) rawImageUri else ""

        @Suppress("UNCHECKED_CAST")
        val rawImageUris = (data["imageUris"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.map { it.trim() }
            ?.filter { it.startsWith("https://", ignoreCase = true) }
            ?.take(3)
            .orEmpty()

        @Suppress("UNCHECKED_CAST")
        val rawPublicIds = (data["imagePublicIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.map { it.trim() }
            ?.take(3)
            .orEmpty()

        // Fallback: If imageUris list is empty but legacy imageUri is a valid HTTPS URL, promote it to imageUris
        val finalImageUris = if (rawImageUris.isNotEmpty()) {
            rawImageUris
        } else if (sanitizedLegacyUri.isNotEmpty()) {
            listOf(sanitizedLegacyUri)
        } else {
            emptyList()
        }

        val finalPrimaryImageUri = finalImageUris.firstOrNull() ?: sanitizedLegacyUri

        val department = (data["department"] as? String)?.ifBlank { "IMSc.IT" } ?: "IMSc.IT"
        val condition = (data["condition"] as? String)?.ifBlank { "Like New" } ?: "Like New"
        val maxDuration = (data["maxDuration"] as? String)?.ifBlank { "Up to 3 days" } ?: "Up to 3 days"
        val securityDeposit = (data["securityDeposit"] as? String)?.ifBlank { "None" } ?: "None"
        val completedLends = (data["completedLends"] as? Number)?.toInt() ?: 0
        val ownerCourse = (data["ownerCourse"] as? String)?.ifBlank { "IMSc.IT Sem 4" } ?: "IMSc.IT Sem 4"
        val isAvailable = (data["isAvailable"] as? Boolean) ?: true
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: createdAt

        return CampusItem(
            id = id,
            title = title,
            description = description,
            category = category,
            priceLabel = priceLabel,
            isFree = isFree,
            pickupSpot = pickupSpot,
            ownerName = ownerName,
            ownerUid = ownerUid,
            rating = rating,
            imageUri = finalPrimaryImageUri,
            imageUris = finalImageUris,
            imagePublicIds = rawPublicIds,
            drawableRes = 0,
            department = department,
            condition = condition,
            maxDuration = maxDuration,
            securityDeposit = securityDeposit,
            completedLends = completedLends,
            ownerCourse = ownerCourse,
            createdAt = createdAt,
            isAvailable = isAvailable,
            updatedAt = updatedAt
        )
    }

    /**
     * Deserializes a Firestore [DocumentSnapshot] into a [CampusItem].
     */
    fun docToCampusItem(doc: DocumentSnapshot): CampusItem? {
        return mapToCampusItem(doc.id, doc.data)
    }

    /**
     * Publishes a new item listing to Firestore `items/{itemId}`.
     * Enforces that current user is authenticated and matches ownerUid.
     * On success, adds the item to [StudentProfileRepository] for immediate UI reactivity.
     */
    fun createItem(item: CampusItem, onComplete: (Boolean, String?) -> Unit) {
        val currentUid = try { auth?.currentUser?.uid } catch (_: Exception) { null }
        if (currentUid.isNullOrBlank()) {
            onComplete(false, "Authentication required to publish listings")
            return
        }

        val effectiveUid = if (item.ownerUid.isNotBlank()) item.ownerUid else currentUid
        if (effectiveUid != currentUid) {
            onComplete(false, "Cannot publish listing under another user's identity")
            return
        }

        val fs = firestore
        if (fs == null) {
            onComplete(false, "Firestore service is unavailable")
            return
        }

        val itemId = if (item.id.isNotBlank()) item.id else fs.collection(COLLECTION_ITEMS).document().id
        val preparedItem = item.copy(id = itemId, ownerUid = currentUid)
        val dataMap = campusItemToMap(preparedItem)

        fs.collection(COLLECTION_ITEMS).document(itemId)
            .set(dataMap)
            .addOnSuccessListener {
                val activeUid = try { auth?.currentUser?.uid } catch (_: Exception) { null }
                if (activeUid != currentUid) {
                    onComplete(false, "Account switched during publication")
                    return@addOnSuccessListener
                }
                StudentProfileRepository.addCurrentUserItem(preparedItem)
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                onComplete(false, e.localizedMessage ?: "Failed to save item to Firestore")
            }
    }

    /**
     * Retrieves a single item from Firestore by its document ID.
     */
    fun getItem(itemId: String, onComplete: (CampusItem?, Exception?) -> Unit) {
        if (itemId.isBlank()) {
            onComplete(null, IllegalArgumentException("Invalid item ID"))
            return
        }

        val fs = firestore
        if (fs == null) {
            onComplete(null, IllegalStateException("Firestore unavailable"))
            return
        }

        fs.collection(COLLECTION_ITEMS).document(itemId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onComplete(docToCampusItem(doc), null)
                } else {
                    onComplete(null, null)
                }
            }
            .addOnFailureListener { e ->
                onComplete(null, e)
            }
    }

    /**
     * Fetches all published items by a specific owner UID.
     */
    fun fetchItemsByOwner(ownerUid: String, onComplete: (List<CampusItem>, Exception?) -> Unit) {
        if (ownerUid.isBlank()) {
            onComplete(emptyList(), null)
            return
        }

        val fs = firestore
        if (fs == null) {
            onComplete(emptyList(), IllegalStateException("Firestore unavailable"))
            return
        }

        fs.collection(COLLECTION_ITEMS)
            .whereEqualTo("ownerUid", ownerUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents.mapNotNull { docToCampusItem(it) }
                onComplete(items, null)
            }
            .addOnFailureListener { e ->
                onComplete(emptyList(), e)
            }
    }

    /**
     * Fetches all published campus items ordered by creation date descending.
     */
    fun fetchAllItems(onComplete: (List<CampusItem>, Exception?) -> Unit) {
        val fs = firestore
        if (fs == null) {
            onComplete(emptyList(), IllegalStateException("Firestore unavailable"))
            return
        }

        fs.collection(COLLECTION_ITEMS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents.mapNotNull { docToCampusItem(it) }
                onComplete(items, null)
            }
            .addOnFailureListener { e ->
                onComplete(emptyList(), e)
            }
    }

    /**
     * Realtime listener for all items in the Firestore `items` collection.
     */
    fun listenAllItems(onUpdate: (List<CampusItem>) -> Unit): ListenerRegistration? {
        val fs = firestore ?: return null
        return try {
            fs.collection(COLLECTION_ITEMS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val items = snapshot.documents.mapNotNull { docToCampusItem(it) }
                    onUpdate(items)
                }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Realtime listener for items published by a specific owner UID.
     */
    fun listenItemsByOwner(ownerUid: String, onUpdate: (List<CampusItem>) -> Unit): ListenerRegistration? {
        if (ownerUid.isBlank()) return null
        val fs = firestore ?: return null
        return try {
            fs.collection(COLLECTION_ITEMS)
                .whereEqualTo("ownerUid", ownerUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val items = snapshot.documents.mapNotNull { docToCampusItem(it) }
                    onUpdate(items)
                }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Updates an item's availability status in Firestore.
     */
    fun updateItemAvailability(itemId: String, isAvailable: Boolean, onComplete: (Boolean, String?) -> Unit) {
        val currentUid = try { auth?.currentUser?.uid } catch (_: Exception) { null }
        if (currentUid.isNullOrBlank()) {
            onComplete(false, "Authentication required")
            return
        }

        val fs = firestore
        if (fs == null) {
            onComplete(false, "Firestore unavailable")
            return
        }

        val updates = hashMapOf<String, Any>(
            "isAvailable" to isAvailable,
            "updatedAt" to System.currentTimeMillis()
        )

        fs.collection(COLLECTION_ITEMS).document(itemId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                onComplete(false, e.localizedMessage ?: "Failed to update availability")
            }
    }

    /**
     * Deletes an item document from Firestore.
     */
    fun deleteItem(itemId: String, onComplete: (Boolean, String?) -> Unit) {
        val currentUid = try { auth?.currentUser?.uid } catch (_: Exception) { null }
        if (currentUid.isNullOrBlank()) {
            onComplete(false, "Authentication required")
            return
        }

        val fs = firestore
        if (fs == null) {
            onComplete(false, "Firestore unavailable")
            return
        }

        fs.collection(COLLECTION_ITEMS).document(itemId)
            .delete()
            .addOnSuccessListener {
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                onComplete(false, e.localizedMessage ?: "Failed to delete item")
            }
    }
}
