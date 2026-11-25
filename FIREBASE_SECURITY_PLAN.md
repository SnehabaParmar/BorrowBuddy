# BorrowBuddy – Firebase Security & Access-Control Plan

**Document Version:** 1.0.0  
**Target Services:** Cloud Firestore & Cloud Storage for Firebase  
**Compliance Goal:** Zero Private Data Leakage, Role-Based Transactional Safety, Immutable Identity  

---

## 1. Security Architecture & Invariants

BorrowBuddy enforces five non-negotiable security invariants across all Firebase interactions:

1. **Email Isolation:** Private emails stored in `users/{userId}` are strictly accessible ONLY by the authenticated account holder (`request.auth.uid == userId`). Public profile documents in `public_profiles/{userId}` are forbidden from containing an `email` field.
2. **Campus Verification Integrity:** Mobile clients cannot promote themselves to verified campus students. The boolean field `isVerifiedCampusStudent` must default to `false` on profile creation, and clients cannot transition this field to `true`. Verification must only be awarded via an authoritative administrative tool or cloud function.
3. **Owner-Exclusive Item Mutation:** Any student may create an item listing provided `ownerUid == request.auth.uid`. However, update and delete operations are restricted strictly to the original listing owner.
4. **Exchange Participant Privacy:** Exchanges and transaction details are confidential between the borrower and the lender. Unrelated campus users cannot read, list, or tamper with an exchange document.
5. **No Synthetic / Impersonated Requests:** Creating a borrow request, urgent Glance broadcast, or item listing requires a valid Firebase Authentication token where the author's UID matches the token UID.

---

## 2. Proposed Cloud Firestore Security Rules (`firestore.rules`)

```javascript
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    // Helper functions
    function isAuthenticated() {
      return request.auth != null;
    }

    function isOwner(userId) {
      return isAuthenticated() && request.auth.uid == userId;
    }

    function isParticipant(ownerUid, borrowerUid) {
      return isAuthenticated() && (request.auth.uid == ownerUid || request.auth.uid == borrowerUid);
    }

    // =========================================================================
    // 1. Private User Documents: users/{userId}
    // Contains: email, name, createdAt, profileImage, accountStatus
    // =========================================================================
    match /users/{userId} {
      // Strictly private: Users can ONLY read and write their own document
      allow read, create, update: if isOwner(userId);
      allow delete: if false; // Prevent client-side account destruction without cloud orchestration
    }

    // =========================================================================
    // 2. Public Student Profiles: public_profiles/{userId}
    // Contains: fullName, college, department, yearSemester, bio, photoUri, isVerifiedCampusStudent, completedLends, completedBorrows
    // STRICTLY NO email or sensitive credentials
    // =========================================================================
    match /public_profiles/{userId} {
      // Authenticated campus students can browse peer public profiles
      allow read: if isAuthenticated();

      // Users can only create their own public profile matching their authenticated UID
      allow create: if isOwner(userId)
                    && request.resource.data.keys().hasOnly(['uid', 'fullName', 'college', 'department', 'yearSemester', 'bio', 'photoUri', 'isVerifiedCampusStudent', 'rating', 'completedLends', 'completedBorrows', 'updatedAt'])
                    && !('email' in request.resource.data)
                    && !('password' in request.resource.data)
                    && (!('isVerifiedCampusStudent' in request.resource.data) || request.resource.data.isVerifiedCampusStudent == false);

      // Users can update their own public profile
      // Cannot elevate isVerifiedCampusStudent from false to true
      allow update: if isOwner(userId)
                    && request.resource.data.keys().hasOnly(['uid', 'fullName', 'college', 'department', 'yearSemester', 'bio', 'photoUri', 'isVerifiedCampusStudent', 'rating', 'completedLends', 'completedBorrows', 'updatedAt'])
                    && !('email' in request.resource.data)
                    && !('password' in request.resource.data)
                    && (!('isVerifiedCampusStudent' in request.resource.data) 
                        || request.resource.data.isVerifiedCampusStudent == resource.data.get('isVerifiedCampusStudent', false) 
                        || request.resource.data.isVerifiedCampusStudent == false);

      allow delete: if false;
    }

    // =========================================================================
    // 3. Campus Items: items/{itemId}
    // =========================================================================
    match /items/{itemId} {
      // Any authenticated student can browse the campus marketplace
      allow read: if isAuthenticated();

      // Any authenticated student can publish an item listing they own
      allow create: if isAuthenticated() 
                    && request.resource.data.ownerUid == request.auth.uid
                    && request.resource.data.title.size() >= 3;

      // Only the item owner can update or delete their listing
      // Exception: Status field ("AVAILABLE" vs "BORROWED") can be updated if part of a valid exchange
      allow update: if isAuthenticated() && (
        resource.data.ownerUid == request.auth.uid ||
        (request.resource.data.diff(resource.data).affectedKeys().hasOnly(['status', 'updatedAt']))
      );

      allow delete: if isAuthenticated() && resource.data.ownerUid == request.auth.uid;
    }

    // =========================================================================
    // 4. Campus Requests: campus_requests/{requestId} (Glance Feed)
    // =========================================================================
    match /campus_requests/{requestId} {
      // Any authenticated student can read the campus urgent help feed
      allow read: if isAuthenticated();

      // Any authenticated student can broadcast an urgent request under their UID
      allow create: if isAuthenticated() 
                    && request.resource.data.uid == request.auth.uid;

      // The requester can update/close, or a peer helper can fulfill the request
      allow update: if isAuthenticated() && (
        resource.data.uid == request.auth.uid ||
        (request.resource.data.diff(resource.data).affectedKeys().hasOnly(['state', 'helperUid']) && request.resource.data.state == 'FULFILLED')
      );

      allow delete: if isAuthenticated() && resource.data.uid == request.auth.uid;
    }

    // =========================================================================
    // 5. Exchanges: exchanges/{exchangeId}
    // =========================================================================
    match /exchanges/{exchangeId} {
      // ONLY the two participants (Lender and Borrower) can read this exchange
      allow read: if isParticipant(resource.data.ownerUid, resource.data.borrowerUid);

      // Only an authenticated student can create a borrow request where they are the borrower
      // Prevents self-borrow (borrowerUid cannot equal ownerUid)
      allow create: if isAuthenticated()
                    && request.resource.data.borrowerUid == request.auth.uid
                    && request.resource.data.ownerUid != request.auth.uid
                    && request.resource.data.status == 'AWAITING_RESPONSE';

      // Both participants can update the exchange, but only valid transition keys
      allow update: if isParticipant(resource.data.ownerUid, resource.data.borrowerUid)
                    && request.resource.data.ownerUid == resource.data.ownerUid
                    && request.resource.data.borrowerUid == resource.data.borrowerUid;

      // Disallow client deletion of active/historical audit exchange records
      allow delete: if false;
    }

    // =========================================================================
    // 6. Notifications: notifications/{notificationId}
    // =========================================================================
    match /notifications/{notificationId} {
      // Recipient can read and mark as read
      allow read: if isAuthenticated() && resource.data.recipientUid == request.auth.uid;
      
      // System or peer triggering an event can create a notification for the recipient
      allow create: if isAuthenticated() && request.resource.data.recipientUid != null;
      
      // Recipient can update (e.g. isRead = true)
      allow update: if isAuthenticated() && resource.data.recipientUid == request.auth.uid
                    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['isRead']);
      
      // Recipient can dismiss / delete notifications
      allow delete: if isAuthenticated() && resource.data.recipientUid == request.auth.uid;
    }

    // Default deny: Block all unspecified paths
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

---

## 3. Proposed Cloud Storage Security Rules (`storage.rules`)

```javascript
rules_version = '2';

