package space.ourmosaic.app.offline

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.ourmosaic.app.system.FrontSession
import kotlin.test.*

class OfflineManagerTest {
    private lateinit var offlineManager: OfflineManager
    private lateinit var settings: MapSettings

    @BeforeTest
    fun setup() {
        settings = MapSettings()
        offlineManager = OfflineManager(settings)
    }

    @Test
    fun testStartEndStartFrontingCycle() = runTest {
        val memberId = "member1"
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()

        // 1. Start session
        val startAction1 = PendingAction(
            id = "front_start_1",
            type = PendingActionType.START_FRONT,
            memberId = memberId,
            jsonPayload = "{}",
            timestamp = now
        )
        offlineManager.queueAction(startAction1)

        var sessions = offlineManager.cachedFrontSessions.first()
        assertNotNull(sessions)
        assertEquals(1, sessions.size)
        assertEquals(memberId, sessions[0].memberId)
        assertNull(sessions[0].endTime)

        // 2. End session
        val endAction1 = PendingAction(
            id = "front_end_1",
            type = PendingActionType.END_FRONT,
            memberId = memberId,
            sessionId = "front_start_1",
            jsonPayload = "{}",
            timestamp = now + 1000
        )
        offlineManager.queueAction(endAction1)

        sessions = offlineManager.cachedFrontSessions.first()
        assertNotNull(sessions)
        assertEquals(1, sessions.size)
        assertNotNull(sessions[0].endTime)

        // 3. Start another session
        val startAction2 = PendingAction(
            id = "front_start_2",
            type = PendingActionType.START_FRONT,
            memberId = memberId,
            jsonPayload = "{}",
            timestamp = now + 2000
        )
        offlineManager.queueAction(startAction2)

        sessions = offlineManager.cachedFrontSessions.first()
        assertNotNull(sessions)
        assertEquals(2, sessions.size)
        
        val activeSessions = sessions.filter { it.endTime == null }
        assertEquals(1, activeSessions.size)
        assertEquals("front_start_2", activeSessions[0].id)
    }

    @Test
    fun testSingleActiveSessionInvariant() = runTest {
        val memberId = "member1"
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()

        // Start session 1
        offlineManager.queueAction(PendingAction(
            id = "front_start_1",
            type = PendingActionType.START_FRONT,
            memberId = memberId,
            jsonPayload = "{}",
            timestamp = now
        ))

        // Start session 2 without ending session 1
        offlineManager.queueAction(PendingAction(
            id = "front_start_2",
            type = PendingActionType.START_FRONT,
            memberId = memberId,
            jsonPayload = "{}",
            timestamp = now + 5000
        ))

        val sessions = offlineManager.cachedFrontSessions.first()
        assertNotNull(sessions)
        
        val activeSessions = sessions.filter { it.endTime == null }
        // Should only have one active session due to invariant
        assertEquals(1, activeSessions.size)
        assertEquals("front_start_2", activeSessions[0].id)

        // Session 1 should have been automatically ended
        val session1 = sessions.find { it.id == "front_start_1" }
        assertNotNull(session1?.endTime)
    }

    @Test
    fun testServerReconciliationWithMappings() = runTest {
        val memberId = "member1"
        val localId = "front_start_local"
        val serverUuid = "server-uuid-123"
        val now = kotlin.time.Clock.System.now()

        // 1. Local start
        offlineManager.queueAction(PendingAction(
            id = localId,
            type = PendingActionType.START_FRONT,
            memberId = memberId,
            jsonPayload = "{}",
            timestamp = now.toEpochMilliseconds()
        ))

        // 2. Simulate server sync returning the session with a UUID
        offlineManager.saveIdMapping(serverUuid, localId)
        val serverSession = FrontSession(
            id = serverUuid,
            memberId = memberId,
            systemId = "system1",
            startTime = now.toString(),
            endTime = null
        )
        offlineManager.cacheFrontSessions(listOf(serverSession))
        
        // Remove the action as if synced
        offlineManager.removeAction(localId)

        val sessions = offlineManager.cachedFrontSessions.first()
        assertNotNull(sessions)
        
        // Should only see the server version (or correctly merged)
        // In current implementation, if we have mapping, it should deduplicate
        assertEquals(1, sessions.size)
        assertEquals(serverUuid, sessions[0].id)
        assertNull(sessions[0].endTime)
    }
}
