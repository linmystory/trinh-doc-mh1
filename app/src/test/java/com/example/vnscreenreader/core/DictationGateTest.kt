package com.example.vnscreenreader.core

import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationGateTest {

    private val voiceRecognition = MicRecording(source = 6, silenced = false)
    private val plainMic = MicRecording(source = 1, silenced = false)

    private fun gate() = DictationGate(tailMs = 700, maxMuteMs = 120_000)

    private fun DictationGate.record(now: Long, typing: Boolean, vararg recordings: MicRecording) =
        update(recordings.toList(), now) { typing }

    @Test
    fun nothingRecordingMeansNothingIsMuted() {
        val gate = gate()
        gate.record(1_000, typing = true)
        assertFalse(gate.isMuted(1_000))
        assertNull(gate.nextChangeAt(1_000))
    }

    @Test
    fun voiceTypingWithTheKeyboardOpenSilencesTheReader() {
        val gate = gate()
        gate.record(1_000, typing = true, voiceRecognition)
        assertTrue(gate.isMuted(1_000))
        assertTrue(gate.isMuted(60_000))
    }

    @Test
    fun aRecorderRunningWhileNoKeyboardIsOpenDoesNotSilenceTheReader() {
        // Ứng dụng ghi âm chạy nền: người dùng vẫn cần nghe trình đọc khi dùng điện thoại.
        val gate = gate()
        gate.record(1_000, typing = false, plainMic)
        assertFalse(gate.isMuted(1_000))
        assertFalse(gate.isMuted(50_000))
    }

    @Test
    fun aSilencedRecordingCapturesNothingSoItDoesNotCount() {
        val gate = gate()
        gate.record(1_000, typing = true, MicRecording(source = 6, silenced = true))
        assertFalse(gate.isMuted(1_000))
    }

    @Test
    fun callsVideoAndAlwaysOnHotwordRecordingsDoNotCount() {
        // 7 = VOICE_COMMUNICATION (gọi qua mạng), 5 = CAMCORDER, 8 = REMOTE_SUBMIX, 1999 = HOTWORD (nghe từ khóa chạy ngầm), 4 = VOICE_CALL.
        for (source in listOf(7, 5, 8, 1999, 4, 2, 3, 10, 1997, 1998, 2000)) {
            val gate = gate()
            gate.record(1_000, typing = true, MicRecording(source, silenced = false))
            assertFalse("nguồn $source không được làm trình đọc im", gate.isMuted(1_000))
        }
    }

    @Test
    fun theSourcesThatCountAreExactlyTheSpeechInputOnes() {
        // Hằng số thật của Android (trình biên dịch nhúng giá trị, nên không cần android.jar lúc chạy): bắt lỗi chép sai số trong DictationGate.
        val speechInput = listOf(
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.UNPROCESSED,
        )
        assertEquals(speechInput.toSet(), DictationGate.DICTATION_SOURCES)
        for (source in speechInput) {
            val gate = gate()
            gate.record(1_000, typing = true, MicRecording(source, silenced = false))
            assertTrue("nguồn $source phải làm trình đọc im", gate.isMuted(1_000))
        }
    }

    @Test
    fun anyCountedRecordingAmongSeveralIsEnough() {
        val gate = gate()
        gate.record(1_000, typing = true, MicRecording(1999, silenced = false), MicRecording(7, silenced = false), voiceRecognition)
        assertTrue(gate.isMuted(1_000))
    }

    @Test
    fun theReaderStaysQuietBrieflyAfterRecordingStopsThenSpeaksAgain() {
        val gate = gate()
        gate.record(1_000, typing = true, voiceRecognition)
        gate.record(5_000, typing = true) // dừng thu
        assertTrue(gate.isMuted(5_000))
        assertTrue(gate.isMuted(5_699))
        assertFalse(gate.isMuted(5_700))
    }

    @Test
    fun noTailWhenTheReaderWasNotSilencedToBeginWith() {
        val gate = gate()
        gate.record(1_000, typing = false, plainMic)
        gate.record(5_000, typing = false)
        assertFalse(gate.isMuted(5_000))
    }

    @Test
    fun aLongRunningRecordingStopsSilencingTheReaderAfterTheSafetyValve() {
        val gate = DictationGate(tailMs = 100, maxMuteMs = 1_000)
        gate.record(0, typing = true, plainMic)
        assertTrue(gate.isMuted(999))
        assertFalse(gate.isMuted(1_000))
        assertFalse(gate.isMuted(500_000))
    }

    @Test
    fun theValveCountsFromTheStartOfTheRecordingNotFromEachConfigChange() {
        // Hệ thống báo lại danh sách mỗi khi cấu hình thu đổi: không được kéo dài thời gian IM mãi.
        val gate = DictationGate(tailMs = 100, maxMuteMs = 1_000)
        gate.record(0, typing = true, plainMic)
        gate.record(900, typing = true, plainMic)
        assertFalse(gate.isMuted(1_000))
    }

    @Test
    fun noTailAfterTheValveHasOpened() {
        val gate = DictationGate(tailMs = 100, maxMuteMs = 1_000)
        gate.record(0, typing = true, plainMic)
        gate.record(5_000, typing = true)
        assertFalse(gate.isMuted(5_000))
    }

    @Test
    fun aNewRecordingAfterTheValveSilencesTheReaderAgain() {
        val gate = DictationGate(tailMs = 100, maxMuteMs = 1_000)
        gate.record(0, typing = true, plainMic)
        gate.record(5_000, typing = true)
        gate.record(6_000, typing = true, voiceRecognition)
        assertTrue(gate.isMuted(6_000))
        assertFalse(gate.isMuted(7_000))
    }

    @Test
    fun aRecordingThatStartsDuringTheTailCancelsTheTail() {
        val gate = gate()
        gate.record(1_000, typing = true, voiceRecognition)
        gate.record(5_000, typing = true)
        gate.record(5_100, typing = true, voiceRecognition)
        assertTrue(gate.isMuted(5_100))
        assertTrue(gate.isMuted(100_000)) // đuôi cũ không còn liên quan; vẫn trong van an toàn của lần thu mới
    }

    @Test
    fun theKeyboardClosingWhileRecordingContinuesReleasesTheReaderOnTheNextUpdate() {
        val gate = gate()
        gate.record(1_000, typing = true, voiceRecognition)
        gate.record(2_000, typing = false, voiceRecognition) // cấu hình đổi, bàn phím đã đóng
        assertFalse(gate.isMuted(2_000))
    }

    @Test
    fun typingContextIsOnlyAskedWhenARecordingCounts() {
        val gate = gate()
        var asked = 0
        val ask: () -> Boolean = { asked++; true }
        gate.update(emptyList(), 0, ask)
        gate.update(listOf(MicRecording(6, silenced = true)), 10, ask)
        gate.update(listOf(MicRecording(1999, silenced = false)), 20, ask)
        assertEquals(0, asked)
        gate.update(listOf(voiceRecognition), 30, ask)
        assertEquals(1, asked)
    }

    @Test
    fun nextChangeAtNamesTheValveWhileRecordingAndTheTailAfterwards() {
        val gate = DictationGate(tailMs = 700, maxMuteMs = 120_000)
        gate.record(1_000, typing = true, voiceRecognition)
        assertEquals(121_000L, gate.nextChangeAt(2_000))
        gate.record(5_000, typing = true)
        assertEquals(5_700L, gate.nextChangeAt(5_100))
        assertNull(gate.nextChangeAt(5_700))
    }

    @Test
    fun noTimerIsNeededWhileNotSilenced() {
        val gate = gate()
        gate.record(1_000, typing = false, plainMic)
        assertNull(gate.nextChangeAt(2_000))
    }

    @Test
    fun defaultsAreTheDocumentedConstants() {
        val gate = DictationGate()
        gate.record(0, typing = true, voiceRecognition)
        assertTrue(gate.isMuted(DictationGate.MAX_MUTE_MS - 1))
        assertFalse(gate.isMuted(DictationGate.MAX_MUTE_MS))
        val other = DictationGate()
        other.record(0, typing = true, voiceRecognition)
        other.record(10, typing = true)
        assertTrue(other.isMuted(10 + DictationGate.TAIL_MS - 1))
        assertFalse(other.isMuted(10 + DictationGate.TAIL_MS))
    }
}

