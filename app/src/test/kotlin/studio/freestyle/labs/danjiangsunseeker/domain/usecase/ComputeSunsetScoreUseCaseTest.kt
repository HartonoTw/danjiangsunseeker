package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ComputeSunsetScoreUseCaseTest {

    private lateinit var useCase: ComputeSunsetScoreUseCase

    @Before
    fun setUp() {
        useCase = ComputeSunsetScoreUseCase()
    }

    @Test
    fun `perfect alignment gives score 100`() {
        val score = useCase(0.0)
        assertEquals(100.0, score.overall, 0.1)
        assertEquals(VerdictLevel.TOP, score.verdict)
    }

    @Test
    fun `null alignment gives score 0`() {
        val score = useCase(null)
        assertEquals(0.0, score.overall, 0.01)
    }

    @Test
    fun `small offset gives high score`() {
        val score = useCase(1.0)
        assertTrue("Score for 1° offset should be >= 90", score.overall >= 90.0)
    }

    @Test
    fun `2 degree offset gives score around 88`() {
        val score = useCase(2.0)
        assertEquals(88.0, score.overall, 2.0)
    }

    @Test
    fun `10 degree offset gives score around 70`() {
        val score = useCase(10.0)
        assertEquals(70.0, score.overall, 2.0)
    }

    @Test
    fun `20 degree offset gives score around 60`() {
        val score = useCase(20.0)
        assertEquals(60.0, score.overall, 2.0)
    }

    @Test
    fun `90 degree offset gives score near 0`() {
        val score = useCase(90.0)
        assertTrue("Score for 90° should be <= 2", score.overall <= 2.0)
    }

    @Test
    fun `negative offset same as positive offset`() {
        val positive = useCase(15.0)
        val negative = useCase(-15.0)
        assertEquals(positive.overall, negative.overall, 0.001)
    }

    @Test
    fun `score monotonically decreases as offset increases`() {
        val s0 = useCase(0.0).overall
        val s5 = useCase(5.0).overall
        val s20 = useCase(20.0).overall
        val s50 = useCase(50.0).overall
        assertTrue(s0 > s5)
        assertTrue(s5 > s20)
        assertTrue(s20 > s50)
    }

    @Test
    fun `verdict for high score is top rating`() {
        val score = useCase(0.0)
        assertEquals(VerdictLevel.TOP, score.verdict)
    }

    @Test
    fun `verdict for medium score is medium`() {
        val score = useCase(25.0)
        assertEquals(VerdictLevel.MEDIUM, score.verdict)
    }

    @Test
    fun `overall equals alignment field`() {
        val score = useCase(5.0)
        assertEquals(score.alignment, score.overall, 0.001)
    }
}
