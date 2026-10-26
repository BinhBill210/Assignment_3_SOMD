package com.example.assignment_3.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.assignment_3.data.local.dao.AssessmentDao
import com.example.assignment_3.data.local.dao.StudySessionDao
import com.example.assignment_3.data.local.dao.StudyUnitDao
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.data.local.entity.StudySession
import com.example.assignment_3.data.local.entity.StudyUnit
import com.example.assignment_3.data.local.model.AssessmentWithUnit
import com.example.assignment_3.data.local.model.CascadeCounts
import com.example.assignment_3.data.local.model.UnitProgress
import com.example.assignment_3.domain.AssessmentStatus
import com.example.assignment_3.domain.DuplicateUnitCodeException
import com.example.assignment_3.domain.ReminderPolicy
import com.example.assignment_3.notifications.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Clock

class RoomStudyUnitRepository(
    private val unitDao: StudyUnitDao,
    private val assessmentDao: AssessmentDao,
    private val reminderScheduler: ReminderScheduler
) : StudyUnitRepository {

    override fun observeUnits(): Flow<List<StudyUnit>> = unitDao.observeAll()

    override fun observeUnit(id: Long): Flow<StudyUnit?> = unitDao.observeById(id)

    override fun observeProgress(weekStart: Long, weekEnd: Long): Flow<List<UnitProgress>> =
        unitDao.observeProgress(weekStart, weekEnd)

    override suspend fun getUnit(id: Long): StudyUnit? = unitDao.getById(id)

    override suspend fun saveUnit(unit: StudyUnit): Long = try {
        if (unit.id == 0L) {
            unitDao.insert(unit)
        } else {
            unitDao.update(unit)
            unit.id
        }
    } catch (e: SQLiteConstraintException) {
        throw DuplicateUnitCodeException(unit.code)
    }

    override suspend fun cascadeCounts(unitId: Long): CascadeCounts = unitDao.cascadeCounts(unitId)

    override suspend fun deleteUnit(unit: StudyUnit) {
        // Ids must be read before the delete: afterwards the cascade has already removed them.
        val assessmentIds = assessmentDao.getIdsForUnit(unit.id)
        unitDao.delete(unit)
        assessmentIds.forEach(reminderScheduler::cancel)
    }
}

class RoomAssessmentRepository(
    private val assessmentDao: AssessmentDao,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock
) : AssessmentRepository {

    override fun observeOutstanding(): Flow<List<AssessmentWithUnit>> = assessmentDao.observeOutstanding()

    override fun observeForUnit(unitId: Long): Flow<List<Assessment>> = assessmentDao.observeForUnit(unitId)

    override fun observeAssessment(id: Long): Flow<AssessmentWithUnit?> = assessmentDao.observeWithUnit(id)

    override suspend fun getAssessment(id: Long): Assessment? = assessmentDao.getById(id)

    override suspend fun weightingTotal(unitId: Long, excludingId: Long): Int =
        assessmentDao.weightingTotal(unitId, excludingId)

    override suspend fun saveAssessment(assessment: Assessment): Long {
        val stamped = assessment.copy(updatedAt = clock.millis())
        val id = if (stamped.id == 0L) {
            assessmentDao.insert(stamped)
        } else {
            assessmentDao.update(stamped)
            stamped.id
        }
        syncReminder(stamped.copy(id = id))
        return id
    }

    override suspend fun updateStatus(id: Long, status: AssessmentStatus) {
        assessmentDao.updateStatus(id, status, clock.millis())
        assessmentDao.getById(id)?.let(::syncReminder)
    }

    override suspend fun deleteAssessment(assessment: Assessment) {
        assessmentDao.delete(assessment)
        reminderScheduler.cancel(assessment.id)
    }

    private fun syncReminder(assessment: Assessment) {
        val delay = ReminderPolicy.delayUntilReminder(
            nowMillis = clock.millis(),
            dueAtMillis = assessment.dueAt,
            offsetMinutes = assessment.reminderOffsetMinutes,
            status = assessment.status
        )
        if (delay == null) {
            reminderScheduler.cancel(assessment.id)
        } else {
            reminderScheduler.schedule(assessment.id, assessment.dueAt, delay)
        }
    }
}

class RoomStudySessionRepository(
    private val sessionDao: StudySessionDao
) : StudySessionRepository {

    override fun observeSessions(assessmentId: Long): Flow<List<StudySession>> =
        sessionDao.observeForAssessment(assessmentId)

    override suspend fun saveSession(session: StudySession): Long {
        if (session.id != 0L && sessionDao.getById(session.id) != null) {
            sessionDao.update(session)
            return session.id
        }
        return sessionDao.insert(session)
    }

    override suspend fun deleteSession(session: StudySession) {
        sessionDao.delete(session)
    }
}
