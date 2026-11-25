# BorrowBuddy – Backend Integration Readiness Audit Report

**Audit Phase:** Pre-Backend Architectural & Integration Readiness Inspection  
**Application:** BorrowBuddy Android App  
**Package:** `com.borrowbuddy.app`  
**Firebase Project ID:** `borrowbuddy-420fd`  
**Storage Bucket:** `borrowbuddy-420fd.firebasestorage.app`  
**Date:** November 2026  
**Status:** Audit Complete – Ready for Staged Backend Implementation  

---

## Executive Summary

BorrowBuddy has undergone an exhaustive, source-level Backend Integration Readiness Audit. The application currently features a complete, highly refined Android frontend built with Kotlin, ViewBinding, Material 3, and curved bottom navigation. It possesses passing unit test suites (26/26 tests, 100% pass rate) and a functional debug APK build.

However, the application currently operates primarily on **in-memory data structures, static seed lists, and local file storage**, with only partial Firebase Authentication and rudimentary Firestore document writing. 

This audit evaluates every Firebase configuration component, catalogs all mock and local data, analyzes repository architectures, designs a normalized Firestore database schema, drafts comprehensive security rules, plans cloud image storage, and outlines an 8-stage roadmap accompanied by a 2-account peer testing matrix.

---

## Phase 1: Current Backend Configuration Audit

| Firebase Service / Component | Configuration Status | Actual Code Implementation Status | Assessment & Notes |
| :--- | :---: | :---: | :--- |
| **Google Services Plugin** | **Fully Configured** | Applied in Gradle | `com.google.gms.google-services` v4.6.0 applied in `build.gradle.kts` and app module. |
| **`google-services.json`** | **Fully Configured** | Present & Valid | Project `borrowbuddy-420fd`, package `com.borrowbuddy.app`, active API key. |
| **Firebase BOM & SDKs** | **Fully Configured** | BOM 33.2.0 | `firebase-auth`, `firebase-firestore`, `firebase-storage` declared in `app/build.gradle.kts`. |
| **Firebase Authentication** | **Partially Configured** | Active in Auth Screens | Integrated in `LoginActivity`, `RegisterActivity`, `ForgotPasswordActivity`, `SplashActivity`, and `PrivacySecurityActivity`. Missing session teardown on logout. |
| **Cloud Firestore** | **Partially Configured** | Partial Read/Writes | Basic writes exist for `users`, `public_profiles`, `items`, `campus_requests`. **0% implemented for `exchanges` and `notifications`.** |
| **Firebase Storage** | **Configured but Unused** | Zero Code Integration | Dependency present in Gradle, but 0 lines of code call `FirebaseStorage`. App saves photos locally to private internal storage or keeps content URIs. |
| **Firestore Security Rules** | **Partially Configured** | `firestore.rules` present | Basic rules exist for `users`, `public_profiles`, `items`, and `campus_requests`. Rules for `exchanges` and `notifications` are missing. |
| **Storage Security Rules** | **Not Configured** | Missing file | No `storage.rules` exists in the project. |

### Incomplete & Potentially Conflicting Implementations Identified
1. **Local Content URI Leak into Firestore:** In `ListItemActivity.kt` line 311, `"imageUri" to newItem.imageUri` writes a local Android PhotoPicker content URI (`content://media/picker/...`) to Firestore. This URI is accessible ONLY on the author's device; other students cannot resolve or render the image.
2. **ID Disconnect Between Local and Remote Items:** `ListItemActivity.kt` generates an in-memory ID `"c_" + System.currentTimeMillis()`, while `firestore.collection("items").add(itemMap)` creates an auto-generated Firestore document ID. The two IDs are disconnected.
3. **Session Retention on Sign-Out:** When `auth.signOut()` is called in `ProfileFragment`, the in-memory singletons (`StudentProfileRepository`, `ExchangeRepository`) are not cleared. If Account B logs in on the same phone without killing the app process, Account B briefly sees Account A's cached data.

---

## Phase 2: Complete Mock Data Audit