class DictationReadbackTest {

    @Test
    fun nothingTypedMeansNothingToRead() {
        assertNull(DictationReadback().take())
    }

    @Test
    fun readsFromTheEarliestChangeToTheEndOfTheField() {
        // Ô đã có "Xin chào " (9 ký tự); bộ nhận dạng thay đi thay lại đoạn đang soạn bắt đầu từ ký tự 9.
        val readback = DictationReadback()
        readback.onTextChanged("Xin chào hôm", fromIndex = 9)
        readback.onTextChanged("Xin chào hôm nay", fromIndex = 9)
        readback.onTextChanged("Xin chào hôm nay trời đẹp", fromIndex = 9)
        assertEquals("hôm nay trời đẹp", readback.take())
    }

    @Test
    fun theEarliestChangePointWins() {
        val readback = DictationReadback()
        readback.onTextChanged("Xin chào bạn", fromIndex = 9)
        readback.onTextChanged("Xin chào bạn ơi", fromIndex = 3)
        assertEquals("chào bạn ơi", readback.take())
        // Thứ tự ngược lại (chỗ đổi sớm tới trước, chỗ đổi muộn tới sau): vẫn lấy chỗ đổi SỚM NHẤT, không phải chỗ đổi gần nhất.
        readback.onTextChanged("Xin chào bạn ơi", fromIndex = 3)
        readback.onTextChanged("Xin chào bạn ơi nhé", fromIndex = 16)
        assertEquals("chào bạn ơi nhé", readback.take())
    }

