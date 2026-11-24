package com.example.vnscreenreader.core

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileMigrationTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("filemigration").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun dir(name: String) = File(root, name).also { it.mkdirs() }

    private fun File.write(name: String, text: String) = File(this, name).also { it.writeText(text) }

    @Test
    fun everyFileMovesAndTheEmptySourceDirectoryDisappears() {
        val from = dir("old")
        from.write("crash-1.txt", "một")
        from.write("crash-2.txt", "hai")
        val to = File(root, "new")

        assertTrue(FileMigration.moveFiles(from, to))

        assertEquals("một", File(to, "crash-1.txt").readText())
        assertEquals("hai", File(to, "crash-2.txt").readText())
        assertFalse(from.exists())
    }

    @Test
    fun aFileAlreadyAtTheTargetIsKeptBecauseItIsNewer() {
        val from = dir("old")
        from.write("same.txt", "cũ")
        from.write("only-old.txt", "chỉ có ở cũ")
        val to = dir("new")
        to.write("same.txt", "mới")

        assertTrue(FileMigration.moveFiles(from, to))

        assertEquals("mới", File(to, "same.txt").readText())
        assertEquals("chỉ có ở cũ", File(to, "only-old.txt").readText())
        assertFalse(from.exists())
    }

    @Test
    fun aMissingSourceIsAlreadyMigratedAndCreatesNothing() {
        val to = File(root, "new")

        assertTrue(FileMigration.moveFiles(File(root, "never-existed"), to))

        assertFalse(to.exists())
    }

    @Test
    fun runningTwiceChangesNothingTheSecondTime() {
        val from = dir("old")
        from.write("a.txt", "a")
        val to = File(root, "new")
        assertTrue(FileMigration.moveFiles(from, to))

        assertTrue(FileMigration.moveFiles(from, to))

        assertEquals(listOf("a.txt"), to.list().orEmpty().toList())
        assertEquals("a", File(to, "a.txt").readText())
    }

    @Test
    fun theTargetDirectoryIsCreatedWithItsParents() {
        val from = dir("old")
        from.write("a.txt", "a")
        val to = File(root, "x/y/z")

        assertTrue(FileMigration.moveFiles(from, to))

        assertEquals("a", File(to, "a.txt").readText())
    }

    @Test
    fun subdirectoriesAreLeftAloneAndTheSourceStaysBecauseItIsNotEmpty() {
        val from = dir("old")
        from.write("a.txt", "a")
        File(from, "nested").mkdirs()
        val to = File(root, "new")

        assertTrue(FileMigration.moveFiles(from, to))

        assertTrue(File(to, "a.txt").isFile)
        assertTrue(File(from, "nested").isDirectory)
    }

    @Test
    fun anUncreatableTargetReportsFailureAndLosesNothing() {
        val from = dir("old")
        from.write("a.txt", "giữ lại")
        val blocker = File(root, "blocker").also { it.writeText("tệp chắn đường") }
        val to = File(blocker, "sub") // cha là tệp thường nên không tạo được thư mục

        assertFalse(FileMigration.moveFiles(from, to))

        assertEquals("giữ lại", File(from, "a.txt").readText())
    }

    @Test
    fun deleteQuietlyRemovesAFile() {
        val file = dir("d").write("a.txt", "a")

        assertTrue(FileMigration.deleteQuietly(file))

        assertFalse(file.exists())
    }

    @Test
    fun deleteQuietlyOnAMissingFileIsFine() {
        assertTrue(FileMigration.deleteQuietly(File(root, "missing.log")))
    }

    @Test
    fun deleteQuietlyReportsAFailureForANonEmptyDirectory() {
        val folder = dir("full")
        folder.write("a.txt", "a")

        assertFalse(FileMigration.deleteQuietly(folder))

        assertTrue(folder.isDirectory)
    }
}