| Feature / Data Entity | Current Data Source | Mock / Local / Remote | Persistence Level | Firebase Integration Requirement |
| :--- | :--- | :---: | :---: | :--- |
| **Logged-in Student Profile** | `StudentProfileRepository.currentUserProfile` | Local (Mock fallback) | Resets on process kill | Fetch from `public_profiles/{uid}` & `users/{uid}` |
| **Peer Student Profiles** | `StudentProfileRepository.peerProfiles` | Mock (10 seeded profiles) | Hardcoded | Query `public_profiles/{uid}` on demand |
| **Logged-in Student Listings**| `StudentProfileRepository.currentUserItems` | Local (2 seeded items) | In-memory only | Query `items.whereEqualTo("ownerUid", uid)` |
| **Marketplace Feed (Explore)**| `ExploreFragment.defaultItems` | Mock (10 items) + Local | Static seed fallback | Query `items.whereEqualTo("status", "AVAILABLE")` |
| **Recent Listings (Home)** | `HomeFragment.sampleItems` | Mock (10 items) + Local | Static seed fallback | Real-time `SnapshotListener` on `items` |
| **Glance Urgent Requests** | `HomeFragment.glanceRequests` | Mock (4 seeded requests) | In-memory only | Real-time `SnapshotListener` on `campus_requests` |
| **Borrowing Exchanges** | `ExchangeRepository.exchangeList` | Mock (5 borrow items) | In-memory only | Query `exchanges.whereEqualTo("borrowerUid", uid)` |
| **Lending Exchanges** | `ExchangeRepository.exchangeList` | Mock (5 lend items) | In-memory only | Query `exchanges.whereEqualTo("ownerUid", uid)` |
| **Exchange State Transitions**| `ExchangeRepository` transition methods | Local in-memory mutation | In-memory only | Firestore document updates on `exchanges/{id}` |
| **Activity History** | `ExchangeRepository.getAll()` | Mock / In-memory | In-memory only | Derived from Firestore `exchanges` |
| **Notifications Feed** | `NotificationRepository` (derived from mock) | Mock / Dynamic | In-memory + SharedPrefs | Query `notifications.whereEqualTo("recipientUid", uid)`|
| **Profile Photo (Avatar)** | `AvatarHelper` (`context.filesDir`) | Local internal storage | Survives locally only | Upload to Cloud Storage `profile_photos/{uid}` |
| **Listing Photos** | `SelectedPhotoAdapter` (PhotoPicker URIs) | Local device cache | In-memory only | Upload to Cloud Storage `item_photos/{itemId}` |
| **Completed Lends / Borrows** | `ExchangeRepository.getAll().count(...)` | Mock / Local calculation | In-memory only | Atomic increments on `public_profiles/{uid}` |
| **App Preferences** | `AppPreferencesManager` | Local (SharedPreferences)| Persistent locally | Preserve SharedPreferences (no cloud needed) |

---

## Phase 3: Repository and Model Analysis

### 3.1 `StudentProfileRepository`
- **Responsibilities:** Single source of truth for user profile, email masking, and personal listings.
- **Current Data Source:** In-memory `currentUserProfile`, `currentUserItems`, `peerProfiles` map.
- **Used By:** `MainActivity`, `ProfileFragment`, `ListItemActivity`, `StudentProfileActivity`, `MyListingsActivity`, `EditProfileBottomSheetFragment`.
- **Firebase Changes Needed:**
  - Connect `syncWithFirebase(user)` to attach a real-time snapshot listener on `public_profiles/{uid}`.
  - Implement `clearSession()` to reset profile upon logout.
  - Keep existing public method signatures intact to avoid breaking UI consumers.
- **Preservation Verdict:** **100% Architecture Preserved.**

### 3.2 `ExchangeRepository`
- **Responsibilities:** Manages borrowing and lending requests, handover PIN verification, 7-step status transitions, unseen activity tracking.
- **Current Data Source:** In-memory mutable list (`exchangeList`).
- **Used By:** `ExchangesFragment`, `ExchangeDetailBottomSheetFragment`, `BorrowConfirmBottomSheetFragment`, `ItemDetailBottomSheetFragment`, `ProfileFragment`, `ActivityHistoryActivity`, `NotificationRepository`.
- **Firebase Changes Needed:**
  - Back `exchangeList` with two Firestore snapshot listeners: `exchanges.whereEqualTo("borrowerUid", currentUid)` and `exchanges.whereEqualTo("ownerUid", currentUid)`.
  - Update `addBorrowRequest()`, `acceptRequest()`, `confirmHandover()`, `initiateReturn()`, `confirmReturn()` to write mutations to Firestore.
- **Preservation Verdict:** **100% Architecture Preserved.** All 7 calling components continue calling existing methods seamlessly.