    @Test
    fun anUnknownChangePointReadsTheWholeField() {
        val readback = DictationReadback()
        readback.onTextChanged("Xin chào", fromIndex = -1)
        assertEquals("Xin chào", readback.take())
    }

    @Test
    fun aLongFieldIsCutToTheLastCharacters() {
        val readback = DictationReadback(maxChars = 10)
        readback.onTextChanged("0123456789 abcdefghij", fromIndex = 0)
        assertEquals("abcdefghij", readback.take())
    }

    @Test
    fun takingForgetsEverything() {
        val readback = DictationReadback()
        readback.onTextChanged("xin chào", fromIndex = 0)
        assertEquals("xin chào", readback.take())
        assertNull(readback.take())
    }

    @Test
    fun clearingForgetsEverything() {
        val readback = DictationReadback()
        readback.onTextChanged("xin chào", fromIndex = 0)
        readback.clear()
        assertNull(readback.take())
    }

    @Test
    fun aBlankTailReadsNothing() {
        val readback = DictationReadback()
        readback.onTextChanged("xin chào   ", fromIndex = 8)
        assertNull(readback.take())
    }

    @Test
    fun aChangePointPastTheEndOfAShorterFieldIsSafe() {
        // Người dùng xóa bớt chữ trong lúc IM: chỗ đổi nằm sau cuối chữ mới.
        val readback = DictationReadback()
        readback.onTextChanged("ab", fromIndex = 50)
        assertNull(readback.take())
    }

    @Test
    fun defaultLimitIsTheDocumentedConstant() {
        val readback = DictationReadback()
        readback.onTextChanged("x".repeat(DictationReadback.MAX_CHARS + 50), fromIndex = 0)
        assertEquals(DictationReadback.MAX_CHARS, readback.take()!!.length)
    }
}
