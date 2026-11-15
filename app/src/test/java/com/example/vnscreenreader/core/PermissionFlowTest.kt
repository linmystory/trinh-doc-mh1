package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Luồng xin quyền: đã cấp, chưa cấp, bị từ chối, từ chối vĩnh viễn, tính năng chưa bật. */
class PermissionFlowTest {

    private val rationale = S.PERM_LOCATION_RATIONALE

    @Test
    fun featureNotEnabled_neverRequests() {
        assertEquals(PermissionStep.NotRequested, PermissionFlow.start(false, granted = false, rationale = rationale))
        assertEquals(PermissionStep.NotRequested, PermissionFlow.start(false, granted = true, rationale = rationale))
    }

    @Test
    fun alreadyGranted_proceedsImmediately() {
        assertEquals(PermissionStep.Proceed, PermissionFlow.start(true, granted = true, rationale = rationale))
    }

    @Test
    fun notGranted_explainsBeforeTheSystemDialog() {
        val step = PermissionFlow.start(true, granted = false, rationale = rationale)
        assertEquals(PermissionStep.Explain(rationale), step)
        assertEquals(rationale, PermissionFlow.message(step))
    }

    @Test
    fun afterDialog_granted_proceeds() {
        assertEquals(PermissionStep.Proceed, PermissionFlow.afterSystemDialog(granted = true, shouldShowRationale = false))
    }

    @Test
    fun afterDialog_denied_readsDeniedAndStops() {
        val step = PermissionFlow.afterSystemDialog(granted = false, shouldShowRationale = true)
        assertEquals(PermissionStep.Denied, step)
        assertEquals(S.PERM_DENIED, PermissionFlow.message(step))
        assertFalse(PermissionFlow.offersOpenSettings(step)) // không có nút Cài đặt, không hỏi lại ngay
    }

    @Test
    fun afterDialog_neverAskAgain_offersOpeningSettings() {
        val step = PermissionFlow.afterSystemDialog(granted = false, shouldShowRationale = false)
        assertEquals(PermissionStep.DeniedPermanently, step)
        assertEquals(S.PERM_DENIED_PERMANENTLY, PermissionFlow.message(step))
        assertTrue(PermissionFlow.offersOpenSettings(step))
    }

    @Test
    fun nothingToReadForProceedOrNotRequested() {
        assertNull(PermissionFlow.message(PermissionStep.Proceed))
        assertNull(PermissionFlow.message(PermissionStep.NotRequested))
    }

    @Test
    fun statusLabels_andMultiplePermissions() {
        assertEquals(S.PERM_STATUS_GRANTED, PermissionFlow.statusLabel(true))
        assertEquals(S.PERM_STATUS_DENIED, PermissionFlow.statusLabel(false))
        assertTrue(PermissionFlow.allGranted(listOf(true, true)))
        assertFalse(PermissionFlow.allGranted(listOf(true, false)))
        assertFalse(PermissionFlow.allGranted(emptyList())) // không có quyền nào để kiểm tra: không coi là đã cấp
    }

    @Test
    fun messagesAreAllReadable() {
        for (key in listOf(S.PERM_DENIED, S.PERM_DENIED_PERMANENTLY, S.PERM_LOCATION_RATIONALE, S.PERM_CONTACTS_RATIONALE,
            S.PERM_OPEN_SETTINGS, S.PERM_SETTINGS_INTRO, S.PERM_STATUS_GRANTED, S.PERM_STATUS_DENIED, S.PERM_NONE)) {
            assertTrue(key.name, t(key).isNotBlank())
        }
    }

    @Test
    fun everyFeatureHasUniqueIdPermissionsAndReadableTexts() {
        val all = PermissionFeatures.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.size >= 3)
        for (feature in all) {
            assertTrue(feature.id, feature.permissions.isNotEmpty())
            assertTrue(feature.id, t(feature.name).isNotBlank())
            assertTrue(feature.id, t(feature.rationale).isNotBlank())
        }
    }

    @Test
    fun sensitivePermissionsAreOnlyAskedForTheirOwnFeature() {
        val byPermission = PermissionFeatures.all.flatMap { f -> f.permissions.map { it to f.id } }.toMap()
        assertEquals("caller", byPermission["android.permission.READ_CALL_LOG"])
        assertEquals("caller", byPermission["android.permission.READ_PHONE_STATE"])
        assertEquals("contacts", byPermission["android.permission.READ_CONTACTS"])
        assertEquals("location", byPermission["android.permission.ACCESS_FINE_LOCATION"])
        assertFalse(byPermission.containsKey("android.permission.CALL_PHONE"))
    }
}