### 3.3 `NotificationRepository`
- **Responsibilities:** Aggregates activity notifications, tracks unread counts, persists read state.
- **Current Data Source:** Dynamically generated from `ExchangeRepository.getAll()`; read IDs stored in `SharedPreferences`.
- **Used By:** `NotificationsActivity`, `HomeFragment` (bell icon badge), `ProfileFragment`.
- **Firebase Changes Needed:**
  - Query Firestore collection `notifications.whereEqualTo("recipientUid", uid)`.
  - Batch update `isRead = true` in Firestore upon "Mark All as Read".
- **Preservation Verdict:** **100% Architecture Preserved.**

---

## Phase 4: Proposed Firestore Database Design

Detailed collection schemas and composite indexes are documented in [FIRESTORE_DATABASE_SCHEMA.md](file:///c:/Projects/BorrowBuddy%20-%20Copy/FIRESTORE_DATABASE_SCHEMA.md).

### Summary of Primary Collections:
1. `/users/{userId}`: Private credentials, email, account status (Owner-only access).
2. `/public_profiles/{userId}`: Student name, college, department, semester, bio, avatar, rating (No emails!).
3. `/items/{itemId}`: Marketplace listings with category, price, condition, owner UID, and availability status.
4. `/campus_requests/{requestId}`: Urgent Glance broadcast requests with expiration timestamps.
5. `/exchanges/{exchangeId}`: Multi-step borrowing/lending contracts between lender and borrower.
6. `/notifications/{notificationId}`: Real-time recipient alerts and activity updates.

---

## Phase 5: Authentication and User Identity Audit

1. **UID as Primary Key:** All user resources across `users`, `public_profiles`, `items.ownerUid`, `exchanges.borrowerUid`, and `exchanges.ownerUid` bind strictly to `request.auth.uid`.
2. **Account Creation:** `RegisterActivity` creates `users/{uid}` and `public_profiles/{uid}` in a transactional rollback-safe sequence (rolls back with `user.delete()` if Firestore write fails).
3. **Session Retrieval:** `MainActivity` and `ProfileFragment` call `StudentProfileRepository.syncWithFirebase(auth.currentUser)` upon launch.
4. **Guest Mode Behavior:** Guest/unauthenticated users can browse public items in Home and Explore, but tapping "List an Item" or "Request to Borrow" prompts navigation to `WelcomeActivity`.
5. **Logout Cleansing:** `StudentProfileRepository.clearSession()`, `ExchangeRepository.clearSession()`, and `NotificationRepository.clearSession()` must execute upon sign-out to prevent data bleeding between accounts.

---

## Phase 6: Image Storage Plan

1. **Profile Photos:**
   - Saved temporarily to local cache via `AvatarHelper`.
   - Uploaded to Cloud Storage path: `profile_photos/{uid}/avatar.jpg`.
   - Download URL stored in `public_profiles/{uid}.photoUri`.
2. **Item Listings Photos:**
   - Multi-photo picker captures images.
   - Uploaded to Cloud Storage path: `item_photos/{itemId}/{index}.jpg`.
   - Array of download URLs stored in `items/{itemId}.additionalPhotos` and primary in `items/{itemId}.imageUri`.
3. **Image Loading:**
   - Uses Android standard image decoding / Coil / Glide to load HTTPS download URLs. Local `content://` URIs are never persisted to Firestore.

---

## Phase 7: Firebase Security Plan

Complete rules specifications are documented in [FIREBASE_SECURITY_PLAN.md](file:///c:/Projects/BorrowBuddy%20-%20Copy/FIREBASE_SECURITY_PLAN.md).

- **Email Privacy:** Enforced by rule constraint `!('email' in request.resource.data)` on `public_profiles`.
- **Campus Verification:** Enforced by rule constraint preventing client mutation of `isVerifiedCampusStudent` to `true`.
- **Exchange Isolation:** `exchanges/{id}` restricted strictly to `isParticipant(ownerUid, borrowerUid)`.

---

## Phase 8: Staged Implementation Roadmap

The full 8-stage roadmap is documented in [FIREBASE_IMPLEMENTATION_ROADMAP.md](file:///c:/Projects/BorrowBuddy%20-%20Copy/FIREBASE_IMPLEMENTATION_ROADMAP.md).

- **Stage 1:** Firebase Authentication & Session Lifecycle.
- **Stage 2:** Student Profile Creation & Retrieval.
- **Stage 3:** Firestore Item Listings.
- **Stage 4:** Home & Explore Live Feed Data.
- **Stage 5:** Campus Item Requests (Glance).
- **Stage 6:** Exchange Management & Real-Time Status Updates.
- **Stage 7:** Firebase Storage for Images.
- **Stage 8:** Real-Time Notifications.

---

## Phase 9: Two-Account Peer Testing Protocol

| Test Step | Student A (Lender: Rahul Dave / `uid_A`) | Student B (Borrower: Harsh Mistry / `uid_B`) | Expected Verification Check |
| :---: | :--- | :--- | :--- |
| **1. Registration** | Register as `rahul@campus.edu`. Fill profile: "Rahul Dave", IMSc.IT. | Register as `harsh@campus.edu`. Fill profile: "Harsh Mistry", IMSc.IT. | Two distinct documents created in `users` and `public_profiles`. No email in public docs. |
| **2. Publish Listing**| List "Scientific Calculator fx-991", Category: TECH, Free, Library. | On Explore tab, browse TECH category. | Item appears in Firestore `/items/` and renders on Student B's Explore grid within 2s. |
| **3. Self-Borrow Guard**| Open own Calculator card. | N/A | "Request to Borrow" button is disabled ("Your Listing"). |
| **4. Borrow Request** | Waiting on Lend tab. | Tap "Request to Borrow" on Calculator, enter note: "Exam today", set PIN `4892`. | Document created in `exchanges` (`status: AWAITING_RESPONSE`). Student B sees pass. |
| **5. Request Alert** | Receives real-time badge on Lend tab & alert in Notifications. | Watches Borrow tab (status: "Awaiting Response", Step 1). | `ExchangeRepository` listener updates UI on both devices without manual refresh. |
| **6. Accept Request** | Taps "Accept Request" in Exchange Details. | Stepper automatically updates to Step 3 ("Handover Pending"). | `exchanges/{id}.status` updates to `HANDOVER_PENDING`. Item marked `BORROWED` in marketplace. |
| **7. Double-Borrow Guard**| N/A | Student C tries to borrow Calculator. | Button shows "Item Currently Borrowed"; request blocked. |
| **8. In-Person Handover**| Meets Student B at Library. Verifies PIN `4892`. Taps "Confirm Handover". | Stepper updates to Step 4 ("Borrowed"). Item in possession. | `exchanges/{id}.status` updates to `BORROWED`. Handover timestamp set. |
| **9. Return Process** | Meets Student B. Taps "Confirm Return" after checking item. | Taps "Initiate Return" when handing back item. | `exchanges/{id}.status` transitions to `RETURN_PENDING`, then `COMPLETED` (Step 7). |
| **10. Post-Exchange Stats**| Profile shows: Completed Lends = 1. Calculator status restored to `AVAILABLE`. | Profile shows: Completed Borrows = 1. Calculator removed from active borrows. | Profile counters increment in `public_profiles`. Audit trail preserved in Activity History. |
| **11. Account Isolation**| Sign out. Sign in as Student B on Device A. | N/A | Student B sees Student B's data only. Zero residual listings or profile data from Student A. |

---

## Audit Sign-Off & Implementation Readiness

1. **Application Code Untouched:** Confirmed zero Kotlin source files, XML layouts, or assets were modified during this audit.
2. **Architecture Compatibility:** Existing repositories (`StudentProfileRepository`, `ExchangeRepository`, `NotificationRepository`) are architected cleanly as observer singletons and will be extended rather than replaced.
3. **Documentation Deliverables Complete:**
   - [BACKEND_INTEGRATION_READINESS_REPORT.md](file:///c:/Projects/BorrowBuddy%20-%20Copy/BACKEND_INTEGRATION_READINESS_REPORT.md)
   - [FIRESTORE_DATABASE_SCHEMA.md](file:///c:/Projects/BorrowBuddy%20-%20Copy/FIRESTORE_DATABASE_SCHEMA.md)
   - [FIREBASE_IMPLEMENTATION_ROADMAP.md](file:///c:/Projects/BorrowBuddy%20-%20Copy/FIREBASE_IMPLEMENTATION_ROADMAP.md)
   - [BACKEND_DEPENDENCY_MAP.md](file:///c:/Projects/BorrowBuddy%20-%20Copy/BACKEND_DEPENDENCY_MAP.md)
   - [FIREBASE_SECURITY_PLAN.md](file:///c:/Projects/BorrowBuddy%20-%20Copy/FIREBASE_SECURITY_PLAN.md)
