package com.example.diplom.db

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/** Связь «группа изучает дисциплину» (учебный план). Занятие в расписании допустимо только при наличии этой связи. */
object GroupCourseLinks {

    fun isLinked(groupId: Long, courseId: Long): Boolean = transaction {
        GroupCoursesTable.selectAll().where {
            (GroupCoursesTable.groupId eq groupId) and (GroupCoursesTable.courseId eq courseId)
        }.any()
    }

    fun ensureLink(groupId: Long, courseId: Long) {
        if (isLinked(groupId, courseId)) return
        transaction {
            GroupCoursesTable.insert {
                it[GroupCoursesTable.groupId] = groupId
                it[GroupCoursesTable.courseId] = courseId
            }
        }
    }

    /** Для существующих БД: восстановить связи по фактическим парам в lessons. */
    fun syncFromLessons() = transaction {
        syncFromLessonsUnsafe()
    }

    internal fun syncFromLessonsUnsafe() {
        LessonsTable.selectAll().forEach { row ->
            val groupId = row[LessonsTable.groupId]
            val courseId = row[LessonsTable.courseId]
            val exists = GroupCoursesTable.selectAll().where {
                (GroupCoursesTable.groupId eq groupId) and (GroupCoursesTable.courseId eq courseId)
            }.any()
            if (!exists) {
                GroupCoursesTable.insert {
                    it[GroupCoursesTable.groupId] = groupId
                    it[GroupCoursesTable.courseId] = courseId
                }
            }
        }
    }
}