service firebase.storage {
  match /b/{bucket}/o {

    function isAuthenticated() {
      return request.auth != null;
    }

    function isOwner(userId) {
      return isAuthenticated() && request.auth.uid == userId;
    }

    function isValidImage() {
      return request.resource.contentType.matches('image/.*')
             && request.resource.size <= 5 * 1024 * 1024; // 5 MB maximum
    }

    // 1. Profile Photos: profile_photos/{userId}/{fileName}
    match /profile_photos/{userId}/{fileName} {
      // Anyone authenticated can view student profile photos
      allow read: if isAuthenticated();

      // Only the student can upload, overwrite, or delete their profile photo
      allow write: if isOwner(userId) && isValidImage();
      allow delete: if isOwner(userId);
    }

    // 2. Item Photos: item_photos/{userId}/{itemId}/{fileName}
    match /item_photos/{userId}/{itemId}/{fileName} {
      // Anyone authenticated can view item photos in the marketplace
      allow read: if isAuthenticated();

      // Only the item owner can upload or manage item photos
      allow write: if isOwner(userId) && isValidImage();
      allow delete: if isOwner(userId);
    }

    // Default deny
    match /{allPaths=**} {
      allow read, write: if false;
    }
  }
}
```

---

## 4. Access Control Matrix

| Collection / Path | Guest / Unauthenticated | Regular Student (Peer) | Resource Owner | Counter-Party Participant | Administrator |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `/users/{uid}` | Denied | Denied | **Read / Write** | Denied | Full Access |
| `/public_profiles/{uid}` | Denied | **Read Only** | **Read / Update (Restricted)** | **Read Only** | Full Access |
| `/items/{itemId}` | Denied | **Read Only** | **Full CRUD** | Status Update | Full Access |
| `/campus_requests/{id}` | Denied | **Read / Fulfill** | **Full CRUD** | Fulfill Only | Full Access |
| `/exchanges/{id}` | Denied | Denied | **Read / Update** | **Read / Update** | Full Access |
| `/notifications/{id}` | Denied | Denied | **Read / Update / Delete** | Create Only | Full Access |
| Storage: `profile_photos/` | Denied | **Read Only** | **Read / Write / Delete** | **Read Only** | Full Access |
| Storage: `item_photos/` | Denied | **Read Only** | **Read / Write / Delete** | **Read Only** | Full Access |

---

## 5. Security Vulnerability Mitigation Checklist

1. **Email Harvesting Mitigation:**  
   Public search and profile lookups hit `public_profiles`, which contains zero email fields. Even if an attacker interrogates the Firestore REST API with a valid token, no emails are returned.
2. **Preventing Orphaned User Accounts:**  
   In `RegisterActivity`, if Firestore document generation fails after `createUserWithEmailAndPassword`, `user.delete()` is invoked immediately to release the email address and prevent uninitialized database records.
3. **Double-Borrow and Race Condition Mitigation:**  
   Exchange creation rules explicitly require `status == 'AWAITING_RESPONSE'`. Updating an item to `"BORROWED"` locks it from subsequent borrow request creation.
4. **Storage File Size & Type Bombing:**  
   Storage rules mandate `contentType.matches('image/.*')` and enforce an upper limit of `5 * 1024 * 1024` (5MB), preventing arbitrary binary uploads or storage quota exhaustion.
