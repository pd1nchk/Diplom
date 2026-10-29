package com.example.diplom

import com.example.diplom.api.LessonJournalEntryDto
import com.example.diplom.api.TeacherGroupCourseDto
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import kotlin.test.*
import kotlinx.serialization.json.Json

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        environment {
            config = MapApplicationConfig(
                "ktor.deployment.port" to "0",
                "database.jdbcUrl" to "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to "",
            )
        }
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Attendance server is running", response.bodyAsText())
    }

    @Test
    fun authLogoutInvalidatesSession() = testApplication {
        environment {
            config = MapApplicationConfig(
                "ktor.deployment.port" to "0",
                "database.jdbcUrl" to "jdbc:h2:mem:testlogout;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to "",
            )
        }
        application {
            module()
        }
        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"studentTicket":"9400888","password":"1234"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status, login.bodyAsText())
        val token = Regex(""""token"\s*:\s*"([^"]+)"""").find(login.bodyAsText())!!.groupValues[1]
        val out = client.post("/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"studentTicket":"9400888","token":"$token"}""")
        }
        assertEquals(HttpStatusCode.NoContent, out.status, out.bodyAsText())
        val out2 = client.post("/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"studentTicket":"9400888","token":"$token"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, out2.status)
    }

    @Test
    fun journalMarkPresentPersistsAfterReload() = testApplication {
        val dbName = "jpr_${System.nanoTime()}"
        environment {
            config = MapApplicationConfig(
                "ktor.deployment.port" to "0",
                "database.jdbcUrl" to "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to "",
            )
        }
        application {
            module()
        }
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        val groupsRes = client.get("/attendance/teacher-groups/teacher")
        assertEquals(HttpStatusCode.OK, groupsRes.status, groupsRes.bodyAsText())
        val groups = json.decodeFromString<List<TeacherGroupCourseDto>>(groupsRes.bodyAsText())
        assertTrue(groups.isNotEmpty(), "seed should expose teacher groups")

        val picked = groups.mapNotNull { gg ->
            val jRes = client.get("/attendance/journal/teacher") {
                url {
                    parameters.append("groupId", gg.groupId.toString())
                    parameters.append("courseId", gg.courseId.toString())
                }
            }
            if (jRes.status != HttpStatusCode.OK) return@mapNotNull null
            val lessons = runCatching { json.decodeFromString<List<LessonJournalEntryDto>>(jRes.bodyAsText()) }.getOrNull()
                ?: return@mapNotNull null
            val lesson = lessons.firstOrNull { it.rows.isNotEmpty() } ?: return@mapNotNull null
            Triple(gg, lesson, lesson.rows.first().studentTicket)
        }.firstOrNull() ?: fail("no journal with at least one student row (check DomainSeed)")

        val g = picked.first
        val lesson = picked.second
        val studentTicket = picked.third

        val save = client.post("/attendance/journal/teacher") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"lessonId":${lesson.lessonId},"studentTicket":"$studentTicket","absenceReason":"PRESENT"}""",
            )
        }
        assertEquals(HttpStatusCode.OK, save.status, save.bodyAsText())

        val journal2Res = client.get("/attendance/journal/teacher") {
            url {
                parameters.append("groupId", g.groupId.toString())
                parameters.append("courseId", g.courseId.toString())
            }
        }
        assertEquals(HttpStatusCode.OK, journal2Res.status, journal2Res.bodyAsText())
        val lessons2 = json.decodeFromString<List<LessonJournalEntryDto>>(journal2Res.bodyAsText())
        val lesson2 = lessons2.first { it.lessonId == lesson.lessonId }
        val row2 = lesson2.rows.first { it.studentTicket == studentTicket }
        assertTrue(
            row2.isPresent || row2.attendanceStatus?.uppercase() == "PRESENT",
            "expected PRESENT after save; got isPresent=${row2.isPresent} status=${row2.attendanceStatus} reason=${row2.absenceReason}",
        )
    }

    @Test
    fun teacherLessonCheckNfcLabReturnsOk() = testApplication {
        val dbName = "nfcchk_${System.nanoTime()}"
        environment {
            config = MapApplicationConfig(
                "ktor.deployment.port" to "0",
                "database.jdbcUrl" to "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "database.user" to "sa",
                "database.password" to "",
            )
        }
        application {
            module()
        }
        val res = client.post("/attendance/teacher-lesson-check/teacher") {
            contentType(ContentType.Application.Json)
            setBody("""{"roomNfc":"04:49:29:5E:C1:2A:81"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertTrue(res.bodyAsText().contains("Компьютерные сети"), res.bodyAsText())
    }
}